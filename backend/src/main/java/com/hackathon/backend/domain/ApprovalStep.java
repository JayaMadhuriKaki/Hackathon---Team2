package com.hackathon.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.hackathon.backend.chain.SkipReason;

@Getter
@Setter
@Entity
@Table(name = "approval_steps")
public class ApprovalStep extends MutableEntity {

	@Column(name = "claim_id", nullable = false)
	private UUID claimId;

	@Column(name = "submission_no", nullable = false)
	private int submissionNo;

	@Column(name = "step_order", nullable = false)
	private short stepOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "step_type", nullable = false, length = 20)
	private StepType stepType;

	@Column(name = "assigned_approver_id")
	private UUID assignedApproverId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private StepStatus status = StepStatus.WAITING;

	@Column(name = "activated_at")
	private Instant activatedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "skip_reason", length = 30)
	private SkipReason skipReason;

	@Column(name = "acted_by_id")
	private UUID actedById;

	@Column(name = "on_behalf_of_id")
	private UUID onBehalfOfId;

	@Column(name = "acted_at")
	private Instant actedAt;

}
