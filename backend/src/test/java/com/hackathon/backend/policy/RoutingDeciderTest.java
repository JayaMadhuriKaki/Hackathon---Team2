package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.SAT;
import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.inr;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static com.hackathon.backend.policy.PolicyFixtures.withVendor;
import static com.hackathon.backend.policy.PolicyFixtures.withoutReceipt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hackathon.backend.domain.UserRole;

/**
 * Claim-level routing in the fixed order of CLAUDE.md §2.7: breach → auto-reject (CL-07);
 * else total > 25,000, weekend, duplicate or finance-head claimant → route (CL-02, CL-03,
 * ASM-004, SPEC-Q-14); else auto-approve.
 */
class RoutingDeciderTest {

	private static final java.math.BigDecimal AUDITOR_THRESHOLD = inr("25000.00");

	private final RoutingDecider decider = new RoutingDecider();

	private static PolicyEvaluation evaluate(List<LineInput> lines) {
		return PolicyEngine.withDocumentedRules().evaluate(input(lines));
	}

	@Test
	void anyBreachAutoRejectsEvenWithFlagsAndHighTotal() {
		PolicyEvaluation eval = evaluate(List.of(meals("l1", "1600.00", SAT), cab("l2", "2100.00", TUE)));

		RoutingDecision d = decider.decide(eval, inr("30000.00"), UserRole.EMPLOYEE, AUDITOR_THRESHOLD);

		assertThat(d.outcome()).isEqualTo(RoutingOutcome.AUTO_REJECT);
		assertThat(d.drivingResults()).extracting(PolicyResult::lineItemId).containsExactlyInAnyOrder("l1", "l2");
		assertThat(d.drivingResults()).allMatch(r -> r.resultType() == ResultType.LIMIT_BREACH);
		assertThat(d.reason()).contains("POL-001").contains("POL-004");
	}

	@ParameterizedTest(name = "total {0} -> {1}")
	@CsvSource({ "24999.99, AUTO_APPROVE", "25000.00, AUTO_APPROVE", "25000.01, ROUTE" })
	void auditorThresholdIsStrictlyGreaterThan(String total, RoutingOutcome expected) {
		PolicyEvaluation clean = evaluate(List.of(meals("l1", "400.00", TUE)));

		assertThat(decider.decide(clean, inr(total), UserRole.EMPLOYEE, AUDITOR_THRESHOLD).outcome())
			.isEqualTo(expected);
	}

	@Test
	void weekendLineRoutesEvenForSmallClaim() {
		RoutingDecision d = decider.decide(evaluate(List.of(meals("l1", "100.00", SAT))), inr("100.00"),
				UserRole.EMPLOYEE, AUDITOR_THRESHOLD);

		assertThat(d.outcome()).isEqualTo(RoutingOutcome.ROUTE);
		assertThat(d.drivingResults()).extracting(PolicyResult::ruleId).containsExactly("POL-008");
	}

	@Test
	void duplicateFlagRoutes() {
		List<LineInput> lines = List.of(withVendor(cab("l1", "300.00", TUE), "Acme"),
				withVendor(cab("l2", "300.00", TUE), "Acme"));

		RoutingDecision d = decider.decide(evaluate(lines), inr("600.00"), UserRole.EMPLOYEE, AUDITOR_THRESHOLD);

		assertThat(d.outcome()).isEqualTo(RoutingOutcome.ROUTE);
		assertThat(d.drivingResults()).extracting(PolicyResult::ruleId).contains("POL-006");
	}

	@Test
	void financeHeadCleanSmallClaimStillRoutes() {
		RoutingDecision d = decider.decide(evaluate(List.of(meals("l1", "100.00", TUE))), inr("100.00"),
				UserRole.FINANCE_HEAD, AUDITOR_THRESHOLD);

		assertThat(d.outcome()).isEqualTo(RoutingOutcome.ROUTE);
		assertThat(d.reason()).containsIgnoringCase("finance head");
	}

	@Test
	void financeAuditorCleanSmallClaimAutoApproves() {
		assertThat(decider.decide(evaluate(List.of(meals("l1", "100.00", TUE))), inr("100.00"),
				UserRole.FINANCE_AUDITOR, AUDITOR_THRESHOLD).outcome())
			.isEqualTo(RoutingOutcome.AUTO_APPROVE);
	}

	@Test
	void validationResultsMustBeHandledBeforeRouting() {
		PolicyEvaluation eval = evaluate(List.of(withoutReceipt(meals("l1", "900.00", TUE))));

		assertThatThrownBy(() -> decider.decide(eval, inr("900.00"), UserRole.EMPLOYEE, AUDITOR_THRESHOLD))
			.isInstanceOf(IllegalStateException.class);
	}

}
