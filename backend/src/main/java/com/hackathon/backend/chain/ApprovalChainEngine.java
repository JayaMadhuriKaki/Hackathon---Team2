package com.hackathon.backend.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.domain.UserRole;

/**
 * Computes the approval steps of a routed claim (architecture/SPEC.md §5.2, ADR-001):
 * <ol>
 * <li>a finance head's claim → one FINANCE_AUDITOR step, whatever the amount (CL-16);</li>
 * <li>otherwise MANAGER always, FINANCE_AUDITOR when total &gt; auditor threshold,
 * FINANCE_HEAD when total &gt; finance-head threshold (strictly, CL-03);</li>
 * <li>a level with no approver (CL-09) or where the claimant would approve (ASM-009) is
 * SKIPPED, and the claim moves to the next level, which is added if the amount did not
 * already require it;</li>
 * <li>the first remaining step is PENDING, later ones WAITING.</li>
 * </ol>
 */
public class ApprovalChainEngine {

	public ChainPlan compute(ChainInput in) {
		if (in.claimantRole() == UserRole.FINANCE_HEAD) {
			return withPendingFirst(List.of(active(StepType.FINANCE_AUDITOR, null)));
		}

		TreeSet<Integer> levels = new TreeSet<>();
		levels.add(StepType.MANAGER.level());
		if (in.total().compareTo(in.auditorThreshold()) > 0) {
			levels.add(StepType.FINANCE_AUDITOR.level());
		}
		if (in.total().compareTo(in.financeHeadThreshold()) > 0) {
			levels.add(StepType.FINANCE_HEAD.level());
		}

		List<PlannedStep> steps = new ArrayList<>();
		Integer level = levels.first();
		while (level != null) {
			StepType type = StepType.ofLevel(level);
			SkipReason skip = skipReason(type, in);
			if (skip == null) {
				steps.add(active(type, type == StepType.MANAGER ? in.managerId() : null));
			}
			else {
				steps.add(new PlannedStep(type, null, StepStatus.SKIPPED, skip));
				if (level < StepType.FINANCE_HEAD.level()) {
					levels.add(level + 1);
				}
			}
			level = levels.higher(level);
		}
		return withPendingFirst(steps);
	}

	private static SkipReason skipReason(StepType type, ChainInput in) {
		return switch (type) {
			case MANAGER -> in.managerId() == null ? SkipReason.NO_APPROVER
					: Objects.equals(in.managerId(), in.claimantId()) ? SkipReason.CLAIMANT_IS_APPROVER : null;
			case FINANCE_AUDITOR -> poolSkip(UserRole.FINANCE_AUDITOR, in);
			case FINANCE_HEAD -> poolSkip(UserRole.FINANCE_HEAD, in);
		};
	}

	private static SkipReason poolSkip(UserRole poolRole, ChainInput in) {
		if (in.claimantRole() == poolRole) {
			return SkipReason.CLAIMANT_IS_APPROVER;
		}
		return in.directory().hasUserWithRole(poolRole) ? null : SkipReason.NO_APPROVER;
	}

	private static PlannedStep active(StepType type, String approverId) {
		return new PlannedStep(type, approverId, StepStatus.WAITING, null);
	}

	private static ChainPlan withPendingFirst(List<PlannedStep> steps) {
		List<PlannedStep> result = new ArrayList<>();
		boolean pendingSet = false;
		for (PlannedStep step : steps) {
			if (!pendingSet && step.status() == StepStatus.WAITING) {
				result.add(new PlannedStep(step.stepType(), step.assignedApproverId(), StepStatus.PENDING, null));
				pendingSet = true;
			}
			else {
				result.add(step);
			}
		}
		if (!pendingSet) {
			throw new IllegalStateException("No approver exists at any level; check the seeded users");
		}
		return new ChainPlan(List.copyOf(result));
	}

}
