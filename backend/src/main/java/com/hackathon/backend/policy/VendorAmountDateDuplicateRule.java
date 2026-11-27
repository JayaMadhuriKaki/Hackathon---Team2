package com.hackathon.backend.policy;

import java.util.List;

/** POL-006: Duplicate on vendor, amount and date within the claimant (ASM-S-02). */
public class VendorAmountDateDuplicateRule implements PolicyRule {

	@Override
	public String ruleId() {
		return "POL-006";
	}

	@Override
	public List<PolicyResult> evaluate(LineInput line, EvalContext context) {
		throw new UnsupportedOperationException("POL-006 not implemented yet");
	}

}
