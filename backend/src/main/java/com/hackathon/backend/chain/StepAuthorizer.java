package com.hackathon.backend.chain;

import java.time.LocalDate;
import java.util.List;

import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.domain.UserRole;

/**
 * Who may act on the PENDING step or on a sample audit (architecture/SPEC.md §5.3,
 * backend/SPEC.md §5.2). Order: self-approval (B-02), ADMIN, then the step's own rule.
 */
public final class StepAuthorizer {

	private StepAuthorizer() {
	}

	/**
	 * @param delegationsToActor delegations where the actor is the delegate; only those whose
	 * delegator is the assigned manager count, so delegations are one hop (B-03)
	 */
	public static StepAuthorization forStep(Actor actor, String claimantId, StepType stepType,
			String assignedApproverId, List<DelegationView> delegationsToActor, LocalDate todayIst) {
		if (actor.userId().equals(claimantId)) {
			return StepAuthorization.denied(AuthorizationOutcome.SELF_APPROVAL);
		}
		if (actor.role() == UserRole.ADMIN) {
			return StepAuthorization.denied(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		}
		return switch (stepType) {
			case MANAGER -> managerStep(actor, assignedApproverId, delegationsToActor, todayIst);
			case FINANCE_AUDITOR -> pool(actor, UserRole.FINANCE_AUDITOR);
			case FINANCE_HEAD -> pool(actor, UserRole.FINANCE_HEAD);
		};
	}

	public static StepAuthorization forSampleAudit(Actor actor, String claimantId) {
		if (actor.userId().equals(claimantId)) {
			return StepAuthorization.denied(AuthorizationOutcome.SELF_APPROVAL);
		}
		return pool(actor, UserRole.FINANCE_AUDITOR);
	}

	private static StepAuthorization managerStep(Actor actor, String assignedApproverId,
			List<DelegationView> delegationsToActor, LocalDate todayIst) {
		if (assignedApproverId == null) {
			return StepAuthorization.denied(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		}
		if (actor.userId().equals(assignedApproverId)) {
			return StepAuthorization.allowed(null);
		}
		boolean delegate = delegationsToActor.stream()
			.anyMatch(d -> d.delegateId().equals(actor.userId()) && d.delegatorId().equals(assignedApproverId)
					&& DelegationRules.isActive(d, todayIst));
		return delegate ? StepAuthorization.allowed(assignedApproverId)
				: StepAuthorization.denied(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	private static StepAuthorization pool(Actor actor, UserRole role) {
		return actor.role() == role ? StepAuthorization.allowed(null)
				: StepAuthorization.denied(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

}
