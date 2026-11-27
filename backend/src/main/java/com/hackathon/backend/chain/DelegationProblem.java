package com.hackathon.backend.chain;

/** Why a new delegation is refused (backend/SPEC.md §5.3). */
public enum DelegationProblem {
	/** 400 VALIDATION_FAILED. */
	INVALID_DATES,
	/** 422 INVALID_DELEGATE (CL-13, B-03). */
	INVALID_DELEGATE,
	/** 422 DELEGATION_OVERLAP (ASM-007). */
	OVERLAP
}
