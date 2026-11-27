package com.hackathon.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.PayoutBatch;

import jakarta.persistence.LockModeType;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from PayoutBatch b where b.id = :id")
	Optional<PayoutBatch> findForUpdate(@Param("id") UUID id);

}
