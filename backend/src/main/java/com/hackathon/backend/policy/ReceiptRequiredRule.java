package com.hackathon.backend.policy;

import java.util.List;

/** POL-005: Receipt mandatory above the threshold; blocks submit (ASM-003). */
public class ReceiptRequiredRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-005";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-005 not implemented yet");
	}

}
