package com.hackathon.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end API behaviour on H2 through the real security chain: authentication, claim
 * capture, the submit pipeline, routing and visibility (backend/SPEC.md §11 AC-01..AC-11,
 * AC-15, AC-18; CLAUDE.md §16.2).
 */
class ClaimFlowApiTest extends ApiTestSupport {

	private static List<String> steps(JsonNode claim) {
		List<String> result = new ArrayList<>();
		int sub = claim.get("submissionNo").asInt();
		claim.get("steps").forEach(s -> {
			if (s.get("submissionNo").asInt() == sub) {
				result.add(s.get("stepType").asString() + ":" + s.get("status").asString());
			}
		});
		return result;
	}

	// ---- authentication ----

	@Test
	void loginRejectsWrongPasswordAndApiNeedsAToken() throws Exception {
		MvcResult bad = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"employee@expenseflow.test\",\"password\":\"wrong\"}")).andReturn();
		assertThat(bad.getResponse().getStatus()).isEqualTo(401);
		assertThat(json(bad).get("code").asString()).isEqualTo("UNAUTHENTICATED");

		MvcResult anonymous = mvc.perform(get("/api/v1/me")).andReturn();
		assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
		assertThat(json(anonymous).get("traceId").asString()).isNotBlank();

		MvcResult forged = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not.a.token")).andReturn();
		assertThat(forged.getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void meReturnsDerivedCapabilities() throws Exception {
		JsonNode manager = json(call("manager", get("/api/v1/me")));
		assertThat(manager.get("isManager").asBoolean()).isTrue();
		assertThat(manager.get("canDelegate").asBoolean()).isTrue();
		assertThat(manager.get("isAdmin").asBoolean()).isFalse();

		JsonNode employee = json(call("employee", get("/api/v1/me")));
		assertThat(employee.get("isApprover").asBoolean()).isFalse();

		JsonNode head = json(call("financehead", get("/api/v1/me")));
		assertThat(head.get("isAdmin").asBoolean()).isTrue();
		assertThat(head.get("isApprover").asBoolean()).isTrue();
	}

	@Test
	void unknownJsonFieldIsRejected() throws Exception {
		String claimId = newClaim("employee");
		MvcResult r = callJson("employee", post("/api/v1/claims/" + claimId + "/line-items"),
				"{\"category\":\"MEALS\",\"amount\":\"10.00\",\"expenseDate\":\"2026-11-24\",\"vendor\":\"V\","
						+ "\"receiptNumber\":\"R\",\"status\":\"APPROVED\"}");
		assertThat(r.getResponse().getStatus()).isEqualTo(400);
		assertThat(json(r).get("code").asString()).isEqualTo("VALIDATION_FAILED");
	}

	// ---- capture and validation ----

	@Test
	void lineValidationCoversAmountsDatesAndHotelFields() throws Exception {
		String claimId = newClaim("employee");
		String base = "/api/v1/claims/" + claimId + "/line-items";
		assertThat(callJson("employee", post(base), "{\"category\":\"MEALS\",\"amount\":\"0\",\"expenseDate\":"
				+ "\"2026-11-24\",\"vendor\":\"V\",\"receiptNumber\":\"R\"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(callJson("employee", post(base), "{\"category\":\"MEALS\",\"amount\":\"-5.00\",\"expenseDate\":"
				+ "\"2026-11-24\",\"vendor\":\"V\",\"receiptNumber\":\"R\"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(callJson("employee", post(base), "{\"category\":\"MEALS\",\"amount\":\"1.234\",\"expenseDate\":"
				+ "\"2026-11-24\",\"vendor\":\"V\",\"receiptNumber\":\"R\"}").getResponse().getStatus()).isEqualTo(400);
		MvcResult future = callJson("employee", post(base), "{\"category\":\"MEALS\",\"amount\":\"5.00\","
				+ "\"expenseDate\":\"2026-11-28\",\"vendor\":\"V\",\"receiptNumber\":\"R\"}");
		assertThat(future.getResponse().getStatus()).isEqualTo(400);
		assertThat(json(future).at("/details/fields/expenseDate").asString()).contains("future");
		MvcResult hotel = callJson("employee", post(base), "{\"category\":\"HOTEL\",\"amount\":\"5.00\","
				+ "\"expenseDate\":\"2026-11-24\",\"vendor\":\"V\",\"receiptNumber\":\"R\"}");
		assertThat(json(hotel).at("/details/fields/city").asString()).isNotBlank();
	}

	@Test
	void receiptMustBePdfJpegOrPngByContent() throws Exception {
		String claimId = newClaim("employee");
		String line = addLine("employee", claimId, "MEALS", "900.00", "2026-11-24", "Cafe", "R-1");
		MvcResult html = upload("employee", claimId, line, "<html>not a receipt</html>".getBytes());
		assertThat(html.getResponse().getStatus()).isEqualTo(415);
		assertThat(json(html).get("code").asString()).isEqualTo("UNSUPPORTED_FILE_TYPE");
		MvcResult ok = upload("employee", claimId, line, pdf("one"));
		assertThat(ok.getResponse().getStatus()).isEqualTo(201);
		assertThat(json(ok).get("contentType").asString()).isEqualTo("application/pdf");
	}

	@Test
	void ac03SubmitNeedsLinesAndReceiptsAboveFiveHundred() throws Exception {
		String claimId = newClaim("employee");
		MvcResult empty = submit("employee", claimId);
		assertThat(empty.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(empty).get("code").asString()).isEqualTo("CLAIM_HAS_NO_LINES");

		addLine("employee", claimId, "MEALS", "500.00", "2026-11-24", "Cafe A", "R-500");
		String line = addLine("employee", claimId, "MEALS", "500.01", "2026-11-25", "Cafe B", "R-501");
		MvcResult missing = submit("employee", claimId);
		assertThat(missing.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(missing).get("code").asString()).isEqualTo("RECEIPT_REQUIRED");
		assertThat(json(missing).at("/details/lines/0/lineItemId").asString()).isEqualTo(line);
		assertThat(claim("employee", claimId).get("status").asString()).isEqualTo("DRAFT");

		upload("employee", claimId, line, pdf("501"));
		assertThat(submit("employee", claimId).getResponse().getStatus()).isEqualTo(200);
	}

	// ---- routing (CLAUDE.md §2.7) ----

	@Test
	void ac01CleanSmallClaimIsAutoApprovedOrSampled() throws Exception {
		String a = newClaim("employee");
		addLine("employee", a, "MEALS", "400.00", "2026-11-24", "Cafe", "R-A");
		NEXT_DRAW.set(0.5);
		JsonNode approved = json(submit("employee", a));
		assertThat(approved.get("status").asString()).isEqualTo("APPROVED");

		String b = newClaim("employee");
		addLine("employee", b, "MEALS", "300.00", "2026-11-25", "Diner", "R-B");
		NEXT_DRAW.set(0.0999);
		JsonNode sampled = json(submit("employee", b));
		assertThat(sampled.get("status").asString()).isEqualTo("SAMPLE_AUDIT_PENDING");

		JsonNode audit = json(call("employee", get("/api/v1/claims/" + b + "/audit-events")));
		List<String> actions = new ArrayList<>();
		audit.get("items").forEach(e -> actions.add(e.get("action").asString()));
		assertThat(actions).containsSubsequence("CLAIM_SUBMITTED", "POLICY_EVALUATED", "CLAIM_AUTO_APPROVED",
				"SAMPLE_AUDIT_SELECTED");
		assertThat(audit.get("items").get(actions.indexOf("SAMPLE_AUDIT_SELECTED")).at("/details/draw").asDouble())
			.isEqualTo(0.0999);

		// CL-14: the claimant cannot review their own sample; a finance auditor can.
		assertThat(json(decide("employee", b, "approve", null)).get("code").asString())
			.isEqualTo("SELF_APPROVAL_FORBIDDEN");
		assertThat(decide("financehead", b, "approve", null).getResponse().getStatus()).isEqualTo(404);
		JsonNode reviewed = json(decide("auditor", b, "approve", null));
		assertThat(reviewed.get("status").asString()).isEqualTo("APPROVED");
	}

	@Test
	void ac02AnyBreachAutoRejectsTheWholeClaimListingEveryLine() throws Exception {
		String c = newClaim("employee");
		String cab = addLine("employee", c, "LOCAL_CAB", "2000.01", "2026-11-24", "Cabs", "R-C1");
		String meal = addLine("employee", c, "MEALS", "1600.00", "2026-11-25", "Feast", "R-C2");
		upload("employee", c, cab, pdf("c1"));
		upload("employee", c, meal, pdf("c2"));
		JsonNode result = json(submit("employee", c));
		assertThat(result.get("status").asString()).isEqualTo("AUTO_REJECTED");
		assertThat(result.get("decisionReason").asString()).contains("POL-004").contains("POL-001")
			.contains("2000.01").contains("1600.00");
	}

	@Test
	void ac04AuditorThresholdIsStrictlyAboveTwentyFiveThousand() throws Exception {
		String at = newClaim("employee");
		String l1 = addHotel("employee", at, "25000.00", "Mumbai", "2026-11-02", "2026-11-07", "H-AT");
		upload("employee", at, l1, pdf("at"));
		assertThat(json(submit("employee", at)).get("status").asString()).isEqualTo("APPROVED");

		String above = newClaim("employee2");
		String l2 = addHotel("employee2", above, "25000.01", "Mumbai", "2026-11-09", "2026-11-14", "H-AB");
		upload("employee2", above, l2, pdf("above"));
		JsonNode routed = json(submit("employee2", above));
		assertThat(routed.get("status").asString()).isEqualTo("PENDING_MANAGER");
		assertThat(steps(routed)).containsExactly("MANAGER:PENDING", "FINANCE_AUDITOR:WAITING");
	}

	@Test
	void ac05FullChainAboveOneLakhApprovesStepByStep() throws Exception {
		String c = newClaim("employee");
		String l = addHotel("employee", c, "100000.01", "Bengaluru", "2026-10-05", "2026-10-25", "H-BIG");
		upload("employee", c, l, pdf("big"));
		assertThat(json(submit("employee", c)).get("status").asString()).isEqualTo("PENDING_MANAGER");

		// Wrong approvers: another employee cannot even see it; an auditor is not yet current.
		assertThat(decide("employee2", c, "approve", null).getResponse().getStatus()).isEqualTo(404);
		assertThat(json(decide("manager", c, "approve", "ok")).get("status").asString()).isEqualTo("PENDING_AUDITOR");
		MvcResult early = decide("financehead", c, "approve", null);
		assertThat(early.getResponse().getStatus()).isEqualTo(404);
		assertThat(json(decide("auditor", c, "approve", null)).get("status").asString())
			.isEqualTo("PENDING_FINANCE_HEAD");
		JsonNode done = json(decide("financehead", c, "approve", null));
		assertThat(done.get("status").asString()).isEqualTo("APPROVED");
		assertThat(steps(done)).containsExactly("MANAGER:APPROVED", "FINANCE_AUDITOR:APPROVED",
				"FINANCE_HEAD:APPROVED");

		// AC-15 contrast: the manager acted, so can still open it; a repeat approve is a 409.
		MvcResult again = decide("manager", c, "approve", null);
		assertThat(again.getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void ac06FinanceHeadsClaimGoesOnlyToAnAuditor() throws Exception {
		String c = newClaim("financehead");
		addLine("financehead", c, "MEALS", "200.00", "2026-11-24", "Tea", "R-FH");
		JsonNode routed = json(submit("financehead", c));
		assertThat(routed.get("status").asString()).isEqualTo("PENDING_AUDITOR");
		assertThat(steps(routed)).containsExactly("FINANCE_AUDITOR:PENDING");
	}

	@Test
	void ac07NoManagerWeekendClaimSkipsToAnAddedAuditorStep() throws Exception {
		String c = newClaim("nomanager");
		addLine("nomanager", c, "MEALS", "400.00", "2026-11-21", "Weekend Cafe", "R-SAT");
		JsonNode routed = json(submit("nomanager", c));
		assertThat(routed.get("status").asString()).isEqualTo("PENDING_AUDITOR");
		assertThat(steps(routed)).containsExactly("MANAGER:SKIPPED", "FINANCE_AUDITOR:PENDING");
		assertThat(routed.at("/lineItems/0/policyResults/0/ruleId").asString()).isEqualTo("POL-008");
	}

	@Test
	void ac08FinanceAuditorsOwnBigClaimSkipsTheAuditorStep() throws Exception {
		String c = newClaim("auditor");
		String l = addHotel("auditor", c, "30000.00", "Pune", "2026-11-02", "2026-11-10", "H-FA");
		upload("auditor", c, l, pdf("fa"));
		JsonNode routed = json(submit("auditor", c));
		assertThat(steps(routed)).containsExactly("MANAGER:PENDING", "FINANCE_AUDITOR:SKIPPED", "FINANCE_HEAD:WAITING");
		// SPEC-Q-16: the finance head approves both the manager and the finance-head step.
		assertThat(json(decide("financehead", c, "approve", null)).get("status").asString())
			.isEqualTo("PENDING_FINANCE_HEAD");
		assertThat(json(decide("financehead", c, "approve", null)).get("status").asString()).isEqualTo("APPROVED");
	}

	@Test
	void ac09RenamedReceiptReusedByAnotherEmployeeIsFlaggedAndRouted() throws Exception {
		String first = newClaim("employee");
		String l1 = addLine("employee", first, "MEALS", "800.00", "2026-11-24", "Cafe One", "R-ONE");
		upload("employee", first, l1, pdf("same-bytes"));
		submit("employee", first);
		String firstNumber = claim("employee", first).get("claimNumber").asString();

		String second = newClaim("employee2");
		String l2 = addLine("employee2", second, "MEALS", "750.00", "2026-11-25", "Cafe Two", "R-TWO");
		upload("employee2", second, l2, pdf("same-bytes"));
		JsonNode routed = json(submit("employee2", second));
		assertThat(routed.get("status").asString()).isEqualTo("PENDING_MANAGER");
		JsonNode result = routed.at("/lineItems/0/policyResults/0");
		assertThat(result.get("ruleId").asString()).isEqualTo("POL-007");
		assertThat(result.get("actualValue").asString()).contains(firstNumber);
	}

	@Test
	void ac10RejectNeedsACommentAndEndsTheClaim() throws Exception {
		String c = newClaim("employee");
		addLine("employee", c, "MEALS", "100.00", "2026-11-21", "Sat Lunch", "R-REJ");
		submit("employee", c);
		MvcResult noComment = decide("manager", c, "reject", "  ");
		assertThat(noComment.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(noComment).get("code").asString()).isEqualTo("COMMENT_REQUIRED");
		assertThat(claim("employee", c).get("status").asString()).isEqualTo("PENDING_MANAGER");
		JsonNode rejected = json(decide("manager", c, "reject", "Personal expense"));
		assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
		assertThat(claim("employee", c).get("decisionReason").asString()).isEqualTo("Personal expense");
	}

	@Test
	void ac11SendBackAndResubmitRestartsAtTheManager() throws Exception {
		String c = newClaim("employee");
		String l = addHotel("employee", c, "30000.00", "Chennai", "2026-11-02", "2026-11-08", "H-SB");
		upload("employee", c, l, pdf("sb"));
		submit("employee", c);
		decide("manager", c, "approve", null);
		assertThat(json(decide("auditor", c, "send-back", null)).get("code").asString()).isEqualTo("COMMENT_REQUIRED");
		JsonNode sentBack = json(decide("auditor", c, "send-back", "Split the stay by city"));
		assertThat(sentBack.get("status").asString()).isEqualTo("SENT_BACK");
		assertThat(claim("employee", c).get("latestSendBackComment").asString()).isEqualTo("Split the stay by city");

		// B-04: lines are editable again only now; lower the amount below the threshold (B-05).
		String body = "{\"category\":\"HOTEL\",\"amount\":\"20000.00\",\"expenseDate\":\"2026-11-02\",\"vendor\":"
				+ "\"Hotel H-SB\",\"receiptNumber\":\"H-SB\",\"city\":\"Chennai\",\"checkInDate\":\"2026-11-02\","
				+ "\"checkOutDate\":\"2026-11-08\"}";
		assertThat(callJson("employee", put("/api/v1/claims/" + c + "/line-items/" + l), body).getResponse()
			.getStatus()).isEqualTo(200);
		JsonNode resubmitted = json(submit("employee", c));
		assertThat(resubmitted.get("status").asString()).isEqualTo("PENDING_MANAGER");
		assertThat(resubmitted.get("submissionNo").asInt()).isEqualTo(2);
		assertThat(steps(resubmitted)).containsExactly("MANAGER:PENDING");
		List<String> old = new ArrayList<>();
		resubmitted.get("steps").forEach(s -> {
			if (s.get("submissionNo").asInt() == 1) {
				old.add(s.get("status").asString());
			}
		});
		assertThat(old).containsExactly("SUPERSEDED", "SENT_BACK");
	}

	@Test
	void linesAreLockedAfterSubmit() throws Exception {
		String c = newClaim("employee");
		String l = addLine("employee", c, "MEALS", "100.00", "2026-11-21", "Sat", "R-LOCK");
		submit("employee", c);
		MvcResult r = call("employee", delete("/api/v1/claims/" + c + "/line-items/" + l));
		assertThat(r.getResponse().getStatus()).isEqualTo(409);
		assertThat(submit("employee", c).getResponse().getStatus()).isEqualTo(409);
	}

	// ---- visibility (FR-019, CL-15, B-10) ----

	@Test
	void ac15OtherPeoplesClaimsAreNotFound() throws Exception {
		String c = newClaim("employee");
		addLine("employee", c, "MEALS", "100.00", "2026-11-24", "Cafe", "R-VIS");
		for (String who : List.of("employee2", "financehead", "auditor", "manager")) {
			MvcResult r = call(who, get("/api/v1/claims/" + c));
			assertThat(r.getResponse().getStatus()).as(who).isEqualTo(404);
			assertThat(json(r).get("code").asString()).isEqualTo("NOT_FOUND");
		}
		JsonNode list = json(call("employee2", get("/api/v1/claims")));
		assertThat(list.get("total").asInt()).isZero();
		// Clean and small, so it is auto-approved: the manager never gets a step and still cannot open it.
		submit("employee", c);
		assertThat(call("manager", get("/api/v1/claims/" + c)).getResponse().getStatus()).isEqualTo(404);
	}

	@Test
	void ac18IdempotencyKeyReplaysAndRejectsReuse() throws Exception {
		String c = newClaim("employee");
		addLine("employee", c, "MEALS", "100.00", "2026-11-24", "Cafe", "R-IDEM");
		MvcResult first = call("employee", post("/api/v1/claims/" + c + "/submit").header("Idempotency-Key", "k-1"));
		MvcResult replay = call("employee", post("/api/v1/claims/" + c + "/submit").header("Idempotency-Key", "k-1"));
		assertThat(replay.getResponse().getStatus()).isEqualTo(200);
		assertThat(json(replay).get("status").asString()).isEqualTo(json(first).get("status").asString());

		String other = newClaim("employee");
		addLine("employee", other, "MEALS", "90.00", "2026-11-23", "Cafe 2", "R-IDEM-2");
		MvcResult reused = call("employee",
				post("/api/v1/claims/" + other + "/submit").header("Idempotency-Key", "k-1"));
		assertThat(reused.getResponse().getStatus()).isEqualTo(422);
		assertThat(json(reused).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
	}

}
