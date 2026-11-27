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
@Table(name = "payout_batches")
public class PayoutBatch extends MutableEntity {

	@Column(name = "batch_number", nullable = false, length = 12)
	private String batchNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PayoutBatchStatus status = PayoutBatchStatus.CREATED;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "exported_by")
	private UUID exportedBy;

	@Column(name = "exported_at")
	private Instant exportedAt;

	@Column(name = "row_count")
	private Integer rowCount;

	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "file_sha256", length = 64)
	private String fileSha256;

	@Column(name = "file_path", length = 120)
	private String filePath;

}
