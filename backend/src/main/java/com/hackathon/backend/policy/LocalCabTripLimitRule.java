package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.RuleSupport.money;

import java.math.BigDecimal;
import java.util.List;

import com.hackathon.backend.domain.ExpenseCategory;

/** POL-004: local cab up to {@code local_cab.max_per_trip}; one line is one trip. */
public class LocalCabTripLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-004";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		BigDecimal limit = context.config().localCabMaxPerTrip();
		if (line.category() != ExpenseCategory.LOCAL_CAB || line.amount().compareTo(limit) <= 0) {
			return List.of();
		}
		return RuleSupport.one(line, ruleId(), ResultType.LIMIT_BREACH, "local_cab.max_per_trip", limit,
				money(line.amount()),
				"Local cab trip of INR " + money(line.amount()) + " is above the limit of INR " + money(limit));
	}

}
