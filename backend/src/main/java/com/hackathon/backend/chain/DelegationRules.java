package com.hackathon.backend.chain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.hackathon.backend.domain.UserRole;

/**
 * Pure delegation rules (architecture/SPEC.md §5.4): the delegate is a finance auditor or a
 * finance head and not the delegator (CL-13, B-03); dates are inclusive IST calendar days;
 * one uncancelled delegation per delegator at any time (ASM-007).
 */
public final class DelegationRules {

	private DelegationRules() {
	}

	public static boolean isActive(DelegationView d, LocalDate todayIst) {
		return !d.cancelled() && !todayIst.isBefore(d.startDate()) && !todayIst.isAfter(d.endDate());
	}

	public static Optional<DelegationProblem> validateNew(String delegatorId, String delegateId,
			UserRole delegateRole, LocalDate startDate, LocalDate endDate, List<DelegationView> delegatorsExisting) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			return Optional.of(DelegationProblem.INVALID_DATES);
		}
		boolean financeRole = delegateRole == UserRole.FINANCE_AUDITOR || delegateRole == UserRole.FINANCE_HEAD;
		if (!financeRole || delegatorId.equals(delegateId)) {
			return Optional.of(DelegationProblem.INVALID_DELEGATE);
		}
		boolean overlaps = delegatorsExisting.stream()
			.filter(d -> !d.cancelled() && d.delegatorId().equals(delegatorId))
			.anyMatch(d -> !startDate.isAfter(d.endDate()) && !endDate.isBefore(d.startDate()));
		return overlaps ? Optional.of(DelegationProblem.OVERLAP) : Optional.empty();
	}

}
