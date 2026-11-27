package com.hackathon.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.PolicyResultEntity;
import com.hackathon.backend.policy.ResultType;

public interface PolicyResultRepository extends JpaRepository<PolicyResultEntity, UUID> {

	List<PolicyResultEntity> findByClaimIdAndSubmissionNo(UUID claimId, int submissionNo);

	boolean existsByClaimIdAndSubmissionNoAndResultType(UUID claimId, int submissionNo, ResultType type);

}
