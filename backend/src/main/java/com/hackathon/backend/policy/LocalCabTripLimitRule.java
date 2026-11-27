package com.hackathon.backend.policy;

import java.util.List;

/** POL-004: Local cab limit per trip. */
public class LocalCabTripLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-004";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-004 not implemented yet");
	}

}
