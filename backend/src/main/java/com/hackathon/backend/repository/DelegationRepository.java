package com.hackathon.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.Delegation;

public interface DelegationRepository extends JpaRepository<Delegation, UUID> {

	List<Delegation> findByDelegatorId(UUID delegatorId);

	List<Delegation> findByDelegateIdAndCancelledAtIsNull(UUID delegateId);

	List<Delegation> findByDelegatorIdOrDelegateIdOrderByStartDateDesc(UUID delegatorId, UUID delegateId);

}
