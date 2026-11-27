package com.hackathon.backend.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every rule on every line and returns all results (CL-07 lists every breaching line).
 * Pure: no Spring, no database, no clock (CLAUDE.md §9.2).
 */
public class PolicyEngine {

	private final List<PolicyRule> rules;

	public PolicyEngine(List<PolicyRule> rules) {
		this.rules = List.copyOf(rules);
	}

	/** The nine documented rules, POL-001..POL-009. */
	public static PolicyEngine withDocumentedRules() {
		return new PolicyEngine(List.of(new MealsDailyLimitRule(), new HotelTier1NightlyLimitRule(),
				new HotelOtherNightlyLimitRule(), new LocalCabTripLimitRule(), new ReceiptRequiredRule(),
				new VendorAmountDateDuplicateRule(), new ReceiptHashDuplicateRule(), new WeekendSpendRule(),
				new DateReceiptNumberDuplicateRule()));
	}

	public PolicyEvaluation evaluate(PolicyInput input) {
		List<PolicyResult> results = new ArrayList<>();
		List<LineInput> lines = input.lines();
		for (int i = 0; i < lines.size(); i++) {
			EvalContext context = new EvalContext(input, List.copyOf(lines.subList(0, i)));
			for (PolicyRule rule : rules) {
				results.addAll(rule.evaluate(lines.get(i), context));
			}
		}
		return new PolicyEvaluation(input.config().configVersion(), List.copyOf(results));
	}

}
