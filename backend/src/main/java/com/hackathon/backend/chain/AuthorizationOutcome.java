package com.hackathon.backend.chain;

/** Result kinds of a current-step check (backend/SPEC.md §5.2). */
public enum AuthorizationOutcome {
	ALLOWED,
	/** 422 SELF_APPROVAL_FORBIDDEN: always checked first (B-02). */
	SELF_APPROVAL,
	/** 403 NOT_CURRENT_APPROVER. */
	NOT_CURRENT_APPROVER
}
