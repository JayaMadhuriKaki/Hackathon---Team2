package com.hackathon.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.security.CurrentUserService;
import com.hackathon.backend.service.ApprovalService;
import com.hackathon.backend.service.ApprovalService.ApprovalItem;
import com.hackathon.backend.service.DelegationService;
import com.hackathon.backend.service.DelegationService.CreateDelegationRequest;
import com.hackathon.backend.service.DelegationService.DelegationView;
import com.hackathon.backend.service.IdempotencyService;
import com.hackathon.backend.service.ReviewQueueService;
import com.hackathon.backend.service.ReviewQueueService.BulkApproveRequest;
import com.hackathon.backend.service.ReviewQueueService.QueueFilter;
import com.hackathon.backend.service.ReviewQueueService.QueueItem;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.Valid;

/** Approver queue, delegations and the auditor review queue (API-014, 019..023, 038). */
@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

	private final ApprovalService approvals;

	private final DelegationService delegations;

	private final ReviewQueueService reviewQueue;

	private final IdempotencyService idempotency;

	private final CurrentUserService currentUser;

	public WorkflowController(ApprovalService approvals, DelegationService delegations,
			ReviewQueueService reviewQueue, IdempotencyService idempotency, CurrentUserService currentUser) {
		this.approvals = approvals;
		this.delegations = delegations;
		this.reviewQueue = reviewQueue;
		this.idempotency = idempotency;
		this.currentUser = currentUser;
	}

	@GetMapping("/approvals")
	public PageResponse<ApprovalItem> approvals(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		return approvals.queue(currentUser.require(), page, pageSize);
	}

	@GetMapping("/delegations")
	public PageResponse<DelegationView> delegations(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		return delegations.list(currentUser.require(), page, pageSize);
	}

	@PostMapping("/delegations")
	@ResponseStatus(HttpStatus.CREATED)
	public DelegationView createDelegation(@Valid @RequestBody CreateDelegationRequest request) {
		return delegations.create(currentUser.require(), request);
	}

	@DeleteMapping("/delegations/{delegationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelDelegation(@PathVariable UUID delegationId) {
		delegations.cancel(currentUser.require(), delegationId);
	}

	@GetMapping("/delegations/eligible-delegates")
	public List<PersonRef> eligibleDelegates() {
		return delegations.eligibleDelegates(currentUser.require());
	}

	@GetMapping("/review-queue")
	public PageResponse<QueueItem> reviewQueue(@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) ExpenseCategory category,
			@RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate,
			@RequestParam(required = false) String flag, @RequestParam(required = false) String queue,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		User user = currentUser.require();
		return reviewQueue.queue(user,
				new QueueFilter(departmentId, category, minAmount, maxAmount, fromDate, toDate, flag, queue), page,
				pageSize);
	}

	@PostMapping("/review-queue/bulk-approve")
	public ResponseEntity<Object> bulkApprove(@Valid @RequestBody BulkApproveRequest request,
			@RequestHeader(name = ClaimController.IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		return idempotency.run(user, "API-023", key, "/review-queue/bulk-approve", request, HttpStatus.OK,
				() -> reviewQueue.bulkApprove(user, request));
	}

}
