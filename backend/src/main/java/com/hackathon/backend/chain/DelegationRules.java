package com.hackathon.backend.chain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.hackathon.backend.domain.UserRole;

/** Pure delegation rules (architecture/SPEC.md §5.4). */
public final class DelegationRules {

	private DelegationRules() {
	}

	public static boolean isActive(DelegationView d, LocalDate todayIst) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	public static Optional<DelegationProblem> validateNew(String delegatorId, String delegateId,
			UserRole delegateRole, LocalDate startDate, LocalDate endDate, List<DelegationView> delegatorsExisting) {
		throw new UnsupportedOperationException("not implemented yet");
	}

}
