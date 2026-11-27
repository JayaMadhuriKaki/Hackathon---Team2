package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.PolicyFixtures.CLAIMANT;
import static com.hackathon.backend.policy.PolicyFixtures.MON;
import static com.hackathon.backend.policy.PolicyFixtures.OTHER_EMPLOYEE;
import static com.hackathon.backend.policy.PolicyFixtures.TUE;
import static com.hackathon.backend.policy.PolicyFixtures.cab;
import static com.hackathon.backend.policy.PolicyFixtures.config;
import static com.hackathon.backend.policy.PolicyFixtures.earlier;
import static com.hackathon.backend.policy.PolicyFixtures.input;
import static com.hackathon.backend.policy.PolicyFixtures.run;
import static com.hackathon.backend.policy.PolicyFixtures.withHash;
import static com.hackathon.backend.policy.PolicyFixtures.withReceiptNumber;
import static com.hackathon.backend.policy.PolicyFixtures.withVendor;
import static com.hackathon.backend.policy.PolicyFixtures.withoutReceipt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * POL-006, POL-007 and POL-009 duplicate flags. Scope answered in SPEC-Q-05: receipt hash
 * and date + receipt number across all employees; vendor + amount + date within the claimant.
 */
class DuplicateRulesTest {

	@Nested
	class VendorAmountDate {

		@Test
		void claimantsEarlierLineWithNormalisedVendorIsFlagged() {
			LineInput now = withVendor(cab("l1", "450.00", TUE), "acme cabs");
			LineInput before = withVendor(cab("e1", "450.00", TUE), "  ACME   Cabs ");
			PolicyInput in = input(List.of(now), List.of(earlier(CLAIMANT, "CLM-2026-00001", before)));

			assertThat(run(in, "POL-006")).singleElement().satisfies(r -> {
				assertThat(r.resultType()).isEqualTo(ResultType.DUPLICATE_FLAG);
				assertThat(r.configKey()).isEqualTo("duplicate.match_fields");
				assertThat(r.actualValue()).contains("CLM-2026-00001");
			});
		}

		@Test
		void anotherEmployeesMatchingLineIsNotFlagged() {
			LineInput line = withVendor(cab("l1", "450.00", TUE), "Acme Cabs");
			PolicyInput in = input(List.of(line),
					List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withVendor(cab("e1", "450.00", TUE), "Acme Cabs"))));

			assertThat(run(in, "POL-006")).isEmpty();
		}

		@Test
		void differentAmountOrDateIsNotFlagged() {
			LineInput line = withVendor(cab("l1", "450.00", TUE), "Acme Cabs");
			PolicyInput in = input(List.of(line),
					List.of(earlier(CLAIMANT, "CLM-2026-00001", withVendor(cab("e1", "450.01", TUE), "Acme Cabs")),
							earlier(CLAIMANT, "CLM-2026-00003", withVendor(cab("e2", "450.00", MON), "Acme Cabs"))));

			assertThat(run(in, "POL-006")).isEmpty();
		}

		@Test
		void secondIdenticalLineInSameClaimIsFlaggedButNotTheFirst() {
			LineInput first = withVendor(cab("l1", "300.00", TUE), "Acme Cabs");
			LineInput second = withVendor(cab("l2", "300.00", TUE), "Acme Cabs");

			assertThat(run(input(List.of(first, second)), "POL-006")).singleElement()
				.satisfies(r -> assertThat(r.lineItemId()).isEqualTo("l2"));
		}

	}

	@Nested
	class ReceiptHash {

		@Test
		void sameFileContentFromAnotherEmployeeIsFlagged() {
			LineInput line = withHash(cab("l1", "900.00", TUE), "abc123");
			PolicyInput in = input(List.of(line),
					List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withHash(cab("e1", "120.00", MON), "abc123"))));

			assertThat(run(in, "POL-007")).singleElement().satisfies(r -> {
				assertThat(r.resultType()).isEqualTo(ResultType.DUPLICATE_FLAG);
				assertThat(r.configKey()).isEqualTo("duplicate.match_receipt_hash");
				assertThat(r.actualValue()).contains("CLM-2026-00002");
			});
		}

		@Test
		void differentHashIsNotFlagged() {
			PolicyInput in = input(List.of(withHash(cab("l1", "900.00", TUE), "abc123")),
					List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withHash(cab("e1", "900.00", TUE), "def456"))));

			assertThat(run(in, "POL-007")).isEmpty();
		}

		@Test
		void lineWithoutReceiptIsNotChecked() {
			PolicyInput in = input(List.of(withoutReceipt(cab("l1", "300.00", TUE))),
					List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withoutReceipt(cab("e1", "300.00", TUE)))));

			assertThat(run(in, "POL-007")).isEmpty();
		}

		@Test
		void switchedOffByConfig() {
			PolicyInput in = input(List.of(withHash(cab("l1", "900.00", TUE), "abc123")),
					List.of(earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withHash(cab("e1", "900.00", TUE), "abc123"))),
					config(false, true, true));

			assertThat(run(in, "POL-007")).isEmpty();
		}

		@Test
		void sameFileTwiceInOneClaimFlagsTheSecondLine() {
			PolicyInput in = input(
					List.of(withHash(cab("l1", "300.00", TUE), "same"), withHash(cab("l2", "400.00", MON), "same")));

			assertThat(run(in, "POL-007")).singleElement()
				.satisfies(r -> assertThat(r.lineItemId()).isEqualTo("l2"));
		}

	}

	@Nested
	class DateAndReceiptNumber {

		@Test
		void sameDateAndNormalisedReceiptNumberFromAnyEmployeeIsFlagged() {
			LineInput line = withReceiptNumber(cab("l1", "700.00", TUE), "r-1");
			PolicyInput in = input(List.of(line), List.of(
					earlier(OTHER_EMPLOYEE, "CLM-2026-00002", withReceiptNumber(cab("e1", "150.00", TUE), " R-1 "))));

			assertThat(run(in, "POL-009")).singleElement().satisfies(r -> {
				assertThat(r.resultType()).isEqualTo(ResultType.DUPLICATE_FLAG);
				assertThat(r.configKey()).isEqualTo("duplicate.match_date_receipt_no");
				assertThat(r.actualValue()).contains("CLM-2026-00002");
			});
		}

		@Test
		void sameReceiptNumberOnAnotherDateIsNotFlagged() {
			PolicyInput in = input(List.of(withReceiptNumber(cab("l1", "700.00", TUE), "R-1")),
					List.of(earlier(CLAIMANT, "CLM-2026-00001", withReceiptNumber(cab("e1", "700.00", MON), "R-1"))));

			assertThat(run(in, "POL-009")).isEmpty();
		}

		@Test
		void switchedOffByConfig() {
			PolicyInput in = input(List.of(withReceiptNumber(cab("l1", "700.00", TUE), "R-1")),
					List.of(earlier(CLAIMANT, "CLM-2026-00001", withReceiptNumber(cab("e1", "700.00", TUE), "R-1"))),
					config(true, true, false));

			assertThat(run(in, "POL-009")).isEmpty();
		}

	}

}
