package com.hackathon.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.DisplayCounter;

import jakarta.persistence.LockModeType;

public interface DisplayCounterRepository extends JpaRepository<DisplayCounter, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from DisplayCounter c where c.counterKey = :key")
	Optional<DisplayCounter> findForUpdate(@Param("key") String key);

}
