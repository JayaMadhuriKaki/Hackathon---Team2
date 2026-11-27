package com.hackathon.backend.domain;

import java.util.Set;

/**
 * Claim states (architecture/SPEC.md §3.1). SUBMITTED and AUTO_APPROVED are transient:
 * they appear in the audit trail only, never on the claims row.
 */
public enum ClaimStatus {

	DRAFT, SUBMITTED, AUTO_APPROVED, AUTO_REJECTED, PENDING_MANAGER, PENDING_AUDITOR, PENDING_FINANCE_HEAD, SENT_BACK,
	REJECTED, SAMPLE_AUDIT_PENDING, APPROVED, IN_PAYOUT_BATCH, PAID;

	public static final Set<ClaimStatus> PENDING_CHAIN = Set.of(PENDING_MANAGER, PENDING_AUDITOR,
			PENDING_FINANCE_HEAD);

	/** States whose lines are not "earlier lines" for duplicate and meal checks (§4.3). */
	public static final Set<ClaimStatus> NOT_COUNTED_FOR_POLICY = Set.of(DRAFT, AUTO_REJECTED, REJECTED);

	public static final Set<ClaimStatus> EDITABLE = Set.of(DRAFT, SENT_BACK);

	public boolean isPendingChain() {
		return PENDING_CHAIN.contains(this);
	}

}
