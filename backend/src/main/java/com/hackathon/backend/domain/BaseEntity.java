package com.hackathon.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import lombok.Getter;

/**
 * UUIDv7 id assigned in Java (so the same code works on PostgreSQL and the H2 test DB) and a
 * creation time. Implements {@link Persistable} so save() inserts without a prior select.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

	@Id
	private UUID id = Ids.newId();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Transient
	private boolean fresh = true;

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		fresh = false;
	}

}
