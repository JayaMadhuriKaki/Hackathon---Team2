package com.hackathon.backend.policy;

import java.util.List;

/** POL-008: Weekend spend flagged for the manager (CL-02, ASM-001, ASM-S-03). */
public class WeekendSpendRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-008";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-008 not implemented yet");
	}

}
