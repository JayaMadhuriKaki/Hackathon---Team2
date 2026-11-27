package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.hotel;
import static com.hackathon.backend.policy.PolicyFixtures.inr;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.meals;
import static com.hackathon.backend.policy.PolicyFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * POL-002 (tier-1, 6,000 per night) and POL-003 (other cities, 4,000 per night).
 * CL-08 tier-1 list, ASM-005 nights from check-in/check-out, ASM-006 aliases.
 */
class HotelNightlyLimitRulesTest {

	@Test
	void tier1OneNightAtLimitPasses() {
		PolicyInput in = input(List.of(hotel("h1", "6000.00", "Mumbai", TUE, 1)));
		assertThat(run(in, "POL-002")).isEmpty();
		assertThat(run(in, "POL-003")).isEmpty();
	}

	@Test
	void tier1OneNightOnePaisaOverIsBreach() {
		assertThat(run(input(List.of(hotel("h1", "6000.01", "Mumbai", TUE, 1))), "POL-002")).singleElement()
			.satisfies(r -> {
				assertThat(r.resultType()).isEqualTo(ResultType.LIMIT_BREACH);
				assertThat(r.configKey()).isEqualTo("hotel.max_per_night.tier1");
				assertThat(r.configuredLimit()).isEqualByComparingTo(inr("6000.00"));
				assertThat(r.actualValue()).isEqualTo("6000.01");
			});
	}

	@Test
	void limitScalesWithNights() {
		assertThat(run(input(List.of(hotel("h1", "18000.00", "Delhi", TUE, 3))), "POL-002")).isEmpty();

		assertThat(run(input(List.of(hotel("h1", "18000.01", "Delhi", TUE, 3))), "POL-002")).singleElement()
			.satisfies(r -> {
				assertThat(r.configuredLimit()).isEqualByComparingTo(inr("18000.00"));
				assertThat(r.actualValue()).isEqualTo("18000.01");
				assertThat(r.details()).containsEntry("nights", "3").containsEntry("perNightLimit", "6000.00");
			});
	}

	@ParameterizedTest
	@ValueSource(strings = { " mumbai ", "MUMBAI", "Bengaluru", "bangalore", "NEW DELHI", "kolkata", "Chennai" })
	void tier1CityMatchIsTrimmedCaseInsensitiveWithAliases(String city) {
		PolicyInput in = input(List.of(hotel("h1", "5000.00", city, TUE, 1)));
		assertThat(run(in, "POL-003")).as("5,000 is within tier-1 but above the other-city limit").isEmpty();
		assertThat(run(in, "POL-002")).isEmpty();
	}

	@Test
	void otherCityAtLimitPassesAndOnePaisaOverIsPol003() {
		assertThat(run(input(List.of(hotel("h1", "4000.00", "Pune", TUE, 1))), "POL-003")).isEmpty();

		PolicyInput over = input(List.of(hotel("h1", "4000.01", "Pune", TUE, 1)));
		assertThat(run(over, "POL-002")).isEmpty();
		assertThat(run(over, "POL-003")).singleElement().satisfies(r -> {
			assertThat(r.configKey()).isEqualTo("hotel.max_per_night.other");
			assertThat(r.configuredLimit()).isEqualByComparingTo(inr("4000.00"));
		});
	}

	@Test
	void otherCityTwoNights() {
		assertThat(run(input(List.of(hotel("h1", "8000.01", "Pune", TUE, 2))), "POL-003")).singleElement()
			.satisfies(r -> assertThat(r.configuredLimit()).isEqualByComparingTo(inr("8000.00")));
	}

	@Test
	void nonHotelLinesAreIgnored() {
		PolicyInput in = input(List.of(meals("l1", "9000.00", TUE)));
		assertThat(run(in, "POL-002")).isEmpty();
		assertThat(run(in, "POL-003")).isEmpty();
	}

}
