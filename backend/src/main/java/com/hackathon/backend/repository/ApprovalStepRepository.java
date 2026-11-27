package com.hackathon.backend.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.StepType;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

	List<ApprovalStep> findByClaimIdOrderBySubmissionNoAscStepOrderAsc(UUID claimId);

	List<ApprovalStep> findByClaimIdAndSubmissionNoOrderByStepOrderAsc(UUID claimId, int submissionNo);

	Optional<ApprovalStep> findByClaimIdAndStatus(UUID claimId, StepStatus status);

	List<ApprovalStep> findByClaimIdInAndStatus(Collection<UUID> claimIds, StepStatus status);

	boolean existsByClaimIdAndActedById(UUID claimId, UUID actedById);

	/** PENDING manager steps assigned to any of these approvers (API-014). */
	List<ApprovalStep> findByStatusAndStepTypeAndAssignedApproverIdIn(StepStatus status, StepType stepType,
			Collection<UUID> approverIds);

	List<ApprovalStep> findByStatusAndStepType(StepStatus status, StepType stepType);

	long countByStatusAndStepType(StepStatus status, StepType stepType);

	@Query("select min(s.activatedAt) from ApprovalStep s where s.status = :status and s.stepType = :type")
	Instant oldestActivation(@Param("status") StepStatus status, @Param("type") StepType type);

}
