package com.hackathon.backend.policy;

import java.util.List;

/** All results of one engine run, plus the config version used. */
public record PolicyEvaluation(int configVersion, List<PolicyResult> results) {

	public boolean has(ResultType type) {
		return results.stream().anyMatch(r -> r.resultType() == type);
	}

	public List<PolicyResult> of(ResultType type) {
		return results.stream().filter(r -> r.resultType() == type).toList();
	}

}
