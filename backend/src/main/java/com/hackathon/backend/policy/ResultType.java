package com.hackathon.backend.policy;

/** What a fired rule means for the claim (architecture/SPEC.md §4.2). */
public enum ResultType {
	/** POL-001..004: the whole claim is auto-rejected (CL-07). */
	LIMIT_BREACH,
	/** POL-005: blocks submit, never stored (ASM-003). */
	VALIDATION,
	/** POL-006/007/009: route to the chain, never auto-approve (ASM-004). */
	DUPLICATE_FLAG,
	/** POL-008: route to the chain (CL-02). */
	WEEKEND_FLAG
}
