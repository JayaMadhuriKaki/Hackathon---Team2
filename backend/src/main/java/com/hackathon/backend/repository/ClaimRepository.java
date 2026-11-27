package com.hackathon.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimStatus;

import jakarta.persistence.LockModeType;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

	/** Locks the claim row before any state check (backend/SPEC.md §7.4). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Claim c where c.id = :id")
	Optional<Claim> findForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Claim c where c.id in :ids order by c.id")
	List<Claim> findAllForUpdate(@Param("ids") Collection<UUID> ids);

	Page<Claim> findByClaimantId(UUID claimantId, Pageable pageable);

	Page<Claim> findByClaimantIdAndStatus(UUID claimantId, ClaimStatus status, Pageable pageable);

	List<Claim> findByPayoutBatchIdOrderByClaimNumberAsc(UUID payoutBatchId);

	Page<Claim> findByStatusAndPayoutBatchIdIsNull(ClaimStatus status, Pageable pageable);

	List<Claim> findByStatus(ClaimStatus status);

	Optional<Claim> findByClaimNumber(String claimNumber);

	long countByStatus(ClaimStatus status);

	@Query("select min(c.sampleAuditSelectedAt) from Claim c where c.status = :status")
	java.time.Instant oldestSampleSelection(@Param("status") ClaimStatus status);

}
