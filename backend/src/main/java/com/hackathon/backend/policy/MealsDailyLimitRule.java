package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.RuleSupport.money;

import java.math.BigDecimal;
import java.util.List;

import com.hackathon.backend.domain.ExpenseCategory;

/**
 * POL-001: meals up to {@code meals.max_per_day} per day, summed across the claimant's lines
 * for that date in this claim and in-scope earlier claims (SPEC-Q-04). The line that pushes
 * the day over the limit is reported.
 */
public class MealsDailyLimitRule implements PolicyRule {

	static final String KEY = "meals.max_per_day";

	@Override
	public String ruleId() {
		return "POL-001";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		if (line.category() != ExpenseCategory.MEALS) {
			return List.of();
		}
		BigDecimal dayTotal = line.amount();
		for (RuleSupport.Candidate c : RuleSupport.candidates(context, true)) {
			if (c.line().category() == ExpenseCategory.MEALS && c.line().expenseDate().equals(line.expenseDate())) {
				dayTotal = dayTotal.add(c.line().amount());
			}
		}
		BigDecimal limit = context.config().mealsMaxPerDay();
		if (dayTotal.compareTo(limit) <= 0) {
			return List.of();
		}
		return RuleSupport.one(line, ruleId(), ResultType.LIMIT_BREACH, KEY, limit, money(dayTotal),
				"Meals on " + line.expenseDate() + " total INR " + money(dayTotal) + ", above the daily limit of INR "
						+ money(limit));
	}

}
