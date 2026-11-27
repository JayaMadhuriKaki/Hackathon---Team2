package com.hackathon.backend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.ClaimLineItem;
import com.hackathon.backend.domain.ClaimStatus;

public interface ClaimLineItemRepository extends JpaRepository<ClaimLineItem, UUID> {

	List<ClaimLineItem> findByClaimIdOrderByLineNoAsc(UUID claimId);

	List<ClaimLineItem> findByClaimIdIn(Collection<UUID> claimIds);

	/**
	 * Lines of other claims of this claimant in scope for POL-001 and POL-006
	 * (architecture/SPEC.md §4.3): the claim state is not DRAFT, AUTO_REJECTED or REJECTED.
	 */
	@Query("""
			select l from ClaimLineItem l, Claim c where l.claimId = c.id and c.claimantId = :claimantId
			and c.id <> :claimId and c.status not in :excluded""")
	List<ClaimLineItem> findClaimantEarlierLines(@Param("claimantId") UUID claimantId,
			@Param("claimId") UUID claimId, @Param("excluded") Collection<ClaimStatus> excluded);

	/** Every employee's in-scope lines on the given dates (POL-009 candidates). */
	@Query("""
			select l from ClaimLineItem l, Claim c where l.claimId = c.id and c.id <> :claimId
			and c.status not in :excluded and l.expenseDate in :dates""")
	List<ClaimLineItem> findEarlierLinesOnDates(@Param("claimId") UUID claimId,
			@Param("dates") Collection<LocalDate> dates, @Param("excluded") Collection<ClaimStatus> excluded);

	/** Every employee's in-scope lines whose receipt has one of the hashes (POL-007 candidates). */
	@Query("""
			select l from ClaimLineItem l, Claim c, Receipt r where l.claimId = c.id and r.lineItemId = l.id
			and c.id <> :claimId and c.status not in :excluded and r.sha256 in :hashes""")
	List<ClaimLineItem> findEarlierLinesWithHashes(@Param("claimId") UUID claimId,
			@Param("hashes") Collection<String> hashes, @Param("excluded") Collection<ClaimStatus> excluded);

}
