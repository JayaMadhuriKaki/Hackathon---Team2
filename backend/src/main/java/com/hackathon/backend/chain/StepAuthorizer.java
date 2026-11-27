package com.hackathon.backend.chain;

import java.time.LocalDate;
import java.util.List;

import com.hackathon.backend.domain.StepType;

/** Who may act on the PENDING step or a sample audit (architecture/SPEC.md §5.3). */
public final class StepAuthorizer {

	private StepAuthorizer() {
	}

	public static StepAuthorization forStep(Actor actor, String claimantId, StepType stepType,
			String assignedApproverId, List<DelegationView> delegationsToActor, LocalDate todayIst) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	public static StepAuthorization forSampleAudit(Actor actor, String claimantId) {
		throw new UnsupportedOperationException("not implemented yet");
	}

}
