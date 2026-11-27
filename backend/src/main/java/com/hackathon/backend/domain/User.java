package com.hackathon.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A synthetic employee (DATA-001/002/005). Read-only for the app (no user management, Q-20). */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

	@Column(name = "employee_code", nullable = false, length = 10)
	private String employeeCode;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role = UserRole.EMPLOYEE;

	@Column(name = "manager_id")
	private UUID managerId;

	@Column(name = "department_id", nullable = false)
	private UUID departmentId;

	@Column(name = "bank_account_number", nullable = false, length = 18)
	private String bankAccountNumber;

	@Column(name = "ifsc_code", nullable = false, length = 11)
	private String ifscCode;

}
