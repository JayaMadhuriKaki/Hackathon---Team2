package com.hackathon.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "claim_line_items")
public class ClaimLineItem extends MutableEntity {

	@Column(name = "claim_id", nullable = false)
	private UUID claimId;

	@Column(name = "line_no", nullable = false)
	private short lineNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExpenseCategory category;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;

	@Column(nullable = false, length = 120)
	private String vendor;

	@Column(name = "receipt_number", nullable = false, length = 60)
	private String receiptNumber;

	@Column(length = 100)
	private String city;

	@Column(name = "check_in_date")
	private LocalDate checkInDate;

	@Column(name = "check_out_date")
	private LocalDate checkOutDate;

}
