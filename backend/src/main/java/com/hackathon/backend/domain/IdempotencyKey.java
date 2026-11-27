package com.hackathon.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends MutableEntity {

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false, length = 7)
	private String operation;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "response_status")
	private Short responseStatus;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_body")
	private String responseBody;

}
