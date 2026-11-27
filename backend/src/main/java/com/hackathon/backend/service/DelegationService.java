package com.hackathon.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.chain.DelegationProblem;
import com.hackathon.backend.chain.DelegationRules;
import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.Delegation;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.DelegationRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.constraints.NotNull;

/**
 * Delegation while on leave (API-019..021, API-038; FR-008, CL-13, ASM-007, B-03). Only
 * managers delegate; the delegate is a finance auditor or finance head; one active
 * delegation per delegator. Delegation changes who may act, never which steps exist.
 */
@Service
public class DelegationService {

	private final DelegationRepository delegations;

	private final UserRepository users;

	private final CapabilityService capabilities;

	private final ClaimViewAssembler views;

	private final AuditService audit;

	private final AppClock clock;

	public DelegationService(DelegationRepository delegations, UserRepository users, CapabilityService capabilities,
			ClaimViewAssembler views, AuditService audit, AppClock clock) {
		this.delegations = delegations;
		this.users = users;
		this.capabilities = capabilities;
		this.views = views;
		this.audit = audit;
		this.clock = clock;
	}

	/** API-019: delegations the caller gave and received (architecture/SPEC.md §15). */
	@Transactional(readOnly = true)
	public PageResponse<DelegationView> list(User caller, Integer page, Integer pageSize) {
		requireCanDelegate(caller);
		int[] p = PageResponse.check(page, pageSize);
		List<Delegation> all = delegations.findByDelegatorIdOrDelegateIdOrderByStartDateDesc(caller.getId(),
				caller.getId());
		Set<UUID> people = new HashSet<>();
		all.forEach(d -> {
			people.add(d.getDelegatorId());
			people.add(d.getDelegateId());
		});
		Map<UUID, User> userMap = views.userMap(people);
		List<DelegationView> items = all.stream().map(d -> view(d, caller, userMap)).toList();
		return PageResponse.ofList(items, p[0], p[1]);
	}

	/** API-020. */
	@Transactional
	public DelegationView create(User caller, CreateDelegationRequest request) {
		requireCanDelegate(caller);
		User delegate = users.findById(request.delegateId())
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_DELEGATE, "The delegate does not exist"));
		Optional<DelegationProblem> problem = DelegationRules.validateNew(caller.getId().toString(),
				delegate.getId().toString(), delegate.getRole(), request.startDate(), request.endDate(),
				delegations.findByDelegatorId(caller.getId()).stream().map(Delegation::toView).toList());
		if (problem.isPresent()) {
			throw switch (problem.get()) {
				case INVALID_DATES -> new ApiException(ErrorCode.VALIDATION_FAILED, "The end date must not be before the start date",
						Map.of("fields", Map.of("endDate", "must not be before startDate")));
				case INVALID_DELEGATE -> new ApiException(ErrorCode.INVALID_DELEGATE,
						"The delegate must be a finance auditor or finance head other than yourself");
				case OVERLAP -> overlap();
			};
		}
		Delegation d = new Delegation();
		d.setDelegatorId(caller.getId());
		d.setDelegateId(delegate.getId());
		d.setStartDate(request.startDate());
		d.setEndDate(request.endDate());
		try {
			delegations.saveAndFlush(d);
		}
		catch (DataIntegrityViolationException ex) {
			throw overlap();
		}
		audit.event(AuditAction.DELEGATION_CREATED, EntityType.DELEGATION, d.getId())
			.actor(caller.getId())
			.details(Map.of("delegateId", delegate.getId(), "startDate", d.getStartDate().toString(), "endDate",
					d.getEndDate().toString()))
			.save();
		return view(d, caller, Map.of(caller.getId(), caller, delegate.getId(), delegate));
	}

	/** API-021: only the delegator; anyone else gets 404. */
	@Transactional
	public void cancel(User caller, UUID delegationId) {
		Delegation d = delegations.findById(delegationId)
			.filter(x -> x.getDelegatorId().equals(caller.getId()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Delegation not found"));
		if (d.getCancelledAt() != null) {
			throw new ApiException(ErrorCode.STATE_CONFLICT, "The delegation is already cancelled");
		}
		d.setCancelledAt(clock.now());
		delegations.saveAndFlush(d);
		audit.event(AuditAction.DELEGATION_CANCELLED, EntityType.DELEGATION, d.getId()).actor(caller.getId()).save();
	}

	/** API-038: finance auditors and finance heads other than the caller (CL-13). */
	@Transactional(readOnly = true)
	public List<PersonRef> eligibleDelegates(User caller) {
		requireCanDelegate(caller);
		return users.findByRoleInOrderByFullNameAsc(List.of(UserRole.FINANCE_AUDITOR, UserRole.FINANCE_HEAD))
			.stream()
			.filter(u -> !u.getId().equals(caller.getId()))
			.map(ClaimViewAssembler::ref)
			.toList();
	}

	private void requireCanDelegate(User caller) {
		if (!capabilities.canDelegate(caller)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only managers can delegate approvals");
		}
	}

	private static ApiException overlap() {
		return new ApiException(ErrorCode.DELEGATION_OVERLAP, "You already have a delegation in this period");
	}

	private DelegationView view(Delegation d, User caller, Map<UUID, User> userMap) {
		boolean active = DelegationRules.isActive(d.toView(), clock.todayIst());
		return new DelegationView(d.getId(), d.getDelegatorId().equals(caller.getId()) ? "GIVEN" : "RECEIVED",
				ClaimViewAssembler.ref(userMap.get(d.getDelegatorId())),
				ClaimViewAssembler.ref(userMap.get(d.getDelegateId())), d.getStartDate(), d.getEndDate(),
				d.getCancelledAt(), active, d.getCreatedAt());
	}

	public record CreateDelegationRequest(@NotNull UUID delegateId, @NotNull LocalDate startDate,
			@NotNull LocalDate endDate) {
	}

	public record DelegationView(UUID delegationId, String direction, PersonRef delegator, PersonRef delegate,
			LocalDate startDate, LocalDate endDate, Instant cancelledAt, boolean active, Instant createdAt) {
	}

}
