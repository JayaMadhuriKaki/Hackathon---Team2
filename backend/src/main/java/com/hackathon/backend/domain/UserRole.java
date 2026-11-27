package com.hackathon.backend.domain;

/** One role per user (architecture/SPEC.md §2). Line manager is derived, not a role. */
public enum UserRole {
	EMPLOYEE, FINANCE_AUDITOR, FINANCE_HEAD, ADMIN
}
