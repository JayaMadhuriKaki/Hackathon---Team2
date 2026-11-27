package com.hackathon.backend.policy;

import java.util.List;

/**
 * The routing outcome, the policy results that drove it (every breaching line for an
 * auto-reject) and a readable reason for the claimant and the audit trail.
 */
public record RoutingDecision(RoutingOutcome outcome, List<PolicyResult> drivingResults, String reason) {
}
