package com.hackathon.backend.chain;

import java.math.BigDecimal;

import com.hackathon.backend.domain.UserRole;

/**
 * Facts the chain engine needs at submit (backend/SPEC.md §5.1). Thresholds come from the
 * claim's policy config snapshot; {@code managerId} is null when the claimant has none.
 */
public record ChainInput(String claimantId, UserRole claimantRole, String managerId, BigDecimal total,
		BigDecimal auditorThreshold, BigDecimal financeHeadThreshold, ApproverDirectory directory) {
}
