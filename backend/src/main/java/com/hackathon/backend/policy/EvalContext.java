package com.hackathon.backend.policy;

import java.util.List;

/**
 * What a rule may look at while checking one line: the whole input and the lines of the
 * same claim created before this one. A line never matches itself.
 */
public record EvalContext(PolicyInput input, List<LineInput> priorLinesInClaim) {

	public PolicyConfigSnapshot config() {
		return input.config();
	}

}
