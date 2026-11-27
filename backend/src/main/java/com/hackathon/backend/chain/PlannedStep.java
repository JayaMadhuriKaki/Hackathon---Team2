package com.hackathon.backend.chain;

import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;

/**
 * One computed step. {@code assignedApproverId} is set for a non-skipped manager step only;
 * pool steps are open to every holder of the role. {@code skipReason} is set iff SKIPPED.
 */
public record PlannedStep(StepType stepType, String assignedApproverId, StepStatus status, SkipReason skipReason) {

	public int level() {
		return stepType.level();
	}

}
