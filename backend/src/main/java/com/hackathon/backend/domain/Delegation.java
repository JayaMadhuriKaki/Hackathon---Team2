package com.hackathon.backend.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.hackathon.backend.chain.DelegationView;

@Getter
@Setter
@Entity
@Table(name = "delegations")
public class Delegation extends MutableEntity {

	@Column(name = "delegator_id", nullable = false)
	private UUID delegatorId;

	@Column(name = "delegate_id", nullable = false)
	private UUID delegateId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	public DelegationView toView() {
		return new DelegationView(getId().toString(), delegatorId.toString(), delegateId.toString(), startDate,
				endDate, cancelledAt != null);
	}

}
