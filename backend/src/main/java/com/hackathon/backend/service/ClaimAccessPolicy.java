package com.hackathon.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.hackathon.backend.chain.Actor;
import com.hackathon.backend.chain.DelegationView;
import com.hackathon.backend.chain.StepAuthorization;
import com.hackathon.backend.chain.StepAuthorizer;
import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.Delegation;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.AuditEventRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.DelegationRepository;

/**
 * Object-level access (FR-019, NFR-005, CL-15, ASM-008, B-10). A claim is visible to its
 * claimant; to whoever may act on its current PENDING step (incl. active delegates and
 * pool-role holders, never the claimant); to finance auditors for a sample-audit claim that is
 * not theirs; and to anyone who has acted on it. Not visible means 404, never 403.
 */
@Service
public class ClaimAccessPolicy {

	static final Set<AuditAction> ACTING = Set.of(AuditAction.STEP_APPROVED, AuditAction.CLAIM_APPROVED,
			AuditAction.CLAIM_REJECTED, AuditAction.CLAIM_SENT_BACK, AuditAction.COMMENT_ADDED);

	private final ClaimRepository claims;

	private final ApprovalStepRepository steps;

	private final DelegationRepository delegations;

	private final AuditEventRepository auditEvents;

	private final AppClock clock;

	public ClaimAccessPolicy(ClaimRepository claims, ApprovalStepRepository steps, DelegationRepository delegations,
			AuditEventRepository auditEvents, AppClock clock) {
		this.claims = claims;
		this.steps = steps;
		this.delegations = delegations;
		this.auditEvents = auditEvents;
		this.clock = clock;
	}

	public Claim requireVisible(User user, java.util.UUID claimId) {
		Claim claim = claims.findById(claimId).orElseThrow(ClaimAccessPolicy::notFound);
		if (!canView(user, claim)) {
			throw notFound();
		}
		return claim;
	}

	public boolean canView(User user, Claim claim) {
		if (claim.getClaimantId().equals(user.getId())) {
			return true;
		}
		if (authorizeCurrent(user, claim).map(StepAuthorization::isAllowed).orElse(false)) {
			return true;
		}
		return auditEvents.existsByClaimIdAndActorIdAndActionIn(claim.getId(), user.getId(), ACTING);
	}

	/**
	 * The current-step check for an acting user, or empty when the claim has nothing to act
	 * on (not pending and not in sample audit).
	 */
	public Optional<StepAuthorization> authorizeCurrent(User user, Claim claim) {
		Actor actor = new Actor(user.getId().toString(), user.getRole());
		String claimantId = claim.getClaimantId().toString();
		if (claim.getStatus() == ClaimStatus.SAMPLE_AUDIT_PENDING) {
			return Optional.of(StepAuthorizer.forSampleAudit(actor, claimantId));
		}
		if (!claim.getStatus().isPendingChain()) {
			return Optional.empty();
		}
		Optional<ApprovalStep> pending = steps.findByClaimIdAndStatus(claim.getId(), StepStatus.PENDING);
		if (pending.isEmpty()) {
			return Optional.empty();
		}
		ApprovalStep step = pending.get();
		List<DelegationView> toActor = delegations.findByDelegateIdAndCancelledAtIsNull(user.getId())
			.stream()
			.map(Delegation::toView)
			.toList();
		return Optional.of(StepAuthorizer.forStep(actor, claimantId, step.getStepType(),
				step.getAssignedApproverId() == null ? null : step.getAssignedApproverId().toString(), toActor,
				clock.todayIst()));
	}

	public boolean hasActed(User user, Claim claim) {
		return auditEvents.existsByClaimIdAndActorIdAndActionIn(claim.getId(), user.getId(), ACTING);
	}

	public static ApiException notFound() {
		return new ApiException(ErrorCode.NOT_FOUND, "Claim not found");
	}

}
