package com.hackathon.backend.policy;

import java.util.List;

/** POL-003: Hotel per-night limit in all other cities (ASM-005). */
public class HotelOtherNightlyLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-003";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-003 not implemented yet");
	}

}
