package com.hackathon.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	List<Department> findAllByOrderByNameAsc();

}
