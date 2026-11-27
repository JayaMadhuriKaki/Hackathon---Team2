package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.CLAIMANT;
import static com.hackathon.backend.policy.PolicyFixtures.OTHER_EMPLOYEE;
import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.WED;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.earlier;
import static com.hackathon.backend.policy.PolicyFixtures.inr;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static com.hackathon.backend.policy.PolicyFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** POL-001: meals up to 1,500 INR per day (CL-01, CL-07, SPEC-Q-04). */
class MealsDailyLimitRuleTest {

	@Test
	void exactlyAtLimitPasses() {
		assertThat(run(input(List.of(meals("l1", "1500.00", TUE))), "POL-001")).isEmpty();
	}

	@Test
	void onePaisaOverLimitIsBreach() {
		List<PolicyResult> results = run(input(List.of(meals("l1", "1500.01", TUE))), "POL-001");

		assertThat(results).singleElement().satisfies(r -> {
			assertThat(r.resultType()).isEqualTo(ResultType.LIMIT_BREACH);
			assertThat(r.lineItemId()).isEqualTo("l1");
			assertThat(r.configKey()).isEqualTo("meals.max_per_day");
			assertThat(r.configuredLimit()).isEqualByComparingTo(inr("1500.00"));
			assertThat(r.actualValue()).isEqualTo("1500.01");
			assertThat(r.reason()).isNotBlank();
		});
	}

	@Test
	void linesOnTheSameDayAreSummed() {
		List<PolicyResult> results = run(input(List.of(meals("l1", "800.00", TUE), meals("l2", "700.01", TUE))),
				"POL-001");

		assertThat(results).singleElement().satisfies(r -> {
			assertThat(r.lineItemId()).isEqualTo("l2");
			assertThat(r.actualValue()).isEqualTo("1500.01");
		});
	}

	@Test
	void linesOnDifferentDaysAreNotSummed() {
		assertThat(run(input(List.of(meals("l1", "1000.00", TUE), meals("l2", "1000.00", WED))), "POL-001"))
			.isEmpty();
	}

	@Test
	void claimantsEarlierClaimOnSameDayCounts() {
		PolicyInput in = input(List.of(meals("l1", "600.00", TUE)),
				List.of(earlier(CLAIMANT, "CLM-2026-00001", meals("e1", "1000.00", TUE))));

		assertThat(run(in, "POL-001")).singleElement()
			.satisfies(r -> assertThat(r.actualValue()).isEqualTo("1600.00"));
	}

	@Test
	void anotherEmployeesMealsDoNotCount() {
		PolicyInput in = input(List.of(meals("l1", "600.00", TUE)),
				List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", meals("e1", "1000.00", TUE))));

		assertThat(run(in, "POL-001")).isEmpty();
	}

	@Test
	void otherCategoriesAreIgnored() {
		assertThat(run(input(List.of(cab("l1", "1600.00", TUE))), "POL-001")).isEmpty();
	}

}
