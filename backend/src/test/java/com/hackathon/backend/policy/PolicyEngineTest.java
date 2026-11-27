package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.SAT;
import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The engine runs every rule on every line and lists every breaching line (CL-07). */
class PolicyEngineTest {

	@Test
	void cleanClaimHasNoResults() {
		PolicyEvaluation eval = PolicyEngine.withDocumentedRules()
			.evaluate(input(List.of(meals("l1", "400.00", TUE), cab("l2", "300.00", TUE))));

		assertThat(eval.results()).isEmpty();
		assertThat(eval.configVersion()).isEqualTo(7);
	}

	@Test
	void everyBreachingLineIsReported() {
		PolicyEvaluation eval = PolicyEngine.withDocumentedRules()
			.evaluate(input(List.of(meals("l1", "1600.00", TUE), cab("l2", "2100.00", TUE), cab("l3", "100.00", TUE))));

		assertThat(eval.of(ResultType.LIMIT_BREACH)).extracting(PolicyResult::lineItemId)
			.containsExactlyInAnyOrder("l1", "l2");
		assertThat(eval.has(ResultType.LIMIT_BREACH)).isTrue();
	}

	@Test
	void flagsAndBreachesAreReportedTogether() {
		PolicyEvaluation eval = PolicyEngine.withDocumentedRules()
			.evaluate(input(List.of(cab("l1", "2500.00", SAT))));

		assertThat(eval.results()).extracting(PolicyResult::ruleId).containsExactlyInAnyOrder("POL-004", "POL-008");
	}

}
