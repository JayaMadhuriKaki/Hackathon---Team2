package com.hackathon.backend.chain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.domain.UserRole;

/**
 * Current-step authorization (backend/SPEC.md §5.2): self-approval first (B-02), ADMIN never
 * approves, manager steps for the manager or an active one-hop delegate (CL-13, ASM-007,
 * ASM-S-04, B-03), pool steps for any role holder, sample audits for finance auditors (CL-14).
 */
class StepAuthorizerTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 23);

	private static final LocalDate END = LocalDate.of(2026, 11, 27);

	private static final Actor MANAGER = new Actor("mgr", UserRole.EMPLOYEE);

	private static final Actor AUDITOR = new Actor("fa", UserRole.FINANCE_AUDITOR);

	private static final Actor HEAD = new Actor("fh", UserRole.FINANCE_HEAD);

	private static DelegationView mgrToAuditor(boolean cancelled) {
		return new DelegationView("d1", "mgr", "fa", START, END, cancelled);
	}

	private static StepAuthorization managerStep(Actor actor, String claimant, List<DelegationView> delegations,
			LocalDate today) {
		return StepAuthorizer.forStep(actor, claimant, StepType.MANAGER, "mgr", delegations, today);
	}

	@Test
	void assignedManagerMayAct() {
		StepAuthorization a = managerStep(MANAGER, "emp", List.of(), START);

		assertThat(a.isAllowed()).isTrue();
		assertThat(a.onBehalfOfId()).isNull();
	}

	@Test
	void delegateMayActOnStartAndEndDateInclusive() {
		for (LocalDate day : List.of(START, END)) {
			StepAuthorization a = managerStep(AUDITOR, "emp", List.of(mgrToAuditor(false)), day);
			assertThat(a.isAllowed()).as(day.toString()).isTrue();
			assertThat(a.onBehalfOfId()).isEqualTo("mgr");
		}
	}

	@Test
	void delegateMayNotActOutsideTheWindow() {
		assertThat(managerStep(AUDITOR, "emp", List.of(mgrToAuditor(false)), START.minusDays(1)).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		assertThat(managerStep(AUDITOR, "emp", List.of(mgrToAuditor(false)), END.plusDays(1)).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void cancelledDelegationGivesNoRight() {
		assertThat(managerStep(AUDITOR, "emp", List.of(mgrToAuditor(true)), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void delegatorKeepsTheRightDuringTheDelegation() {
		assertThat(managerStep(MANAGER, "emp", List.of(mgrToAuditor(false)), START).isAllowed()).isTrue();
	}

	@Test
	void delegationOfADelegationIsNotFollowed() {
		Actor other = new Actor("fa2", UserRole.FINANCE_AUDITOR);
		DelegationView faToFa2 = new DelegationView("d2", "fa", "fa2", START, END, false);

		assertThat(managerStep(other, "emp", List.of(faToFa2), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void delegateWhoIsTheClaimantGetsSelfApprovalFirst() {
		assertThat(managerStep(AUDITOR, "fa", List.of(mgrToAuditor(false)), START).outcome())
			.isEqualTo(AuthorizationOutcome.SELF_APPROVAL);
	}

	@Test
	void claimantCannotActEvenAsAssignedApprover() {
		assertThat(managerStep(MANAGER, "mgr", List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.SELF_APPROVAL);
	}

	@Test
	void adminNeverApproves() {
		Actor admin = new Actor("mgr", UserRole.ADMIN);

		assertThat(managerStep(admin, "emp", List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		assertThat(StepAuthorizer.forSampleAudit(new Actor("adm", UserRole.ADMIN), "emp").outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void unrelatedUserCannotActOnManagerStep() {
		assertThat(managerStep(new Actor("x", UserRole.EMPLOYEE), "emp", List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void poolStepsAreOpenToAnyHolderOfTheRoleOnly() {
		assertThat(StepAuthorizer.forStep(AUDITOR, "emp", StepType.FINANCE_AUDITOR, null, List.of(), START)
			.isAllowed()).isTrue();
		assertThat(StepAuthorizer.forStep(HEAD, "emp", StepType.FINANCE_AUDITOR, null, List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		assertThat(StepAuthorizer.forStep(HEAD, "emp", StepType.FINANCE_HEAD, null, List.of(), START).isAllowed())
			.isTrue();
		assertThat(StepAuthorizer.forStep(AUDITOR, "emp", StepType.FINANCE_HEAD, null, List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
	}

	@Test
	void poolStepOnOwnClaimIsSelfApproval() {
		assertThat(StepAuthorizer.forStep(AUDITOR, "fa", StepType.FINANCE_AUDITOR, null, List.of(), START).outcome())
			.isEqualTo(AuthorizationOutcome.SELF_APPROVAL);
	}

	@Test
	void sampleAuditIsForFinanceAuditorsOtherThanTheClaimant() {
		assertThat(StepAuthorizer.forSampleAudit(AUDITOR, "emp").isAllowed()).isTrue();
		assertThat(StepAuthorizer.forSampleAudit(HEAD, "emp").outcome())
			.isEqualTo(AuthorizationOutcome.NOT_CURRENT_APPROVER);
		assertThat(StepAuthorizer.forSampleAudit(AUDITOR, "fa").outcome())
			.isEqualTo(AuthorizationOutcome.SELF_APPROVAL);
	}

}
