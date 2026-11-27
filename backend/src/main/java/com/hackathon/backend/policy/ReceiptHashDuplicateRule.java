package com.hackathon.backend.policy;

import java.util.List;

/**
 * POL-007: a receipt whose file content (SHA-256) matches an earlier line of any employee is
 * flagged, so renaming the file does not help (B-07, SPEC-Q-05).
 */
public class ReceiptHashDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-007";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		if (!context.config().duplicateMatchReceiptHash() || !line.hasReceipt()) {
			return List.of();
		}
		for (RuleSupport.Candidate c : RuleSupport.candidates(context, false)) {
			if (line.receiptHash().equals(c.line().receiptHash())) {
				return RuleSupport.one(line, ruleId(), ResultType.DUPLICATE_FLAG, "duplicate.match_receipt_hash", null,
						c.reference(), "Same receipt file as " + c.reference());
			}
		}
		return List.of();
	}

}
