package com.hackathon.backend.policy;

import java.util.List;

/** POL-007: Duplicate receipt file content across all employees. */
public class ReceiptHashDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-007";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-007 not implemented yet");
	}

}
