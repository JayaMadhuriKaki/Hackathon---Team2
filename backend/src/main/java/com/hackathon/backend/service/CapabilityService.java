package com.hackathon.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hackathon.backend.chain.DelegationRules;
import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;
import com.hackathon.backend.repository.DelegationRepository;
import com.hackathon.backend.repository.UserRepository;

/** Derived capabilities of architecture/SPEC.md §2, used for every authorization check. */
@Service
public class CapabilityService {

	private final UserRepository users;

	private final DelegationRepository delegations;

	private final AppClock clock;

	public CapabilityService(UserRepository users, DelegationRepository delegations, AppClock clock) {
		this.users = users;
		this.delegations = delegations;
		this.clock = clock;
	}

	public boolean isManager(User user) {
		return users.existsByManagerId(user.getId());
	}

	public boolean isActiveDelegate(User user) {
		return delegations.findByDelegateIdAndCancelledAtIsNull(user.getId())
			.stream()
			.anyMatch(d -> DelegationRules.isActive(d.toView(), clock.todayIst()));
	}

	public boolean isApprover(User user) {
		return isFinance(user) || isManager(user) || isActiveDelegate(user);
	}

	public boolean canDelegate(User user) {
		return isManager(user);
	}

	public boolean isAdmin(User user) {
		return user.getRole() == UserRole.FINANCE_HEAD || user.getRole() == UserRole.ADMIN;
	}

	public boolean isFinance(User user) {
		return user.getRole() == UserRole.FINANCE_AUDITOR || user.getRole() == UserRole.FINANCE_HEAD;
	}

	/** Dashboard access (ASM-010): finance auditors and admins. */
	public boolean canSeeDashboard(User user) {
		return user.getRole() == UserRole.FINANCE_AUDITOR || isAdmin(user);
	}

	public Map<String, Boolean> all(User user) {
		Map<String, Boolean> result = new LinkedHashMap<>();
		boolean manager = isManager(user);
		result.put("isManager", manager);
		result.put("isApprover", manager || isFinance(user) || isActiveDelegate(user));
		result.put("canDelegate", manager);
		result.put("isAdmin", isAdmin(user));
		return result;
	}

}
