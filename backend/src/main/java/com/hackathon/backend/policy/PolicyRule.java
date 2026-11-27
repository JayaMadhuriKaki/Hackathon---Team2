package com.hackathon.backend.policy;

import java.util.List;

/** One documented policy rule (POL-###). Returns an empty list when the rule does not fire. */
public interface PolicyRule {

	String ruleId();

	List<PolicyResult> evaluate(LineInput line, EvalContext context);

}
