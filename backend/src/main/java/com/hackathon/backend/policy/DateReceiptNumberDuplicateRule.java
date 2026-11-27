package com.hackathon.backend.policy;

import java.util.List;

/**
 * POL-009: expense date + receipt number must be unique across all employees (CL-05,
 * SPEC-Q-05). Receipt numbers are compared trimmed and upper-cased (ASM-S-02).
 */
public class DateReceiptNumberDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-009";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		String number = RuleSupport.normaliseReceiptNumber(line.receiptNumber());
		if (!context.config().duplicateMatchDateReceiptNo() || number.isEmpty()) {
			return List.of();
		}
		for (RuleSupport.Candidate c : RuleSupport.candidates(context, false)) {
			if (line.expenseDate().equals(c.line().expenseDate())
					&& number.equals(RuleSupport.normaliseReceiptNumber(c.line().receiptNumber()))) {
				return RuleSupport.one(line, ruleId(), ResultType.DUPLICATE_FLAG, "duplicate.match_date_receipt_no",
						null, c.reference(), "Same date and receipt number as " + c.reference());
			}
		}
		return List.of();
	}

}
