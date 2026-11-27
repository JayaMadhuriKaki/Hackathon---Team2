package com.hackathon.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.ClaimRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * SRE gauges (NFR-008, architecture/SPEC.md §10): {@code expenseflow_approval_queue_size} and
 * {@code expenseflow_oldest_pending_approval_seconds}, each with {@code step} = MANAGER,
 * FINANCE_AUDITOR, FINANCE_HEAD or SAMPLE_AUDIT. Every series is always present (0 when empty)
 * and refreshed by a scheduled query.
 */
@Component
public class ApprovalMetrics {

	private static final Logger log = LoggerFactory.getLogger(ApprovalMetrics.class);

	enum Step {
		MANAGER, FINANCE_AUDITOR, FINANCE_HEAD, SAMPLE_AUDIT
	}

	private final Map<Step, AtomicLong> queueSize = new EnumMap<>(Step.class);

	private final Map<Step, AtomicLong> oldestSeconds = new EnumMap<>(Step.class);

	private final ApprovalStepRepository steps;

	private final ClaimRepository claims;

	private final AppClock clock;

	public ApprovalMetrics(MeterRegistry registry, ApprovalStepRepository steps, ClaimRepository claims,
			AppClock clock) {
		this.steps = steps;
		this.claims = claims;
		this.clock = clock;
		for (Step step : Step.values()) {
			AtomicLong size = new AtomicLong();
			AtomicLong age = new AtomicLong();
			queueSize.put(step, size);
			oldestSeconds.put(step, age);
			Gauge.builder("expenseflow_approval_queue_size", size, AtomicLong::get)
				.description("Claims waiting at each approval step")
				.tag("step", step.name())
				.register(registry);
			Gauge.builder("expenseflow_oldest_pending_approval_seconds", age, AtomicLong::get)
				.description("Age of the oldest pending approval at each step")
				.baseUnit("seconds")
				.tag("step", step.name())
				.register(registry);
		}
	}

	@Scheduled(fixedDelayString = "${app.metrics.refresh-ms:30000}", initialDelay = 5_000)
	@Transactional(readOnly = true)
	public void refresh() {
		try {
			Instant now = clock.now();
			for (StepType type : StepType.values()) {
				Step step = Step.valueOf(type.name());
				queueSize.get(step).set(steps.countByStatusAndStepType(StepStatus.PENDING, type));
				oldestSeconds.get(step).set(age(steps.oldestActivation(StepStatus.PENDING, type), now));
			}
			queueSize.get(Step.SAMPLE_AUDIT).set(claims.countByStatus(ClaimStatus.SAMPLE_AUDIT_PENDING));
			oldestSeconds.get(Step.SAMPLE_AUDIT)
				.set(age(claims.oldestSampleSelection(ClaimStatus.SAMPLE_AUDIT_PENDING), now));
		}
		catch (RuntimeException ex) {
			log.warn("Could not refresh approval metrics: {}", ex.getMessage());
		}
	}

	private static long age(Instant since, Instant now) {
		return since == null ? 0 : Math.max(0, Duration.between(since, now).getSeconds());
	}

}
