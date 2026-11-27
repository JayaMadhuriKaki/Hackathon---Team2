package com.hackathon.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.ClaimComment;

public interface ClaimCommentRepository extends JpaRepository<ClaimComment, UUID> {

	List<ClaimComment> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

}
