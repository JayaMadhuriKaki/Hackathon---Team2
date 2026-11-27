package com.hackathon.backend.chain;

import java.time.LocalDate;

/** A delegation as the rules see it. Dates are inclusive IST calendar dates (ASM-007). */
public record DelegationView(String delegationId, String delegatorId, String delegateId, LocalDate startDate,
		LocalDate endDate, boolean cancelled) {
}
