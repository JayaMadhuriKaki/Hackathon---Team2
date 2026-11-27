package com.hackathon.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.ExpenseCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for claims and line items (API-004..013, architecture/SPEC.md §4.4, §7.1). */
public final class ClaimDtos {

	private ClaimDtos() {
	}

	/** API-007 / API-008 body. Hotel lines also need city and stay dates (CL-08, ASM-005). */
	public record LineItemRequest(@NotNull ExpenseCategory category,
			@NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10,
					fraction = 2, message = "must have at most 2 decimals") BigDecimal amount,
			@NotNull LocalDate expenseDate, @NotBlank @Size(max = 120) String vendor,
			@NotBlank @Size(max = 60) String receiptNumber, @Size(max = 100) String city, LocalDate checkInDate,
			LocalDate checkOutDate) {
	}

	public record PersonRef(UUID userId, String name, String employeeCode) {
	}

	public record ReceiptView(UUID receiptId, String contentType, long sizeBytes, Instant uploadedAt) {
	}

	public record PolicyResultView(String ruleId, String resultType, String configKey, String configuredLimit,
			String actualValue, String reason, Map<String, Object> details) {
	}

	public record LineItemView(UUID lineItemId, int lineNo, ExpenseCategory category, String amount,
			LocalDate expenseDate, String vendor, String receiptNumber, String city, LocalDate checkInDate,
			LocalDate checkOutDate, Integer nights, ReceiptView receipt, List<PolicyResultView> policyResults) {
	}

	public record StepView(UUID stepId, int submissionNo, int level, String stepType, String status,
			PersonRef assignedApprover, String skipReason, PersonRef actedBy, PersonRef onBehalfOf,
			Instant activatedAt, Instant actedAt) {
	}

	public record CommentView(UUID commentId, PersonRef author, String body, Instant createdAt) {
	}

	public record ClaimSummary(UUID claimId, String claimNumber, ClaimStatus status, String total,
			Instant createdAt, Instant submittedAt, Instant decidedAt, int lineCount) {
	}

	public record ClaimDetail(UUID claimId, String claimNumber, ClaimStatus status, String total, PersonRef claimant,
			String department, Instant createdAt, Instant updatedAt, Instant submittedAt, Instant decidedAt,
			int submissionNo, Integer configVersion, String decisionReason, String latestSendBackComment,
			List<LineItemView> lineItems, List<StepView> steps, List<CommentView> comments,
			List<String> allowedActions) {
	}

	public record AuditEventView(long eventId, Instant occurredAt, String action, PersonRef actor,
			PersonRef onBehalfOf, String entityType, UUID entityId, UUID claimId, String claimNumber,
			String fromState, String toState, String comment, Object details) {
	}

	/** API-015 body; the comment is optional on approve. */
	public record ApproveRequest(@Size(max = 2000) String comment) {
	}

	/** API-016 / API-017 body: the comment is mandatory (CL-04, SPEC-Q-03), checked by the service. */
	public record DecisionRequest(@Size(max = 2000) String comment) {
	}

	/** API-018 body. */
	public record CommentRequest(@NotBlank @Size(max = 2000) String body) {
	}

}
