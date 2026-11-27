package com.hackathon.backend.policy;

import java.time.DayOfWeek;
import java.util.List;

/**
 * POL-008: spend on a Saturday or Sunday (the line's expense date, ASM-001; check-in date
 * for hotels, ASM-S-03) is flagged for the manager (CL-02).
 */
public class WeekendSpendRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-008";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		DayOfWeek day = line.expenseDate().getDayOfWeek();
		if (!context.config().weekendFlagForManager() || (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY)) {
			return List.of();
		}
		return RuleSupport.one(line, ruleId(), ResultType.WEEKEND_FLAG, "weekend.flag_for_manager", null,
				line.expenseDate().toString(), "Weekend spend on " + day + " " + line.expenseDate());
	}

}
