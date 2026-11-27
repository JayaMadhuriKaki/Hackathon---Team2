package com.hackathon.backend.domain;

import java.security.SecureRandom;
import java.util.UUID;

/** UUIDv7 (RFC 9562): time-ordered, non-guessable primary keys (ASM-S-06, ADR-000 P-06). */
public final class Ids {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Ids() {
	}

	public static UUID newId() {
		long millis = System.currentTimeMillis();
		long randA = RANDOM.nextInt(1 << 12);
		long msb = (millis << 16) | (0x7L << 12) | randA;
		long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
		return new UUID(msb, lsb);
	}

}
