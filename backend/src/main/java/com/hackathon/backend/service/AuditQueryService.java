package com.hackathon.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.AuditEvent;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.AuditEventView;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.AuditEventRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.service.support.PageResponse;

/** API-033: the full audit trail for the finance head / admin (FR-018, ASM-010). */
@Service
public class AuditQueryService {

	private final AuditEventRepository events;

	private final ClaimRepository claims;

	private final CapabilityService capabilities;

	private final ClaimViewAssembler views;

	public AuditQueryService(AuditEventRepository events, ClaimRepository claims, CapabilityService capabilities,
			ClaimViewAssembler views) {
		this.events = events;
		this.claims = claims;
		this.capabilities = capabilities;
		this.views = views;
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditEventView> search(User caller, String claimNumber, UUID actorId, AuditAction action,
			EntityType entityType, LocalDate fromDate, LocalDate toDate, Integer page, Integer pageSize) {
		if (!capabilities.isAdmin(caller)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "The full audit trail is for the finance head or an admin");
		}
		Pageable pageable = PageResponse.pageable(page, pageSize, null, Set.of(), Sort.unsorted());
		UUID claimId = null;
		if (claimNumber != null && !claimNumber.isBlank()) {
			claimId = claims.findByClaimNumber(claimNumber.trim().toUpperCase(java.util.Locale.ROOT))
				.map(Claim::getId)
				.orElse(null);
			if (claimId == null) {
				return new PageResponse<>(java.util.List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0);
			}
		}
		// Concrete bounds: PostgreSQL cannot type a null timestamp parameter in "? is null".
		Instant from = fromDate == null ? Instant.parse("2000-01-01T00:00:00Z")
				: fromDate.atStartOfDay(AppClock.IST).toInstant();
		Instant to = toDate == null ? Instant.parse("2999-01-01T00:00:00Z")
				: toDate.plusDays(1).atStartOfDay(AppClock.IST).toInstant();
		Page<AuditEvent> result = events.search(claimId, actorId, action, entityType, from, to, pageable);
		return new PageResponse<>(views.auditViews(result.getContent()), result.getNumber() + 1, result.getSize(),
				result.getTotalElements());
	}

}
