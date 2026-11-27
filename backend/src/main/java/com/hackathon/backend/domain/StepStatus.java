package com.hackathon.backend.domain;

/** Approval-step status (architecture/SPEC.md §5.3). */
public enum StepStatus {
	WAITING, PENDING, APPROVED, REJECTED, SENT_BACK, SKIPPED, SUPERSEDED
}
