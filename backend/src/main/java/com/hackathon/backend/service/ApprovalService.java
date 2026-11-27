package com.hackathon.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.chain.AuthorizationOutcome;
import com.hackathon.backend.chain.DelegationRules;
import com.hackathon.backend.chain.StepAuthorization;
import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimComment;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.Delegation;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;
import com.hackathon.backend.dto.ClaimDtos.ClaimDetail;
import com.hackathon.backend.dto.ClaimDtos.CommentView;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.policy.ResultType;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.ClaimCommentRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.DelegationRepository;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.repository.PolicyResultRepository;
import com.hackathon.backend.service.support.Money;
import com.hackathon.backend.service.support.PageResponse;

/**
 * Approver actions (API-014..018; FR-005..007, FR-020, CL-04, CL-14, B-02, B-06). Every action
 * locks the claim, re-checks its state (409 on conflict), checks the actor with the pure
 * {@code StepAuthorizer} (self-approval 422 first, then 403) and audits in the same transaction.
 */
@Service
public class ApprovalService {

	private final ClaimRepository claims;

	private final ApprovalStepRepository steps;

	private final ClaimCommentRepository comments;

	private final DelegationRepository delegations;

	private final PolicyResultRepository results;

	private final DepartmentRepository departments;

	private final ClaimAccessPolicy access;

	private final ClaimViewAssembler views;

	private final AuditService audit;

	private final AppClock clock;

	public ApprovalService(ClaimRepository claims, ApprovalStepRepository steps, ClaimCommentRepository comments,
			DelegationRepository delegations, PolicyResultRepository results, DepartmentRepository departments,
			ClaimAccessPolicy access, ClaimViewAssembler views, AuditService audit, AppClock clock) {
		this.claims = claims;
		this.steps = steps;
		this.comments = comments;
		this.delegations = delegations;
		this.results = results;
		this.departments = departments;
		this.access = access;
		this.views = views;
		this.audit = audit;
		this.clock = clock;
	}

	/** API-015: T-08 / T-09 on the chain, T-12 for a sample audit. */
	@Transactional
	public ClaimDetail approve(User actor, UUID claimId, String comment) {
		Claim claim = lockVisible(actor, claimId);
		requireState(claim, true);
		StepAuthorization auth = authorize(actor, claim);
		Instant now = clock.now();
		String note = blankToNull(comment);
		if (note != null) {
			saveComment(claim, actor, note);
		}
		if (claim.getStatus() == ClaimStatus.SAMPLE_AUDIT_PENDING) {
			claim.setStatus(ClaimStatus.APPROVED);
			claim.setDecidedAt(now);
			audit.claim(AuditAction.CLAIM_APPROVED, claim.getId())
				.actor(actor.getId())
				.states(ClaimStatus.SAMPLE_AUDIT_PENDING, ClaimStatus.APPROVED)
				.comment(note)
				.details(Map.of("sampleAudit", true))
				.save();
			claims.saveAndFlush(claim);
			return views.detail(claim, actor);
		}

		ApprovalStep step = pendingStep(claim);
		act(step, StepStatus.APPROVED, actor, auth, now);
		ClaimStatus from = claim.getStatus();
		audit.claim(AuditAction.STEP_APPROVED, claim.getId())
			.actor(actor.getId())
			.onBehalfOf(onBehalfOf(auth))
			.comment(note)
			.details(Map.of("stepType", step.getStepType().name(), "level", (int) step.getStepOrder()))
			.save();
		Optional<ApprovalStep> next = steps
			.findByClaimIdAndSubmissionNoOrderByStepOrderAsc(claim.getId(), claim.getSubmissionNo())
			.stream()
			.filter(s -> s.getStatus() == StepStatus.WAITING)
			.findFirst();
		if (next.isPresent()) {
			ApprovalStep n = next.get();
			steps.flush();
			n.setStatus(StepStatus.PENDING);
			n.setActivatedAt(now);
			claim.setStatus(n.getStepType().pendingStatus());
			audit.claim(AuditAction.STEP_ACTIVATED, claim.getId())
				.states(from, claim.getStatus())
				.details(Map.of("stepType", n.getStepType().name(), "level", (int) n.getStepOrder()))
				.save();
		}
		else {
			claim.setStatus(ClaimStatus.APPROVED);
			claim.setDecidedAt(now);
			audit.claim(AuditAction.CLAIM_APPROVED, claim.getId())
				.actor(actor.getId())
				.onBehalfOf(onBehalfOf(auth))
				.states(from, ClaimStatus.APPROVED)
				.save();
		}
		claims.saveAndFlush(claim);
		return views.detail(claim, actor);
	}

	/** API-016: T-10 on the chain, T-13 for a sample audit; comment mandatory (CL-04). */
	@Transactional
	public ClaimDetail reject(User actor, UUID claimId, String comment) {
		Claim claim = lockVisible(actor, claimId);
		requireState(claim, true);
		StepAuthorization auth = authorize(actor, claim);
		String text = requireComment(comment, "Add a comment explaining why the claim is rejected");
		Instant now = clock.now();
		ClaimStatus from = claim.getStatus();
		if (from.isPendingChain()) {
			act(pendingStep(claim), StepStatus.REJECTED, actor, auth, now);
		}
		saveComment(claim, actor, text);
		claim.setStatus(ClaimStatus.REJECTED);
		claim.setDecidedAt(now);
		audit.claim(AuditAction.CLAIM_REJECTED, claim.getId())
			.actor(actor.getId())
			.onBehalfOf(onBehalfOf(auth))
			.states(from, ClaimStatus.REJECTED)
			.comment(text)
			.details(Map.of("sampleAudit", from == ClaimStatus.SAMPLE_AUDIT_PENDING))
			.save();
		claims.saveAndFlush(claim);
		return views.detail(claim, actor);
	}

	/** API-017: T-11; comment mandatory (SPEC-Q-03). Not for sample audits. */
	@Transactional
	public ClaimDetail sendBack(User actor, UUID claimId, String comment) {
		Claim claim = lockVisible(actor, claimId);
		requireState(claim, false);
		StepAuthorization auth = authorize(actor, claim);
		String text = requireComment(comment, "Add a comment telling the claimant what to clarify");
		Instant now = clock.now();
		ClaimStatus from = claim.getStatus();
		act(pendingStep(claim), StepStatus.SENT_BACK, actor, auth, now);
		saveComment(claim, actor, text);
		claim.setStatus(ClaimStatus.SENT_BACK);
		audit.claim(AuditAction.CLAIM_SENT_BACK, claim.getId())
			.actor(actor.getId())
			.onBehalfOf(onBehalfOf(auth))
			.states(from, ClaimStatus.SENT_BACK)
			.comment(text)
			.save();
		claims.saveAndFlush(claim);
		return views.detail(claim, actor);
	}

	/** API-018: the current-step approver, or anyone who has acted on the claim (FR-006). */
	@Transactional
	public CommentView comment(User actor, UUID claimId, String body) {
		Claim claim = claims.findForUpdate(claimId).orElseThrow(ClaimAccessPolicy::notFound);
		if (!access.canView(actor, claim)) {
			throw ClaimAccessPolicy.notFound();
		}
		boolean current = access.authorizeCurrent(actor, claim).map(StepAuthorization::isAllowed).orElse(false);
		boolean acted = !claim.getClaimantId().equals(actor.getId()) && access.hasActed(actor, claim);
		if (!current && !acted) {
			throw new ApiException(ErrorCode.NOT_CURRENT_APPROVER, "Only approvers of this claim can comment");
		}
		String text = requireComment(body, "The comment cannot be empty");
		ClaimComment saved = saveComment(claim, actor, text);
		audit.event(AuditAction.COMMENT_ADDED, EntityType.CLAIM, claim.getId())
			.claim(claim.getId())
			.actor(actor.getId())
			.comment(text)
			.save();
		return new CommentView(saved.getId(), ClaimViewAssembler.ref(actor), text, clock.now());
	}

	/**
	 * API-014: manager steps assigned to the caller or to someone they are the active delegate
	 * for, plus finance-head steps for finance heads. Never the caller's own claims.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ApprovalItem> queue(User caller, Integer page, Integer pageSize) {
		int[] p = PageResponse.check(page, pageSize);
		Map<UUID, UUID> onBehalf = new HashMap<>();
		Set<UUID> approverIds = new HashSet<>();
		approverIds.add(caller.getId());
		for (Delegation d : delegations.findByDelegateIdAndCancelledAtIsNull(caller.getId())) {
			if (DelegationRules.isActive(d.toView(), clock.todayIst())) {
				approverIds.add(d.getDelegatorId());
				onBehalf.put(d.getDelegatorId(), d.getDelegatorId());
			}
		}
		List<ApprovalStep> pending = new ArrayList<>(
				steps.findByStatusAndStepTypeAndAssignedApproverIdIn(StepStatus.PENDING, StepType.MANAGER, approverIds));
		if (caller.getRole() == UserRole.FINANCE_HEAD) {
			pending.addAll(steps.findByStatusAndStepType(StepStatus.PENDING, StepType.FINANCE_HEAD));
		}
		Map<UUID, Claim> claimMap = claims.findAllById(pending.stream().map(ApprovalStep::getClaimId).toList())
			.stream()
			.collect(Collectors.toMap(Claim::getId, Function.identity()));
		Set<UUID> people = new HashSet<>(onBehalf.keySet());
		claimMap.values().forEach(c -> people.add(c.getClaimantId()));
		Map<UUID, User> userMap = views.userMap(people);
		Map<UUID, String> deptNames = new HashMap<>();
		departments.findAll().forEach(d -> deptNames.put(d.getId(), d.getName()));

		List<ApprovalItem> items = new ArrayList<>();
		for (ApprovalStep s : pending) {
			Claim c = claimMap.get(s.getClaimId());
			if (c == null || c.getClaimantId().equals(caller.getId())) {
				continue;
			}
			User claimant = userMap.get(c.getClaimantId());
			UUID behalf = s.getStepType() == StepType.MANAGER && !caller.getId().equals(s.getAssignedApproverId())
					? s.getAssignedApproverId() : null;
			items.add(new ApprovalItem(c.getId(), c.getClaimNumber(), c.getStatus(), Money.format(c.getTotalAmount()),
					ClaimViewAssembler.ref(claimant), claimant == null ? null : deptNames.get(claimant.getDepartmentId()),
					s.getStepType().name(), c.getSubmittedAt(), s.getActivatedAt(),
					behalf == null ? null : ClaimViewAssembler.ref(userMap.get(behalf)), flags(c)));
		}
		items.sort(Comparator.comparing(ApprovalItem::pendingSince, Comparator.nullsLast(Comparator.naturalOrder())));
		return PageResponse.ofList(items, p[0], p[1]);
	}

	List<String> flags(Claim c) {
		List<String> flags = new ArrayList<>();
		if (c.getSubmissionNo() > 0) {
			if (results.existsByClaimIdAndSubmissionNoAndResultType(c.getId(), c.getSubmissionNo(),
					ResultType.DUPLICATE_FLAG)) {
				flags.add("DUPLICATE");
			}
			if (results.existsByClaimIdAndSubmissionNoAndResultType(c.getId(), c.getSubmissionNo(),
					ResultType.WEEKEND_FLAG)) {
				flags.add("WEEKEND");
			}
		}
		return flags;
	}

	private Claim lockVisible(User actor, UUID claimId) {
		Claim claim = claims.findForUpdate(claimId).orElseThrow(ClaimAccessPolicy::notFound);
		if (!access.canView(actor, claim)) {
			throw ClaimAccessPolicy.notFound();
		}
		return claim;
	}

	private static void requireState(Claim claim, boolean sampleAuditAllowed) {
		boolean ok = claim.getStatus().isPendingChain()
				|| (sampleAuditAllowed && claim.getStatus() == ClaimStatus.SAMPLE_AUDIT_PENDING);
		if (!ok) {
			throw new ApiException(ErrorCode.STATE_CONFLICT,
					"The claim is " + claim.getStatus() + " and cannot take this action");
		}
	}

	private StepAuthorization authorize(User actor, Claim claim) {
		StepAuthorization auth = access.authorizeCurrent(actor, claim)
			.orElseThrow(() -> new ApiException(ErrorCode.STATE_CONFLICT, "The claim has no step to act on"));
		if (auth.outcome() == AuthorizationOutcome.SELF_APPROVAL) {
			throw new ApiException(ErrorCode.SELF_APPROVAL_FORBIDDEN, "You cannot act on your own claim");
		}
		if (!auth.isAllowed()) {
			throw new ApiException(ErrorCode.NOT_CURRENT_APPROVER, "You are not the approver of the current step");
		}
		return auth;
	}

	private ApprovalStep pendingStep(Claim claim) {
		return steps.findByClaimIdAndStatus(claim.getId(), StepStatus.PENDING)
			.orElseThrow(() -> new ApiException(ErrorCode.STATE_CONFLICT, "The claim has no pending step"));
	}

	private static void act(ApprovalStep step, StepStatus status, User actor, StepAuthorization auth, Instant now) {
		step.setStatus(status);
		step.setActedById(actor.getId());
		step.setOnBehalfOfId(onBehalfOf(auth));
		step.setActedAt(now);
	}

	private static UUID onBehalfOf(StepAuthorization auth) {
		return auth.onBehalfOfId() == null ? null : UUID.fromString(auth.onBehalfOfId());
	}

	private ClaimComment saveComment(Claim claim, User author, String body) {
		ClaimComment c = new ClaimComment();
		c.setClaimId(claim.getId());
		c.setAuthorId(author.getId());
		c.setBody(body);
		return comments.save(c);
	}

	private static String requireComment(String comment, String message) {
		String text = blankToNull(comment);
		if (text == null) {
			throw new ApiException(ErrorCode.COMMENT_REQUIRED, message);
		}
		return text;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record ApprovalItem(UUID claimId, String claimNumber, ClaimStatus status, String total, PersonRef claimant,
			String department, String stepType, Instant submittedAt, Instant pendingSince, PersonRef onBehalfOf,
			List<String> flags) {
	}

}
