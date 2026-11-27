package com.hackathon.backend.policy;

/** Claim-level result of the submit pipeline (CLAUDE.md §2.7). */
public enum RoutingOutcome {
	/** T-03: a limit was breached (CL-07). */
	AUTO_REJECT,
	/** T-04: goes to the approval chain. */
	ROUTE,
	/** T-05: auto-approved, then the sample-audit draw. */
	AUTO_APPROVE
}
