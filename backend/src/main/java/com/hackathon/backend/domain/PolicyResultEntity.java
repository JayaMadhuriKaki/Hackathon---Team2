package com.hackathon.backend.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.hackathon.backend.policy.ResultType;

/** A stored policy result (FR-004, FR-016). Never VALIDATION (ASM-003). */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "policy_results")
public class PolicyResultEntity extends BaseEntity {

	@Column(name = "claim_id", nullable = false)
	private UUID claimId;

	@Column(name = "submission_no", nullable = false)
	private int submissionNo;

	@Column(name = "line_item_id")
	private UUID lineItemId;

	@Column(name = "rule_id", nullable = false, length = 7)
	private String ruleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "result_type", nullable = false, length = 15)
	private ResultType resultType;

	@Column(name = "config_key", nullable = false, length = 64)
	private String configKey;

	@Column(name = "configured_limit", precision = 12, scale = 2)
	private BigDecimal configuredLimit;

	@Column(name = "actual_value", length = 200)
	private String actualValue;

	@Column(nullable = false, length = 2000)
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	private String details;

}
