package com.hackathon.backend.policy;

import java.util.List;

/** POL-002: Hotel per-night limit in tier-1 cities (CL-08, ASM-005, ASM-006). */
public class HotelTier1NightlyLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-002";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-002 not implemented yet");
	}

}
