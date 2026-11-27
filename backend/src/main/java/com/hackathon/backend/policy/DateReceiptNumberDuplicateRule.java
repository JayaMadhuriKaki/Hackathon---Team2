package com.hackathon.backend.policy;

import java.util.List;

/** POL-009: Duplicate on expense date and receipt number across all employees (CL-05). */
public class DateReceiptNumberDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-009";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-009 not implemented yet");
	}

}
