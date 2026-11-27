package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.RuleSupport.money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hackathon.backend.domain.UserRole;

/**
 * Decides the claim-level route from the policy results, in the fixed order of
 * CLAUDE.md §2.7 / architecture/SPEC.md §3.3 step 5:
 * <ol>
 * <li>any limit breach → auto-reject, listing every breaching line (CL-07);</li>
 * <li>total above the auditor threshold (strictly, CL-03), any weekend or duplicate flag
 * (CL-02, ASM-004), or a finance-head claimant (CL-16, SPEC-Q-14) → route;</li>
 * <li>otherwise → auto-approve.</li>
 * </ol>
 * POL-005 validation results must be handled before routing (ASM-003).
 */
public class RoutingDecider {

	public RoutingDecision decide(PolicyEvaluation evaluation, BigDecimal claimTotal, UserRole claimantRole,
			BigDecimal auditorThreshold) {
		if (evaluation.has(ResultType.VALIDATION)) {
			throw new IllegalStateException("Validation results (POL-005) must block submit before routing");
		}

		List<PolicyResult> breaches = evaluation.of(ResultType.LIMIT_BREACH);
		if (!breaches.isEmpty()) {
			String reason = breaches.stream()
				.map(r -> "Line " + r.lineItemId() + ": " + r.ruleId() + " limit INR " + money(r.configuredLimit())
						+ ", actual INR " + r.actualValue() + " - " + r.reason())
				.collect(Collectors.joining("; "));
			return new RoutingDecision(RoutingOutcome.AUTO_REJECT, breaches, reason);
		}

		List<PolicyResult> flags = new ArrayList<>(evaluation.of(ResultType.WEEKEND_FLAG));
		flags.addAll(evaluation.of(ResultType.DUPLICATE_FLAG));
		List<String> why = new ArrayList<>();
		if (claimTotal.compareTo(auditorThreshold) > 0) {
			why.add("total INR " + money(claimTotal) + " is above INR " + money(auditorThreshold));
		}
		if (!flags.isEmpty()) {
			why.add(flags.stream().map(PolicyResult::reason).collect(Collectors.joining("; ")));
		}
		if (claimantRole == UserRole.FINANCE_HEAD) {
			why.add("claims by the finance head are always approved by a finance auditor");
		}
		if (!why.isEmpty()) {
			return new RoutingDecision(RoutingOutcome.ROUTE, List.copyOf(flags),
					"Routed for approval: " + String.join("; ", why));
		}
		return new RoutingDecision(RoutingOutcome.AUTO_APPROVE, List.of(), "Passed every policy check");
	}

}
