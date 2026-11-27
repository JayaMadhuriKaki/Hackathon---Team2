package com.hackathon.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One insert-only policy configuration row (CL-10, B-08). {@code value} is JSON text. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "policy_rules")
public class PolicyRule extends BaseEntity {

	@Column(name = "rule_key", nullable = false, length = 64)
	private String ruleKey;

	@Column(name = "config_version", nullable = false)
	private int configVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String value;

	@Column(name = "created_by")
	private UUID createdBy;

}
