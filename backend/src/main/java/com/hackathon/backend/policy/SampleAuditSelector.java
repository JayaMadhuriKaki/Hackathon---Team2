package com.hackathon.backend.policy;

import java.math.BigDecimal;
import java.util.function.DoubleSupplier;

/**
 * The 10% sample-audit draw (FR-011, ASM-S-05): each auto-approved claim is selected when a
 * uniform draw in [0, 1) is less than {@code sample_audit.rate}. The random source is
 * injected: SecureRandom in production, a stub in tests (architecture/SPEC.md §5.5).
 */
public class SampleAuditSelector {

	private final DoubleSupplier random;

	public SampleAuditSelector(DoubleSupplier random) {
		this.random = random;
	}

	public SampleDraw draw(BigDecimal rate) {
		double value = random.getAsDouble();
		return new SampleDraw(value, BigDecimal.valueOf(value).compareTo(rate) < 0);
	}

}
