package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.hackathon.backend.domain.ExpenseCategory;

/**
 * Test data for the policy engine. The config uses the seed values of architecture/SPEC.md
 * §4.1; production code never contains them. Dates: 2026-11-27 is a Friday.
 */
final class PolicyFixtures {

	static final String CLAIMANT = "emp-1";

	static final String OTHER_EMPLOYEE = "emp-2";

	static final LocalDate MON = LocalDate.of(2026, 11, 23);

	static final LocalDate TUE = LocalDate.of(2026, 11, 24);

	static final LocalDate WED = LocalDate.of(2026, 11, 25);

	static final LocalDate FRI = LocalDate.of(2026, 11, 27);

	static final LocalDate SAT = LocalDate.of(2026, 11, 28);

	static final LocalDate SUN = LocalDate.of(2026, 11, 29);

	private PolicyFixtures() {
	}

	static BigDecimal inr(String value) {
		return new BigDecimal(value);
	}

	static PolicyConfigSnapshot config() {
		return config(true, true, true);
	}

	static PolicyConfigSnapshot config(boolean matchReceiptHash, boolean weekendFlag, boolean matchDateReceiptNo) {
		return new PolicyConfigSnapshot(7, inr("1500.00"), inr("6000.00"),
				Set.of("Kolkata", "Mumbai", "Delhi", "Chennai", "Bangalore", "Bengaluru", "New Delhi"), inr("4000.00"),
				inr("2000.00"), inr("500.00"), List.of("vendor", "amount", "expenseDate"), matchReceiptHash, weekendFlag,
				matchDateReceiptNo, inr("25000.00"), inr("100000.00"), inr("0.10"));
	}

	/** A line with a receipt, a unique vendor, receipt number and file hash. */
	static LineInput line(String id, ExpenseCategory category, String amount, LocalDate date) {
		return new LineInput(id, category, inr(amount), date, "Vendor " + id, "RCPT-" + id, "hash-" + id, null, null,
				null);
	}

	static LineInput meals(String id, String amount, LocalDate date) {
		return line(id, ExpenseCategory.MEALS, amount, date);
	}

	static LineInput cab(String id, String amount, LocalDate date) {
		return line(id, ExpenseCategory.LOCAL_CAB, amount, date);
	}

	/** Hotel line; expenseDate is the check-in date (ASM-S-03). */
	static LineInput hotel(String id, String amount, String city, LocalDate checkIn, int nights) {
		return new LineInput(id, ExpenseCategory.HOTEL, inr(amount), checkIn, "Hotel " + id, "RCPT-" + id,
				"hash-" + id, city, checkIn, checkIn.plusDays(nights));
	}

	static LineInput withVendor(LineInput l, String vendor) {
		return new LineInput(l.lineItemId(), l.category(), l.amount(), l.expenseDate(), vendor, l.receiptNumber(),
				l.receiptHash(), l.city(), l.checkInDate(), l.checkOutDate());
	}

	static LineInput withReceiptNumber(LineInput l, String receiptNumber) {
		return new LineInput(l.lineItemId(), l.category(), l.amount(), l.expenseDate(), l.vendor(), receiptNumber,
				l.receiptHash(), l.city(), l.checkInDate(), l.checkOutDate());
	}

	static LineInput withHash(LineInput l, String hash) {
		return new LineInput(l.lineItemId(), l.category(), l.amount(), l.expenseDate(), l.vendor(), l.receiptNumber(),
				hash, l.city(), l.checkInDate(), l.checkOutDate());
	}

	static LineInput withoutReceipt(LineInput l) {
		return withHash(l, null);
	}

	static EarlierLine earlier(String claimantId, String claimNumber, LineInput line) {
		return new EarlierLine("claim-" + claimNumber, claimNumber, claimantId, line);
	}

	static PolicyInput input(List<LineInput> lines) {
		return input(lines, List.of(), config());
	}

	static PolicyInput input(List<LineInput> lines, List<EarlierLine> earlier) {
		return input(lines, earlier, config());
	}

	static PolicyInput input(List<LineInput> lines, List<EarlierLine> earlier, PolicyConfigSnapshot config) {
		return new PolicyInput("claim-new", "CLM-2026-00099", CLAIMANT, lines, earlier, config);
	}

	static List<PolicyResult> run(PolicyInput input, String ruleId) {
		return PolicyEngine.withDocumentedRules().evaluate(input).results().stream()
				.filter(r -> r.ruleId().equals(ruleId)).toList();
	}

}
