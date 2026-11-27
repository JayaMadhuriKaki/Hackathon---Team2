package com.hackathon.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;

/**
 * Delegation, approver queue, review queue and bulk approve, payout export, policy config,
 * dashboard and the audit trail (backend/SPEC.md §11 AC-12..AC-14, AC-16, AC-17, AC-20).
 */
class WorkflowApiTest extends ApiTestSupport {

	/** A weekend meal claim: always routed to the manager (CL-02). */
	private String routedClaim(String who, String receiptNo) throws Exception {
		String c = newClaim(who);
		addLine(who, c, "MEALS", "200.00", "2026-11-21", "Weekend " + receiptNo, receiptNo);
		submit(who, c);
		return c;
	}

	/** A claim that ends in the auditor queue: no-manager claimant, weekend line. */
	private String auditorQueueClaim(String receiptNo) throws Exception {
		String c = newClaim("nomanager");
		addLine("nomanager", c, "MEALS", "300.00", "2026-11-21", "Sat " + receiptNo, receiptNo);
		submit("nomanager", c);
		return c;
	}

	private void pinToday(int day) {
		clock.setClock(Clock.fixed(LocalDateTime.of(2026, 11, day, 6, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
	}

	// ---- delegation (FR-008, CL-13) ----

	@Test
	void ac12AndAc13DelegateActsInsideTheWindowOnBehalfOfTheManager() throws Exception {
		String auditorId = id(people.get("auditor"));
		MvcResult created = callJson("manager", post("/api/v1/delegations"),
				"{\"delegateId\":\"" + auditorId + "\",\"startDate\":\"2026-11-25\",\"endDate\":\"2026-11-27\"}");
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		String c1 = routedClaim("employee", "R-D1");
		String c2 = routedClaim("employee2", "R-D2");

		JsonNode queue = json(call("auditor", get("/api/v1/approvals")));
		assertThat(queue.get("total").asInt()).isEqualTo(2);
		assertThat(queue.at("/items/0/onBehalfOf/name").asString()).isEqualTo("Manoj Manager");

		JsonNode approved = json(decide("auditor", c1, "approve", null));
		assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
		assertThat(approved.at("/steps/0/actedBy/name").asString()).isEqualTo("Arjun Auditor");
		assertThat(approved.at("/steps/0/onBehalfOf/name").asString()).isEqualTo("Manoj Manager");

		pinToday(28);
		assertThat(decide("auditor", c2, "approve", null).getResponse().getStatus()).isEqualTo(404);
		assertThat(json(call("auditor", get("/api/v1/approvals"))).get("total").asInt()).isZero();
		// The manager keeps the right throughout (ASM-S-04).
		assertThat(json(decide("manager", c2, "approve", null)).get("status").asString()).isEqualTo("APPROVED");
	}

	@Test
	void ac14DelegateWhoIsTheClaimantCannotApprove() throws Exception {
		callJson("financehead", post("/api/v1/delegations"), "{\"delegateId\":\"" + id(people.get("auditor"))
				+ "\",\"startDate\":\"2026-11-20\",\"endDate\":\"2026-11-30\"}");
		String c = routedClaim("auditor", "R-SELF");
		MvcResult r = decide("auditor", c, "approve", null);
		assertThat(r.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(r).get("code").asString()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
	}

	@Test
	void delegationRules() throws Exception {
		String body = "{\"delegateId\":\"%s\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-05\"}";
		MvcResult employeeDelegate = callJson("manager", post("/api/v1/delegations"),
				body.formatted(id(people.get("employee2"))));
		assertThat(json(employeeDelegate).get("code").asString()).isEqualTo("INVALID_DELEGATE");
		MvcResult notManager = callJson("employee", post("/api/v1/delegations"),
				body.formatted(id(people.get("auditor"))));
		assertThat(notManager.getResponse().getStatus()).isEqualTo(403);
		MvcResult ok = callJson("manager", post("/api/v1/delegations"), body.formatted(id(people.get("auditor"))));
		assertThat(ok.getResponse().getStatus()).isEqualTo(201);
		MvcResult overlap = callJson("manager", post("/api/v1/delegations"),
				body.formatted(id(people.get("auditor2"))));
		assertThat(json(overlap).get("code").asString()).isEqualTo("DELEGATION_OVERLAP");
		String delegationId = json(ok).get("delegationId").asString();
		// Only the delegator may cancel; anyone else gets 404 (backend/SPEC.md §6.4 API-021).
		assertThat(call("auditor", delete("/api/v1/delegations/" + delegationId)).getResponse().getStatus())
			.isEqualTo(404);
		assertThat(call("manager", delete("/api/v1/delegations/" + delegationId)).getResponse().getStatus())
			.isEqualTo(204);
		JsonNode eligible = json(call("manager", get("/api/v1/delegations/eligible-delegates")));
		assertThat(eligible.size()).isEqualTo(3);
	}

	// ---- comments and wrong approvers ----

	@Test
	void wrongApproverOnAVisibleClaimGetsNotCurrentApprover() throws Exception {
		String c = newClaim("employee");
		String l = addHotel("employee", c, "26000.00", "Kolkata", "2026-11-02", "2026-11-07", "H-NCA");
		upload("employee", c, l, pdf("nca"));
		submit("employee", c);
		decide("manager", c, "approve", null);
		// The manager acted, so can still see the claim, but is not the current (auditor) approver.
		MvcResult r = decide("manager", c, "approve", null);
		assertThat(r.getResponse().getStatus()).isEqualTo(403);
		assertThat(json(r).get("code").asString()).isEqualTo("NOT_CURRENT_APPROVER");
		MvcResult comment = callJson("manager", post("/api/v1/claims/" + c + "/comments"), "{\"body\":\"FYI\"}");
		assertThat(comment.getResponse().getStatus()).isEqualTo(201);
		MvcResult claimantComment = callJson("employee", post("/api/v1/claims/" + c + "/comments"),
				"{\"body\":\"hi\"}");
		assertThat(claimantComment.getResponse().getStatus()).isEqualTo(403);
	}

	// ---- review queue (FR-009, FR-010) ----

	@Test
	void ac20BulkApproveReportsPerClaimResults() throws Exception {
		String a = auditorQueueClaim("R-Q1");
		String b = auditorQueueClaim("R-Q2");
		String own = newClaim("auditor");
		addLine("auditor", own, "MEALS", "100.00", "2026-11-21", "Own", "R-OWN");
		submit("auditor", own);
		decide("financehead", own, "approve", null);

		JsonNode queue = json(call("auditor", get("/api/v1/review-queue")));
		assertThat(queue.get("total").asInt()).isEqualTo(2);
		assertThat(json(call("auditor", get("/api/v1/review-queue?flag=WEEKEND"))).get("total").asInt()).isEqualTo(2);
		assertThat(json(call("auditor", get("/api/v1/review-queue?queue=SAMPLE_AUDIT"))).get("total").asInt()).isZero();
		assertThat(call("employee", get("/api/v1/review-queue")).getResponse().getStatus()).isEqualTo(403);

		MvcResult bulk = callJson("auditor", post("/api/v1/review-queue/bulk-approve"),
				"{\"claimIds\":[\"" + a + "\",\"" + b + "\",\"" + own + "\"]}");
		assertThat(bulk.getResponse().getStatus()).isEqualTo(200);
		JsonNode results = json(bulk);
		assertThat(results.get("approved").asInt()).isEqualTo(2);
		assertThat(results.at("/results/2/outcome").asString()).isEqualTo("FAILED");
		assertThat(results.at("/results/2/code").asString()).isIn("SELF_APPROVAL_FORBIDDEN", "STATE_CONFLICT");
	}

	// ---- payout (FR-012..014, CL-14, §2.8) ----

	@Test
	void ac16And17PayoutBatchExportsTheMockBankFile() throws Exception {
		String a = newClaim("employee");
		addLine("employee", a, "MEALS", "450.00", "2026-11-24", "Cafe P1", "R-P1");
		submit("employee", a);
		String b = newClaim("employee2");
		addLine("employee2", b, "LOCAL_CAB", "350.50", "2026-11-25", "Cab P2", "R-P2");
		submit("employee2", b);
		NEXT_DRAW.set(0.01);
		String sampled = newClaim("employee");
		addLine("employee", sampled, "MEALS", "120.00", "2026-11-23", "Cafe P3", "R-P3");
		submit("employee", sampled);

		assertThat(call("manager", get("/api/v1/payout-batches/eligible-claims")).getResponse().getStatus())
			.isEqualTo(403);
		JsonNode eligible = json(call("financehead", get("/api/v1/payout-batches/eligible-claims")));
		assertThat(eligible.get("total").asInt()).isEqualTo(2);

		MvcResult notEligible = callJson("financehead", post("/api/v1/payout-batches"),
				"{\"claimIds\":[\"" + a + "\",\"" + sampled + "\"]}");
		assertThat(notEligible.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(notEligible).get("code").asString()).isEqualTo("CLAIM_NOT_ELIGIBLE_FOR_PAYOUT");
		assertThat(json(notEligible).at("/details/claimIds/0").asString()).isEqualTo(sampled);

		MvcResult created = callJson("financehead", post("/api/v1/payout-batches"),
				"{\"claimIds\":[\"" + a + "\",\"" + b + "\"]}");
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		String batchId = json(created).at("/batch/batchId").asString();
		assertThat(json(created).at("/batch/batchNumber").asString()).isEqualTo("PB-2026-0001");
		assertThat(call("financehead", get("/api/v1/payout-batches/" + batchId + "/file")).getResponse().getStatus())
			.isEqualTo(409);

		JsonNode summary = json(call("financehead", post("/api/v1/payout-batches/" + batchId + "/export")));
		assertThat(summary.get("rowCount").asInt()).isEqualTo(2);
		assertThat(summary.get("totalAmount").asString()).isEqualTo("800.50");
		assertThat(summary.get("fileName").asString()).isEqualTo("EXPENSEFLOW_PAYOUT_PB-2026-0001_20261127.csv");
		assertThat(call("financehead", post("/api/v1/payout-batches/" + batchId + "/export")).getResponse()
			.getStatus()).isEqualTo(409);
		assertThat(claim("employee", a).get("status").asString()).isEqualTo("PAID");

		byte[] first = call("financehead", get("/api/v1/payout-batches/" + batchId + "/file")).getResponse()
			.getContentAsByteArray();
		byte[] second = call("financehead", get("/api/v1/payout-batches/" + batchId + "/file")).getResponse()
			.getContentAsByteArray();
		assertThat(second).isEqualTo(first);
		String csv = new String(first, StandardCharsets.UTF_8);
		assertThat(csv).startsWith("\"batch_number\",\"payment_reference\",\"value_date\"");
		assertThat(csv).contains("\"'=Eve Employee\"").contains("450.00").contains("\"PB-2026-0001-0001\"");
		assertThat(csv).contains("\"Expense reimbursement CLM-2026\"");
	}

	// ---- policy configuration (CL-10, B-08) ----

	@Test
	void policyRuleChangeIsAdminOnlyValidatedAuditedAndVersioned() throws Exception {
		assertThat(callJson("auditor", put("/api/v1/policy-rules/meals.max_per_day"), "{\"value\":\"1800.00\"}")
			.getResponse()
			.getStatus()).isEqualTo(403);
		assertThat(callJson("financehead", put("/api/v1/policy-rules/meals.max_per_day"), "{\"value\":\"-1\"}")
			.getResponse()
			.getStatus()).isEqualTo(400);
		assertThat(callJson("financehead", put("/api/v1/policy-rules/approval.finance_head_threshold"),
				"{\"value\":\"20000.00\"}")
			.getResponse()
			.getStatus()).isEqualTo(400);

		JsonNode changed = json(callJson("financehead", put("/api/v1/policy-rules/meals.max_per_day"),
				"{\"value\":\"1800.00\"}"));
		assertThat(changed.get("configVersion").asInt()).isEqualTo(2);

		String c = newClaim("employee");
		addLine("employee", c, "MEALS", "1700.00", "2026-11-24", "Big Lunch", "R-CFG");
		String l = json(call("employee", get("/api/v1/claims/" + c))).at("/lineItems/0/lineItemId").asString();
		upload("employee", c, l, pdf("cfg"));
		JsonNode result = json(submit("employee", c));
		assertThat(result.get("status").asString()).isEqualTo("APPROVED");
		assertThat(result.get("configVersion").asInt()).isEqualTo(2);

		JsonNode trail = json(call("financehead", get("/api/v1/audit-events?action=POLICY_RULE_CHANGED")));
		assertThat(trail.get("total").asInt()).isEqualTo(1);
		assertThat(trail.at("/items/0/details/oldValue").asString()).isEqualTo("1500.00");
		assertThat(call("auditor", get("/api/v1/audit-events")).getResponse().getStatus()).isEqualTo(403);
		JsonNode client = json(call("employee", get("/api/v1/policy-rules/client-config")));
		assertThat(client.get("receiptMandatoryAbove").asString()).isEqualTo("500.00");
	}

	// ---- dashboard (FR-015..017) ----

	@Test
	void dashboardAggregatesForFinanceOnly() throws Exception {
		String a = newClaim("employee");
		addLine("employee", a, "MEALS", "450.00", "2026-11-24", "Cafe D1", "R-D1");
		addLine("employee", a, "LOCAL_CAB", "150.00", "2026-11-24", "Cab D1", "R-D2");
		submit("employee", a);
		routedClaim("employee2", "R-D3");

		assertThat(call("employee", get("/api/v1/dashboard/spend")).getResponse().getStatus()).isEqualTo(403);
		JsonNode spend = json(call("auditor", get("/api/v1/dashboard/spend?groupBy=category")));
		assertThat(spend.get("grandTotal").asString()).isEqualTo("600.00");
		JsonNode byDept = json(call("financehead", get("/api/v1/dashboard/spend?groupBy=department")));
		assertThat(byDept.at("/items/0/key").asString()).isEqualTo("Consulting");
		JsonNode violations = json(call("auditor", get("/api/v1/dashboard/policy-violations")));
		assertThat(violations.at("/items/0/ruleId").asString()).isEqualTo("POL-008");
		JsonNode time = json(call("auditor", get("/api/v1/dashboard/approval-time")));
		assertThat(time.get("approvedClaims").asInt()).isEqualTo(1);
		assertThat(call("auditor", get("/api/v1/dashboard/spend?groupBy=weekday")).getResponse().getStatus())
			.isEqualTo(400);
	}

}
