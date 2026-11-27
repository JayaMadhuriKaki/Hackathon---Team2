package com.hackathon.backend.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money in JSON is a string with exactly two decimals, e.g. "27450.00" (architecture/SPEC.md §8). */
public final class Money {

	public static final BigDecimal ZERO = new BigDecimal("0.00");

	private Money() {
	}

	public static String format(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	public static BigDecimal scale(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
