package com.hackathon.backend.chain;

/** Why a chain step was skipped (architecture/SPEC.md §5.3). */
public enum SkipReason {
	/** Nobody can approve this level, e.g. the claimant has no manager (CL-09). */
	NO_APPROVER,
	/** The claimant would approve their own claim at this level (ASM-009, B-02). */
	CLAIMANT_IS_APPROVER
}
