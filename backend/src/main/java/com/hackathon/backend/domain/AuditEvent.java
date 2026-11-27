package com.hackathon.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Append-only audit row (FR-018, NFR-004). Immutable: Hibernate never issues an UPDATE. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor_id")
	private UUID actorId;

	@Column(name = "on_behalf_of_id")
	private UUID onBehalfOfId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 20)
	private EntityType entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@Column(name = "claim_id")
	private UUID claimId;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_state", length = 25)
	private ClaimStatus fromState;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_state", length = 25)
	private ClaimStatus toState;

	@Column(length = 2000)
	private String comment;

	@JdbcTypeCode(SqlTypes.JSON)
	private String details;

	@Column(name = "trace_id", length = 64)
	private String traceId;

}
