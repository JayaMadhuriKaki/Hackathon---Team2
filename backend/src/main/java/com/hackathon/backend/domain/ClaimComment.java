package com.hackathon.backend.domain;

import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "claim_comments")
public class ClaimComment extends BaseEntity {

	@Column(name = "claim_id", nullable = false)
	private UUID claimId;

	@Column(name = "author_id", nullable = false)
	private UUID authorId;

	@Column(nullable = false, length = 2000)
	private String body;

}
