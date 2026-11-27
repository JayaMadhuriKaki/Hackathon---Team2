package com.hackathon.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.security.CurrentUserService;
import com.hackathon.backend.service.AuthService;
import com.hackathon.backend.service.AuthService.LoginRequest;
import com.hackathon.backend.service.AuthService.LoginResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** API-002 login, API-003 /me, API-036 departments. HTTP mapping only. */
@RestController
@RequestMapping("/api/v1")
@Validated
public class AuthController {

	private final AuthService auth;

	private final CurrentUserService currentUser;

	private final DepartmentRepository departments;

	public AuthController(AuthService auth, CurrentUserService currentUser, DepartmentRepository departments) {
		this.auth = auth;
		this.currentUser = currentUser;
		this.departments = departments;
	}

	@PostMapping("/auth/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return auth.login(request, http.getRemoteAddr());
	}

	@GetMapping("/me")
	public Map<String, Object> me() {
		return auth.me(currentUser.require());
	}

	@GetMapping("/departments")
	public List<Map<String, Object>> departments() {
		currentUser.require();
		return departments.findAllByOrderByNameAsc()
			.stream()
			.map(d -> Map.<String, Object>of("departmentId", d.getId(), "name", d.getName()))
			.toList();
	}

}
