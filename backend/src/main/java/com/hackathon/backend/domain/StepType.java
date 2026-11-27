package com.hackathon.backend.domain;

/** Approval-chain levels (architecture/SPEC.md §5.1). */
public enum StepType {

	MANAGER(1), FINANCE_AUDITOR(2), FINANCE_HEAD(3);

	private final int level;

	StepType(int level) {
		this.level = level;
	}

	public int level() {
		return level;
	}

	public static StepType ofLevel(int level) {
		for (StepType type : values()) {
			if (type.level == level) {
				return type;
			}
		}
		throw new IllegalArgumentException("No step type at level " + level);
	}

	/** Claim state while this step is the PENDING one (backend/SPEC.md §5.1 rule 5). */
	public ClaimStatus pendingStatus() {
		return switch (this) {
			case MANAGER -> ClaimStatus.PENDING_MANAGER;
			case FINANCE_AUDITOR -> ClaimStatus.PENDING_AUDITOR;
			case FINANCE_HEAD -> ClaimStatus.PENDING_FINANCE_HEAD;
		};
	}

}
