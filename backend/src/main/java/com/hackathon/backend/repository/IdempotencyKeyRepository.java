package com.hackathon.backend.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

	Optional<IdempotencyKey> findByUserIdAndOperationAndIdempotencyKey(UUID userId, String operation, String key);

	@Modifying
	@Query("delete from IdempotencyKey k where k.createdAt < :before")
	int deleteOlderThan(@Param("before") Instant before);

}
