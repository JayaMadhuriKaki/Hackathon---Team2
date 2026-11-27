package com.hackathon.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "claims")
public class Claim extends MutableEntity {

	@Column(name = "claim_number", nullable = false, length = 14)
	private String claimNumber;

	@Column(name = "claimant_id", nullable = false)
	private UUID claimantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 25)
	private ClaimStatus status = ClaimStatus.DRAFT;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount = new BigDecimal("0.00");

	@Column(name = "submission_no", nullable = false)
	private int submissionNo;

	@Column(name = "config_version")
	private Integer configVersion;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "sample_audit_selected")
	private Boolean sampleAuditSelected;

	@Column(name = "sample_audit_selected_at")
	private Instant sampleAuditSelectedAt;

	@Column(name = "payout_batch_id")
	private UUID payoutBatchId;

}
