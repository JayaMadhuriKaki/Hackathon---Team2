package com.hackathon.backend.policy;

import java.util.List;

/** POL-003: hotel per-night limit in all other cities (ASM-005). */
public class HotelOtherNightlyLimitRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-003";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		return HotelNightlyLimitSupport.evaluate(line, context, false, ruleId(), "hotel.max_per_night.other");
	}

}
