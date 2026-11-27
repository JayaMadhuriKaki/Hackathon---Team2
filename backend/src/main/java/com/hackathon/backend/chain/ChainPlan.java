package com.hackathon.backend.chain;

import java.util.List;

import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.StepStatus;

/** The ordered steps computed at submit; exactly one is PENDING. */
public record ChainPlan(List<PlannedStep> steps) {

	public PlannedStep pendingStep() {
		return steps.stream().filter(s -> s.status() == StepStatus.PENDING).findFirst().orElseThrow();
	}

	/** Claim state after routing (T-04). */
	public ClaimStatus claimStatus() {
		return pendingStep().stepType().pendingStatus();
	}

}
