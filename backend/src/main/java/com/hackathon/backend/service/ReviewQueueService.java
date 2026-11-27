package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.ClaimLineItemRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.service.support.Money;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * The finance auditor's review queue (API-022, API-023; FR-009, FR-010, CL-14): claims at the
 * auditor step and sample audits, never the auditor's own. Bulk approve runs each claim
 * through the normal approve logic in its own transaction and reports per-claim results.
 */
@Service
public class ReviewQueueService {

	private static final int MAX_BULK = 100;

	private final ClaimRepository claims;

	private final ApprovalStepRepository steps;

	private final ClaimLineItemRepository lines;

	private final DepartmentRepository departments;

	private final ApprovalService approvals;

	private final ClaimViewAssembler views;

	public ReviewQueueService(ClaimRepository claims, ApprovalStepRepository steps, ClaimLineItemRepository lines,
			DepartmentRepository departments, ApprovalService approvals, ClaimViewAssembler views) {
		this.claims = claims;
		this.steps = steps;
		this.lines = lines;
		this.departments = departments;
		this.approvals = approvals;
		this.views = views;
	}

	@Transactional(readOnly = true)
	public PageResponse<QueueItem> queue(User caller, QueueFilter f, Integer page, Integer pageSize) {
		requireAuditor(caller);
		int[] p = PageResponse.check(page, pageSize);
		List<Claim> candidates = new ArrayList<>();
		if (f.queue() == null || f.queue().equals("AUDITOR_STEP")) {
			candidates.addAll(claims.findByStatus(ClaimStatus.PENDING_AUDITOR));
		}
		if (f.queue() == null || f.queue().equals("SAMPLE_AUDIT")) {
			candidates.addAll(claims.findByStatus(ClaimStatus.SAMPLE_AUDIT_PENDING));
		}
		candidates.removeIf(c -> c.getClaimantId().equals(caller.getId()));
		List<UUID> ids = candidates.stream().map(Claim::getId).toList();
		Map<UUID, Instant> activated = steps.findByClaimIdInAndStatus(ids, StepStatus.PENDING)
			.stream()
			.collect(Collectors.toMap(ApprovalStep::getClaimId, ApprovalStep::getActivatedAt, (a, b) -> a));
		Map<UUID, Set<ExpenseCategory>> categories = new HashMap<>();
		lines.findByClaimIdIn(ids)
			.forEach(l -> categories.computeIfAbsent(l.getClaimId(), k -> new LinkedHashSet<>()).add(l.getCategory()));
		Map<UUID, User> userMap = views.userMap(candidates.stream().map(Claim::getClaimantId).collect(Collectors.toSet()));
		Map<UUID, String> deptNames = new HashMap<>();
		departments.findAll().forEach(d -> deptNames.put(d.getId(), d.getName()));

		List<QueueItem> items = new ArrayList<>();
		for (Claim c : candidates) {
			User claimant = userMap.get(c.getClaimantId());
			List<String> flags = approvals.flags(c);
			Set<ExpenseCategory> cats = categories.getOrDefault(c.getId(), Set.of());
			LocalDate submitted = c.getSubmittedAt() == null ? null
					: c.getSubmittedAt().atZone(AppClock.IST).toLocalDate();
			if (f.departmentId() != null && (claimant == null || !f.departmentId().equals(claimant.getDepartmentId()))) {
				continue;
			}
			if (f.category() != null && !cats.contains(f.category())) {
				continue;
			}
			if (f.minAmount() != null && c.getTotalAmount().compareTo(f.minAmount()) < 0) {
				continue;
			}
			if (f.maxAmount() != null && c.getTotalAmount().compareTo(f.maxAmount()) > 0) {
				continue;
			}
			if (f.fromDate() != null && (submitted == null || submitted.isBefore(f.fromDate()))) {
				continue;
			}
			if (f.toDate() != null && (submitted == null || submitted.isAfter(f.toDate()))) {
				continue;
			}
			if (f.flag() != null && !flags.contains(f.flag())) {
				continue;
			}
			boolean sample = c.getStatus() == ClaimStatus.SAMPLE_AUDIT_PENDING;
			items.add(new QueueItem(c.getId(), c.getClaimNumber(), c.getStatus(), sample ? "SAMPLE_AUDIT" : "AUDITOR_STEP",
					Money.format(c.getTotalAmount()), ClaimViewAssembler.ref(claimant),
					claimant == null ? null : deptNames.get(claimant.getDepartmentId()),
					cats.stream().map(Enum::name).toList(), flags, c.getSubmittedAt(),
					sample ? c.getSampleAuditSelectedAt() : activated.get(c.getId())));
		}
		items.sort(Comparator.comparing(QueueItem::waitingSince, Comparator.nullsLast(Comparator.naturalOrder())));
		return PageResponse.ofList(items, p[0], p[1]);
	}

	/** API-023: not transactional itself, so each approve commits or fails on its own. */
	public BulkResult bulkApprove(User caller, BulkApproveRequest request) {
		requireAuditor(caller);
		List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(request.claimIds()));
		if (ids.size() > MAX_BULK) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "At most 100 claims per bulk approve");
		}
		List<BulkItem> results = new ArrayList<>();
		for (UUID id : ids) {
			try {
				Claim claim = claims.findById(id).orElseThrow(ClaimAccessPolicy::notFound);
				if (claim.getStatus() != ClaimStatus.PENDING_AUDITOR
						&& claim.getStatus() != ClaimStatus.SAMPLE_AUDIT_PENDING) {
					throw new ApiException(ErrorCode.STATE_CONFLICT,
							"Only claims in the review queue can be bulk approved (now " + claim.getStatus() + ")");
				}
				approvals.approve(caller, id, null);
				results.add(new BulkItem(id, "APPROVED", null, null));
			}
			catch (ApiException ex) {
				results.add(new BulkItem(id, "FAILED", ex.getCode().name(), ex.getMessage()));
			}
		}
		long approved = results.stream().filter(r -> r.outcome().equals("APPROVED")).count();
		return new BulkResult(results, (int) approved, results.size() - (int) approved);
	}

	private static void requireAuditor(User caller) {
		if (caller.getRole() != UserRole.FINANCE_AUDITOR) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only finance auditors can use the review queue");
		}
	}

	public record QueueFilter(UUID departmentId, ExpenseCategory category, BigDecimal minAmount, BigDecimal maxAmount,
			LocalDate fromDate, LocalDate toDate, String flag, String queue) {

		public QueueFilter {
			if (flag != null && !Set.of("DUPLICATE", "WEEKEND").contains(flag)) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "flag must be DUPLICATE or WEEKEND");
			}
			if (queue != null && !Set.of("AUDITOR_STEP", "SAMPLE_AUDIT").contains(queue)) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "queue must be AUDITOR_STEP or SAMPLE_AUDIT");
			}
		}

	}

	public record QueueItem(UUID claimId, String claimNumber, ClaimStatus status, String queue, String total,
			PersonRef claimant, String department, List<String> categories, List<String> flags, Instant submittedAt,
			Instant waitingSince) {
	}

	public record BulkApproveRequest(@NotEmpty @Size(max = 100) List<UUID> claimIds) {
	}

	public record BulkItem(UUID claimId, String outcome, String code, String message) {
	}

	public record BulkResult(List<BulkItem> results, int approved, int failed) {
	}

}
