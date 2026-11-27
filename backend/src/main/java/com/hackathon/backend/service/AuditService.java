package com.hackathon.backend.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.AuditEvent;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.exception.TraceIdFilter;
import com.hackathon.backend.repository.AuditEventRepository;
import com.hackathon.backend.service.support.Json;

/**
 * Writes append-only audit rows (FR-018, NFR-004). Called by the service that performs the
 * action, inside the same transaction (CLAUDE.md §11.9). There is no update or delete path.
 */
@Service
public class AuditService {

	private final AuditEventRepository repository;

	private final AppClock clock;

	private final Json json;

	public AuditService(AuditEventRepository repository, AppClock clock, Json json) {
		this.repository = repository;
		this.clock = clock;
		this.json = json;
	}

	public Entry event(AuditAction action, EntityType entityType, UUID entityId) {
		return new Entry(action, entityType, entityId);
	}

	/** Shortcut for a claim-level event. */
	public Entry claim(AuditAction action, UUID claimId) {
		return new Entry(action, EntityType.CLAIM, claimId).claim(claimId);
	}

	/** Fluent builder; {@link #save()} inserts the row. */
	public final class Entry {

		private final AuditEvent event = new AuditEvent();

		private Entry(AuditAction action, EntityType entityType, UUID entityId) {
			event.setAction(action);
			event.setEntityType(entityType);
			event.setEntityId(entityId);
		}

		public Entry claim(UUID claimId) {
			event.setClaimId(claimId);
			return this;
		}

		public Entry actor(UUID actorId) {
			event.setActorId(actorId);
			return this;
		}

		public Entry onBehalfOf(UUID onBehalfOfId) {
			event.setOnBehalfOfId(onBehalfOfId);
			return this;
		}

		public Entry states(ClaimStatus from, ClaimStatus to) {
			event.setFromState(from);
			event.setToState(to);
			return this;
		}

		public Entry comment(String comment) {
			event.setComment(comment);
			return this;
		}

		public Entry details(Map<String, ?> details) {
			event.setDetails(details == null || details.isEmpty() ? null : json.write(details));
			return this;
		}

		public AuditEvent save() {
			event.setOccurredAt(clock.now());
			event.setTraceId(TraceIdFilter.current());
			return repository.save(event);
		}

	}

}
