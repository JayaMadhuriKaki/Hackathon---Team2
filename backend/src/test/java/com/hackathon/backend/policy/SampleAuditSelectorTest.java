package com.hackathon.backend.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** The per-claim draw (FR-011, ASM-S-05): selected when the draw is less than the rate. */
class SampleAuditSelectorTest {

	private static final BigDecimal RATE = new BigDecimal("0.10");

	@Test
	void drawJustBelowTheRateIsSelected() {
		SampleDraw draw = new SampleAuditSelector(() -> 0.0999).draw(RATE);

		assertThat(draw.selected()).isTrue();
		assertThat(draw.value()).isEqualTo(0.0999);
	}

	@Test
	void drawEqualToTheRateIsNotSelected() {
		assertThat(new SampleAuditSelector(() -> 0.10).draw(RATE).selected()).isFalse();
	}

	@Test
	void zeroIsSelectedAndHighDrawIsNot() {
		assertThat(new SampleAuditSelector(() -> 0.0).draw(RATE).selected()).isTrue();
		assertThat(new SampleAuditSelector(() -> 0.95).draw(RATE).selected()).isFalse();
	}

}
