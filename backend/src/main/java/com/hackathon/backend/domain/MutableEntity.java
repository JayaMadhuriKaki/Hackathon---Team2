package com.hackathon.backend.domain;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** Entities the application updates carry {@code updated_at} (database/SPEC.md §1). */
@Getter
@MappedSuperclass
public abstract class MutableEntity extends BaseEntity {

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

}
