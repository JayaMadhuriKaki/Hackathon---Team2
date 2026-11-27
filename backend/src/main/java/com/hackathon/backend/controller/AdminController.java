package com.hackathon.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.AuditEventView;
import com.hackathon.backend.security.CurrentUserService;
import com.hackathon.backend.service.AuditQueryService;
import com.hackathon.backend.service.ClaimService;
import com.hackathon.backend.service.DashboardService;
import com.hackathon.backend.service.DashboardService.ApprovalTimeReport;
import com.hackathon.backend.service.DashboardService.SpendReport;
import com.hackathon.backend.service.DashboardService.ViolationReport;
import com.hackathon.backend.service.IdempotencyService;
import com.hackathon.backend.service.PayoutService;
import com.hackathon.backend.service.PayoutService.BatchDetail;
import com.hackathon.backend.service.PayoutService.BatchSummary;
import com.hackathon.backend.service.PayoutService.CreateBatchRequest;
import com.hackathon.backend.service.PayoutService.PayoutClaim;
import com.hackathon.backend.service.PolicyConfigService;
import com.hackathon.backend.service.PolicyConfigService.RuleView;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.Valid;
import tools.jackson.databind.JsonNode;

/** Payout, dashboard, full audit trail and policy configuration (API-024..035, API-037). */
@RestController
@RequestMapping("/api/v1")
public class AdminController {

	private final PayoutService payouts;

	private final DashboardService dashboard;

	private final AuditQueryService auditQuery;

	private final PolicyConfigService policy;

	private final IdempotencyService idempotency;

	private final CurrentUserService currentUser;

	public AdminController(PayoutService payouts, DashboardService dashboard, AuditQueryService auditQuery,
			PolicyConfigService policy, IdempotencyService idempotency, CurrentUserService currentUser) {
		this.payouts = payouts;
		this.dashboard = dashboard;
		this.auditQuery = auditQuery;
		this.policy = policy;
		this.idempotency = idempotency;
		this.currentUser = currentUser;
	}

	@GetMapping("/payout-batches")
	public PageResponse<BatchSummary> batches(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		return payouts.list(currentUser.require(), page, pageSize);
	}

	@GetMapping("/payout-batches/eligible-claims")
	public PageResponse<PayoutClaim> eligibleClaims(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		return payouts.eligible(currentUser.require(), page, pageSize);
	}

	@PostMapping("/payout-batches")
	public ResponseEntity<Object> createBatch(@Valid @RequestBody CreateBatchRequest request,
			@RequestHeader(name = ClaimController.IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		return idempotency.run(user, "API-026", key, "/payout-batches", request, HttpStatus.CREATED,
				() -> payouts.create(user, request));
	}

	@GetMapping("/payout-batches/{batchId}")
	public BatchDetail batch(@PathVariable UUID batchId) {
		return payouts.get(currentUser.require(), batchId);
	}

	@PostMapping("/payout-batches/{batchId}/export")
	public ResponseEntity<Object> export(@PathVariable UUID batchId,
			@RequestHeader(name = ClaimController.IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		return idempotency.run(user, "API-028", key, "/payout-batches/" + batchId + "/export", null, HttpStatus.OK,
				() -> payouts.export(user, batchId));
	}

	@GetMapping("/payout-batches/{batchId}/file")
	public ResponseEntity<byte[]> file(@PathVariable UUID batchId) {
		ClaimService.StoredFile file = payouts.file(currentUser.require(), batchId);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.fileName()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(file.bytes());
	}

	@GetMapping("/dashboard/spend")
	public SpendReport spend(@RequestParam(required = false) String groupBy,
			@RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate,
			@RequestParam(required = false) UUID departmentId) {
		return dashboard.spend(currentUser.require(), groupBy, fromDate, toDate, departmentId);
	}

	@GetMapping("/dashboard/policy-violations")
	public ViolationReport violations(@RequestParam(required = false) LocalDate fromDate,
			@RequestParam(required = false) LocalDate toDate, @RequestParam(required = false) UUID departmentId) {
		return dashboard.violations(currentUser.require(), fromDate, toDate, departmentId);
	}

	@GetMapping("/dashboard/approval-time")
	public ApprovalTimeReport approvalTime(@RequestParam(required = false) LocalDate fromDate,
			@RequestParam(required = false) LocalDate toDate, @RequestParam(required = false) UUID departmentId) {
		return dashboard.approvalTime(currentUser.require(), fromDate, toDate, departmentId);
	}

	@GetMapping("/audit-events")
	public PageResponse<AuditEventView> auditEvents(@RequestParam(required = false) String claimNumber,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) EntityType entityType, @RequestParam(required = false) LocalDate fromDate,
			@RequestParam(required = false) LocalDate toDate, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		return auditQuery.search(currentUser.require(), claimNumber, actorId, action, entityType, fromDate, toDate,
				page, pageSize);
	}

	@GetMapping("/policy-rules")
	public List<RuleView> policyRules() {
		return policy.list(currentUser.require());
	}

	@PutMapping("/policy-rules/{ruleKey}")
	public RuleView updatePolicyRule(@PathVariable String ruleKey, @RequestBody UpdateRuleRequest request) {
		return policy.update(currentUser.require(), ruleKey, request.value());
	}

	@GetMapping("/policy-rules/client-config")
	public Map<String, Object> clientConfig() {
		currentUser.require();
		return policy.clientConfig();
	}

	public record UpdateRuleRequest(JsonNode value) {
	}

}
