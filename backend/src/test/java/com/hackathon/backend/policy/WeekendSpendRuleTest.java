package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.FRI;
import static com.hackathon.backend.policy.PolicyFixtures.SAT;
import static com.hackathon.backend.policy.PolicyFixtures.SUN;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.config;
import static com.hackathon.backend.policy.PolicyFixtures.hotel;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static com.hackathon.backend.policy.PolicyFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** POL-008: weekend spend flagged for the manager (CL-02, ASM-001, ASM-S-03). */
class WeekendSpendRuleTest {

	@Test
	void saturdayAndSundayAreFlagged() {
		List<PolicyResult> results = run(input(List.of(meals("l1", "200.00", SAT), cab("l2", "150.00", SUN))),
				"POL-008");

		assertThat(results).hasSize(2).allSatisfy(r -> {
			assertThat(r.resultType()).isEqualTo(ResultType.WEEKEND_FLAG);
			assertThat(r.configKey()).isEqualTo("weekend.flag_for_manager");
		});
	}

	@Test
	void fridayIsNotFlagged() {
		assertThat(run(input(List.of(meals("l1", "200.00", FRI))), "POL-008")).isEmpty();
	}

	@Test
	void hotelUsesCheckInDate() {
		assertThat(run(input(List.of(hotel("h1", "3000.00", "Pune", SAT, 2))), "POL-008")).hasSize(1);
		assertThat(run(input(List.of(hotel("h2", "3000.00", "Pune", FRI, 2))), "POL-008"))
			.as("Friday check-in, stays over the weekend").isEmpty();
	}

	@Test
	void switchedOffByConfig() {
		assertThat(run(input(List.of(meals("l1", "200.00", SAT)), List.of(), config(true, false, true)), "POL-008"))
			.isEmpty();
	}

}
