package com.hackathon.backend.policy;

import java.util.List;
import java.util.Objects;

/**
 * POL-006: a line with the same values in {@code duplicate.match_fields} (vendor, amount,
 * expenseDate) as an earlier line of the same claimant is flagged (SPEC-Q-05). Vendor is
 * compared trimmed, whitespace-collapsed and case-insensitive; amount exactly (ASM-S-02).
 */
public class VendorAmountDateDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-006";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		List<String> fields = context.config().duplicateMatchFields();
		for (RuleSupport.Candidate c : RuleSupport.candidates(context, true)) {
			if (fields.stream().allMatch(f -> sameField(f, line, c.line()))) {
				return RuleSupport.one(line, ruleId(), ResultType.DUPLICATE_FLAG, "duplicate.match_fields", null,
						c.reference(), "Same vendor, amount and date as " + c.reference());
			}
		}
		return List.of();
	}

	private static boolean sameField(String field, LineInput a, LineInput b) {
		return switch (field) {
			case "vendor" -> RuleSupport.normaliseText(a.vendor()).equals(RuleSupport.normaliseText(b.vendor()));
			case "amount" -> a.amount().compareTo(b.amount()) == 0;
			case "expenseDate" -> Objects.equals(a.expenseDate(), b.expenseDate());
			default -> throw new IllegalArgumentException("Unsupported duplicate.match_fields entry: " + field);
		};
	}

}
