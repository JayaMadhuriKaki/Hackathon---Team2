package com.hackathon.backend.policy;

/**
 * A line from another claim, loaded by the submit service with the scope of
 * architecture/SPEC.md §4.3 (claims not in DRAFT, AUTO_REJECTED or REJECTED).
 */
public record EarlierLine(String claimId, String claimNumber, String claimantId, LineInput line) {
}
