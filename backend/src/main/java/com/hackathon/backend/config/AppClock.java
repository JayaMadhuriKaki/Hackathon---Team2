package com.hackathon.backend.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * The single source of "now" (UTC timestamps) and "today" (IST calendar date, ASM-002), so
 * tests can pin time. Delegation windows and "not in the future" use the IST date.
 */
@Component
public class AppClock {

	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private volatile Clock clock = Clock.systemUTC();

	public Instant now() {
		return clock.instant();
	}

	public LocalDate todayIst() {
		return LocalDate.now(clock.withZone(IST));
	}

	/** Test hook: pin or move the clock. */
	public void setClock(Clock clock) {
		this.clock = clock;
	}

}
