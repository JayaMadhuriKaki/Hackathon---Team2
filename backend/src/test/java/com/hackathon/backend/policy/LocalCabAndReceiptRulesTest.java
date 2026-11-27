package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.inr;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static com.hackathon.backend.policy.PolicyFixtures.run;
import static com.hackathon.backend.policy.PolicyFixtures.withoutReceipt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** POL-004 local cab 2,000 per trip; POL-005 receipt mandatory above 500 (ASM-003). */
class LocalCabAndReceiptRulesTest {

	@Test
	void cabAtLimitPasses() {
		assertThat(run(input(List.of(cab("c1", "2000.00", TUE))), "POL-004")).isEmpty();
	}

	@Test
	void cabOnePaisaOverIsBreach() {
		assertThat(run(input(List.of(cab("c1", "2000.01", TUE))), "POL-004")).singleElement().satisfies(r -> {
			assertThat(r.resultType()).isEqualTo(ResultType.LIMIT_BREACH);
			assertThat(r.configKey()).isEqualTo("local_cab.max_per_trip");
			assertThat(r.configuredLimit()).isEqualByComparingTo(inr("2000.00"));
			assertThat(r.actualValue()).isEqualTo("2000.01");
		});
	}

	@Test
	void cabLimitIgnoresOtherCategories() {
		assertThat(run(input(List.of(meals("m1", "2500.00", TUE))), "POL-004")).isEmpty();
	}

	@Test
	void exactly500WithoutReceiptIsAllowed() {
		assertThat(run(input(List.of(withoutReceipt(meals("m1", "500.00", TUE)))), "POL-005")).isEmpty();
	}

	@Test
	void above500WithoutReceiptIsValidationResult() {
		assertThat(run(input(List.of(withoutReceipt(meals("m1", "500.01", TUE)))), "POL-005")).singleElement()
			.satisfies(r -> {
				assertThat(r.resultType()).isEqualTo(ResultType.VALIDATION);
				assertThat(r.configKey()).isEqualTo("receipt.mandatory_above");
				assertThat(r.configuredLimit()).isEqualByComparingTo(inr("500.00"));
			});
	}

	@Test
	void above500WithReceiptPasses() {
		assertThat(run(input(List.of(meals("m1", "1200.00", TUE))), "POL-005")).isEmpty();
	}

	@Test
	void receiptRuleAppliesToEveryCategory() {
		assertThat(run(input(List.of(withoutReceipt(cab("c1", "800.00", TUE)))), "POL-005")).hasSize(1);
	}

}
