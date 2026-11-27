package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.RuleSupport.money;

import java.math.BigDecimal;
import java.util.List;

/**
 * POL-005: a receipt is mandatory above {@code receipt.mandatory_above}. The result is a
 * VALIDATION result that blocks submit; it is never stored (ASM-003).
 */
public class ReceiptRequiredRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-005";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		BigDecimal threshold = context.config().receiptMandatoryAbove();
		if (line.hasReceipt() || line.amount().compareTo(threshold) <= 0) {
			return List.of();
		}
		return RuleSupport.one(line, ruleId(), ResultType.VALIDATION, "receipt.mandatory_above", threshold,
				money(line.amount()), "A receipt is required for amounts above INR " + money(threshold));
	}

}
