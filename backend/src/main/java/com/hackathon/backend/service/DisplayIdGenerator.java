package com.hackathon.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.DisplayCounter;
import com.hackathon.backend.repository.DisplayCounterRepository;

/**
 * Display numbers per year (architecture/SPEC.md §6): {@code CLM-YYYY-NNNNN} and
 * {@code PB-YYYY-NNNN}. The counter row is locked, so numbers are unique and gap-free
 * within the calling transaction.
 */
@Service
public class DisplayIdGenerator {

	private final DisplayCounterRepository counters;

	private final AppClock clock;

	public DisplayIdGenerator(DisplayCounterRepository counters, AppClock clock) {
		this.counters = counters;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public synchronized String nextClaimNumber() {
		return next("CLM", 5);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public synchronized String nextBatchNumber() {
		return next("PB", 4);
	}

	private String next(String prefix, int width) {
		int year = clock.todayIst().getYear();
		String key = prefix + "-" + year;
		DisplayCounter counter = counters.findForUpdate(key).orElseGet(() -> counters.saveAndFlush(new DisplayCounter(key)));
		counter.setLastValue(counter.getLastValue() + 1);
		return String.format("%s-%d-%0" + width + "d", prefix, year, counter.getLastValue());
	}

}
