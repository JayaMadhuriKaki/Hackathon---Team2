package com.hackathon.backend.policy;

import java.util.List;

/** POL-001: Meals up to the daily limit, summed per claimant per day (SPEC-Q-04). */
public class MealsDailyLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-001";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-001 not implemented yet");
	}

}
