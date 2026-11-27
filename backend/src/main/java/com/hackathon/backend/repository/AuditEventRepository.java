package com.hackathon.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.AuditEvent;
import com.hackathon.backend.domain.EntityType;

/** Append-only (NFR-004, B-11): save and finders only, no update or delete. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

	AuditEvent save(AuditEvent event);

	Page<AuditEvent> findByClaimIdOrderByIdAsc(UUID claimId, Pageable pageable);

	boolean existsByClaimIdAndActorIdAndActionIn(UUID claimId, UUID actorId, Collection<AuditAction> actions);

	@Query("""
			select e from AuditEvent e where (:claimId is null or e.claimId = :claimId)
			and (:actorId is null or e.actorId = :actorId) and (:action is null or e.action = :action)
			and (:entityType is null or e.entityType = :entityType)
			and e.occurredAt >= :fromTime and e.occurredAt < :toTime
			order by e.id desc""")
	Page<AuditEvent> search(@Param("claimId") UUID claimId, @Param("actorId") UUID actorId,
			@Param("action") AuditAction action, @Param("entityType") EntityType entityType,
			@Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime, Pageable pageable);

}
