package com.hackathon.backend.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.domain.UserRole;

/**
 * Chain computation at submit (architecture/SPEC.md §5.2): thresholds strictly greater than
 * (CL-03), skip-to-next-level (CL-09, ASM-009), finance head's claims to an auditor (CL-16).
 */
class ApprovalChainEngineTest {

	private static final BigDecimal AUDITOR = new BigDecimal("25000.00");

	private static final BigDecimal FINANCE_HEAD = new BigDecimal("100000.00");

	private static final ApproverDirectory EVERYONE = role -> true;

	private final ApprovalChainEngine engine = new ApprovalChainEngine();

	private static ChainInput input(UserRole role, String managerId, String total) {
		return input(role, managerId, total, EVERYONE);
	}

	private static ChainInput input(UserRole role, String managerId, String total, ApproverDirectory dir) {
		return new ChainInput("claimant", role, managerId, new BigDecimal(total), AUDITOR, FINANCE_HEAD, dir);
	}

	private static List<String> summary(ChainPlan plan) {
		return plan.steps()
			.stream()
			.map(s -> s.stepType() + ":" + s.status() + (s.skipReason() == null ? "" : ":" + s.skipReason()))
			.toList();
	}

	@ParameterizedTest(name = "total {0} -> {1}")
	@CsvSource(delimiter = '|', value = { "3000.00 | MANAGER:PENDING", "24999.99 | MANAGER:PENDING",
			"25000.00 | MANAGER:PENDING", "25000.01 | MANAGER:PENDING,FINANCE_AUDITOR:WAITING",
			"99999.99 | MANAGER:PENDING,FINANCE_AUDITOR:WAITING",
			"100000.00 | MANAGER:PENDING,FINANCE_AUDITOR:WAITING",
			"100000.01 | MANAGER:PENDING,FINANCE_AUDITOR:WAITING,FINANCE_HEAD:WAITING" })
	void levelsFollowStrictlyGreaterThanThresholds(String total, String expected) {
		ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, "mgr", total));

		assertThat(summary(plan)).containsExactly(expected.split(","));
		assertThat(plan.claimStatus()).isEqualTo(ClaimStatus.PENDING_MANAGER);
	}

	@Test
	void managerStepIsAssignedToTheClaimantsManagerAndPoolStepsAreUnassigned() {
		ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, "mgr", "150000.00"));

		assertThat(plan.steps()).extracting(PlannedStep::assignedApproverId).containsExactly("mgr", null, null);
		assertThat(plan.steps()).extracting(PlannedStep::level).containsExactly(1, 2, 3);
	}

	@Test
	void noManagerSkipsToAnAddedAuditorStep() {
		ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, null, "3000.00"));

		assertThat(summary(plan)).containsExactly("MANAGER:SKIPPED:NO_APPROVER", "FINANCE_AUDITOR:PENDING");
		assertThat(plan.claimStatus()).isEqualTo(ClaimStatus.PENDING_AUDITOR);
		assertThat(plan.steps().get(0).assignedApproverId()).isNull();
	}

	@Test
	void noManagerAboveAuditorThresholdDoesNotDuplicateTheAuditorStep() {
		ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, null, "30000.00"));

		assertThat(summary(plan)).containsExactly("MANAGER:SKIPPED:NO_APPROVER", "FINANCE_AUDITOR:PENDING");
	}

	@Test
	void financeAuditorsOwnClaimSkipsAuditorAndAddsFinanceHead() {
		ChainPlan plan = engine.compute(input(UserRole.FINANCE_AUDITOR, "fh", "30000.00"));

		assertThat(summary(plan)).containsExactly("MANAGER:PENDING", "FINANCE_AUDITOR:SKIPPED:CLAIMANT_IS_APPROVER",
				"FINANCE_HEAD:WAITING");
	}

	@Test
	void financeAuditorsOwnClaimAboveFinanceHeadThreshold() {
		ChainPlan plan = engine.compute(input(UserRole.FINANCE_AUDITOR, "fh", "150000.00"));

		assertThat(summary(plan)).containsExactly("MANAGER:PENDING", "FINANCE_AUDITOR:SKIPPED:CLAIMANT_IS_APPROVER",
				"FINANCE_HEAD:WAITING");
	}

	@Test
	void financeAuditorsSmallClaimNeedsOnlyTheManager() {
		assertThat(summary(engine.compute(input(UserRole.FINANCE_AUDITOR, "fh", "3000.00"))))
			.containsExactly("MANAGER:PENDING");
	}

	@ParameterizedTest(name = "finance head claim {0}")
	@CsvSource({ "100.00", "25000.01", "200000.00" })
	void financeHeadsClaimGoesToOneAuditorStepWhateverTheAmount(String total) {
		ChainPlan plan = engine.compute(input(UserRole.FINANCE_HEAD, null, total));

		assertThat(summary(plan)).containsExactly("FINANCE_AUDITOR:PENDING");
		assertThat(plan.claimStatus()).isEqualTo(ClaimStatus.PENDING_AUDITOR);
	}

	@Test
	void managerWhoIsTheClaimantIsSkipped() {
		ChainPlan plan = engine.compute(new ChainInput("claimant", UserRole.EMPLOYEE, "claimant",
				new BigDecimal("100.00"), AUDITOR, FINANCE_HEAD, EVERYONE));

		assertThat(summary(plan)).containsExactly("MANAGER:SKIPPED:CLAIMANT_IS_APPROVER", "FINANCE_AUDITOR:PENDING");
	}

	@Test
	void emptyPoolIsSkippedToTheNextLevel() {
		ApproverDirectory noAuditors = role -> role != UserRole.FINANCE_AUDITOR;

		ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, null, "3000.00", noAuditors));

		assertThat(summary(plan)).containsExactly("MANAGER:SKIPPED:NO_APPROVER",
				"FINANCE_AUDITOR:SKIPPED:NO_APPROVER", "FINANCE_HEAD:PENDING");
	}

	@Test
	void exactlyOneStepIsPendingAndLaterStepsWait() {
		for (String total : List.of("100.00", "30000.00", "150000.00")) {
			ChainPlan plan = engine.compute(input(UserRole.EMPLOYEE, "mgr", total));
			assertThat(plan.steps()).filteredOn(s -> s.status() == StepStatus.PENDING).hasSize(1);
			assertThat(plan.steps().get(0).status()).isEqualTo(StepStatus.PENDING);
			assertThat(plan.steps().stream().skip(1)).allMatch(s -> s.status() == StepStatus.WAITING);
		}
	}

	@Test
	void chainThatCannotBeFilledIsASeedDefect() {
		ApproverDirectory nobody = role -> false;

		assertThatThrownBy(() -> engine.compute(input(UserRole.EMPLOYEE, null, "100.00", nobody)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void pendingStepTypesMapToClaimStates() {
		assertThat(StepType.MANAGER.pendingStatus()).isEqualTo(ClaimStatus.PENDING_MANAGER);
		assertThat(StepType.FINANCE_HEAD.pendingStatus()).isEqualTo(ClaimStatus.PENDING_FINANCE_HEAD);
	}

}
