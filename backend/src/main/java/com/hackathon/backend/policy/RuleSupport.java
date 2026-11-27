package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared helpers for the rule classes: formatting, normalisation and match candidates. */
final class RuleSupport {

	private RuleSupport() {
	}

	/** A line another line can be compared with, and the claim it belongs to. */
	record Candidate(String claimNumber, LineInput line) {

		String reference() {
			return claimNumber + " line " + line.lineItemId();
		}

	}

	static String money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	/** Trimmed, inner whitespace collapsed, lower-case (vendor and city matching, ASM-S-02, ASM-006). */
	static String normaliseText(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** Trimmed, upper-case (receipt number matching, ASM-S-02). */
	static String normaliseReceiptNumber(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	/**
	 * Earlier lines to compare against (architecture/SPEC.md §4.3): lines of other claims in
	 * scope plus lines created earlier in this claim. {@code claimantOnly} keeps only the
	 * claimant's own lines.
	 */
	static List<Candidate> candidates(EvalContext context, boolean claimantOnly) {
		PolicyInput input = context.input();
		List<Candidate> result = new ArrayList<>();
		for (EarlierLine earlier : input.earlierLines()) {
			if (!claimantOnly || earlier.claimantId().equals(input.claimantId())) {
				result.add(new Candidate(earlier.claimNumber(), earlier.line()));
			}
		}
		for (LineInput prior : context.priorLinesInClaim()) {
			result.add(new Candidate(input.claimNumber(), prior));
		}
		return result;
	}

	static List<PolicyResult> one(LineInput line, String ruleId, ResultType type, String configKey,
			BigDecimal configuredLimit, String actualValue, String reason) {
		return one(line, ruleId, type, configKey, configuredLimit, actualValue, reason, Map.of());
	}

	static List<PolicyResult> one(LineInput line, String ruleId, ResultType type, String configKey,
			BigDecimal configuredLimit, String actualValue, String reason, Map<String, String> details) {
		return List.of(new PolicyResult(line.lineItemId(), ruleId, type, configKey, configuredLimit, actualValue,
				reason, details));
	}

}
