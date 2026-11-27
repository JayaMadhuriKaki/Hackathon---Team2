package com.hackathon.backend.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;

/**
 * In-memory fixed one-minute window per key (backend/SPEC.md §7.8): login per client IP,
 * receipt uploads per user. Exceeding the limit gives 429 RATE_LIMITED.
 */
@Component
public class RateLimiter {

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private final AppClock clock;

	public RateLimiter(AppClock clock) {
		this.clock = clock;
	}

	public void check(String key, int perMinute) {
		long minute = clock.now().getEpochSecond() / 60;
		Window w = windows.compute(key, (k, old) -> (old == null || old.minute != minute) ? new Window(minute, 1)
				: new Window(minute, old.count + 1));
		if (w.count > perMinute) {
			throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Wait a minute and try again.");
		}
		if (windows.size() > 10_000) {
			windows.entrySet().removeIf(e -> e.getValue().minute != minute);
		}
	}

	private record Window(long minute, int count) {
	}

	Instant now() {
		return clock.now();
	}

}
