package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One fired rule on one line (CLAUDE.md §2.2): the rule, the configured limit, the actual
 * value and a readable reason. For duplicates {@code actualValue} names the matching claim
 * and line. {@code details} carries extra facts such as hotel nights.
 */
public record PolicyResult(String lineItemId, String ruleId, ResultType resultType, String configKey,
		BigDecimal configuredLimit, String actualValue, String reason, Map<String, String> details) {
}
