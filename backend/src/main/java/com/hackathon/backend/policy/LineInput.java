package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.hackathon.backend.domain.ExpenseCategory;

/**
 * One claim line as the policy engine sees it (backend/SPEC.md §4.1). {@code receiptHash}
 * is the SHA-256 of the uploaded file, or null when there is no receipt. Hotel fields are
 * null for other categories.
 */
public record LineInput(String lineItemId, ExpenseCategory category, BigDecimal amount, LocalDate expenseDate,
		String vendor, String receiptNumber, String receiptHash, String city, LocalDate checkInDate,
		LocalDate checkOutDate) {

	public boolean hasReceipt() {
		return receiptHash != null;
	}

}
