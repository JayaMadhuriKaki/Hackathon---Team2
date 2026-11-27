package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Typed values of every policy configuration key at one config version
 * (architecture/SPEC.md §4.1). Built from the policy_rules table; the engine itself never
 * contains these numbers.
 */
public record PolicyConfigSnapshot(int configVersion, BigDecimal mealsMaxPerDay, BigDecimal hotelMaxPerNightTier1,
		Set<String> hotelTier1Cities, BigDecimal hotelMaxPerNightOther, BigDecimal localCabMaxPerTrip,
		BigDecimal receiptMandatoryAbove, List<String> duplicateMatchFields, boolean duplicateMatchReceiptHash,
		boolean weekendFlagForManager, boolean duplicateMatchDateReceiptNo, BigDecimal approvalAuditorThreshold,
		BigDecimal approvalFinanceHeadThreshold, BigDecimal sampleAuditRate) {
}
