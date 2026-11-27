package com.hackathon.backend.chain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.hackathon.backend.domain.UserRole;

/** New-delegation rules (backend/SPEC.md §5.3, CL-13, ASM-007, B-03). */
class DelegationRulesTest {

	private static final LocalDate D1 = LocalDate.of(2026, 12, 1);

	private static final LocalDate D5 = LocalDate.of(2026, 12, 5);

	private static Optional<DelegationProblem> check(String delegateId, UserRole role, LocalDate start,
			LocalDate end, List<DelegationView> existing) {
		return DelegationRules.validateNew("mgr", delegateId, role, start, end, existing);
	}

	@Test
	void auditorOrFinanceHeadIsAValidDelegate() {
		assertThat(check("fa", UserRole.FINANCE_AUDITOR, D1, D5, List.of())).isEmpty();
		assertThat(check("fh", UserRole.FINANCE_HEAD, D1, D1, List.of())).isEmpty();
	}

	@Test
	void employeeOrAdminIsNotAValidDelegate() {
		assertThat(check("e", UserRole.EMPLOYEE, D1, D5, List.of())).contains(DelegationProblem.INVALID_DELEGATE);
		assertThat(check("a", UserRole.ADMIN, D1, D5, List.of())).contains(DelegationProblem.INVALID_DELEGATE);
	}

	@Test
	void selfDelegationIsInvalid() {
		assertThat(DelegationRules.validateNew("fa", "fa", UserRole.FINANCE_AUDITOR, D1, D5, List.of()))
			.contains(DelegationProblem.INVALID_DELEGATE);
	}

	@Test
	void endBeforeStartIsInvalidDates() {
		assertThat(check("fa", UserRole.FINANCE_AUDITOR, D5, D1, List.of())).contains(DelegationProblem.INVALID_DATES);
	}

	@Test
	void overlappingActiveDelegationIsRefusedButCancelledOneIsIgnored() {
		DelegationView existing = new DelegationView("d", "mgr", "fa", D5, D5.plusDays(3), false);
		DelegationView cancelled = new DelegationView("c", "mgr", "fa", D1, D5, true);

		assertThat(check("fh", UserRole.FINANCE_HEAD, D1, D5, List.of(existing))).contains(DelegationProblem.OVERLAP);
		assertThat(check("fh", UserRole.FINANCE_HEAD, D1, D5.minusDays(1), List.of(existing, cancelled))).isEmpty();
	}

	@Test
	void activeWindowIsInclusive() {
		DelegationView d = new DelegationView("d", "mgr", "fa", D1, D5, false);

		assertThat(DelegationRules.isActive(d, D1)).isTrue();
		assertThat(DelegationRules.isActive(d, D5)).isTrue();
		assertThat(DelegationRules.isActive(d, D5.plusDays(1))).isFalse();
		assertThat(DelegationRules.isActive(d, D1.minusDays(1))).isFalse();
		assertThat(DelegationRules.isActive(new DelegationView("d", "mgr", "fa", D1, D5, true), D1)).isFalse();
	}

}
