package com.hackathon.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Receipt metadata; the file lives in the receipts volume under {@code storageName}. */
@Getter
@Setter
@Entity
@Table(name = "receipts")
public class Receipt extends BaseEntity {

	@Column(name = "line_item_id", nullable = false)
	private UUID lineItemId;

	@Column(name = "storage_name", nullable = false, length = 64)
	private String storageName;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false, length = 64)
	private String sha256;

}
