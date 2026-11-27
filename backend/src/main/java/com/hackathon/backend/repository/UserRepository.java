package com.hackathon.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByManagerId(UUID managerId);

	boolean existsByRole(UserRole role);

	List<User> findByRoleInOrderByFullNameAsc(Collection<UserRole> roles);

}
