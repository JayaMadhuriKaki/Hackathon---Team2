package com.hackathon.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.JwtService;
import com.hackathon.backend.security.RateLimiter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API-002 login and API-003 /me (SPEC-Q-07 proposed: seeded users, email + password, JWT).
 * Login is rate-limited per client IP; every attempt is audited without the password.
 */
@Service
public class AuthService {

	/** Compared against when the email is unknown, so timing does not reveal which emails exist. */
	private final String dummyHash;

	private final UserRepository users;

	private final DepartmentRepository departments;

	private final PasswordEncoder passwords;

	private final JwtService jwt;

	private final RateLimiter rateLimiter;

	private final AuditService audit;

	private final CapabilityService capabilities;

	private final int loginsPerMinute;

	private final int loginsPerIpPerMinute;

	public AuthService(UserRepository users, DepartmentRepository departments, PasswordEncoder passwords,
			JwtService jwt, RateLimiter rateLimiter, AuditService audit, CapabilityService capabilities,
			@Value("${app.rate-limit.login-per-minute:5}") int loginsPerMinute,
			@Value("${app.rate-limit.login-per-ip-per-minute:30}") int loginsPerIpPerMinute) {
		this.users = users;
		this.departments = departments;
		this.passwords = passwords;
		this.jwt = jwt;
		this.rateLimiter = rateLimiter;
		this.audit = audit;
		this.capabilities = capabilities;
		this.loginsPerMinute = loginsPerMinute;
		this.loginsPerIpPerMinute = loginsPerIpPerMinute;
		this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
	}

	@Transactional(noRollbackFor = ApiException.class)
	public LoginResponse login(LoginRequest request, String clientIp) {
		String email = request.email().trim();
		// Failed attempts per account per IP (password guessing), plus a looser cap on all attempts
		// per IP; successful sign-ins of people switching demo roles are not penalised (SPEC-Q-13).
		String failKey = "login-fail:" + clientIp + ":" + email.toLowerCase(java.util.Locale.ROOT);
		rateLimiter.checkNotExceeded(failKey, loginsPerMinute);
		rateLimiter.check("login-ip:" + clientIp, loginsPerIpPerMinute);
		Optional<User> found = users.findByEmailIgnoreCase(email);
		boolean ok = found.isPresent() && passwords.matches(request.password(), found.get().getPasswordHash());
		if (found.isEmpty()) {
			passwords.matches(request.password(), dummyHash);
		}
		if (!ok) {
			rateLimiter.record(failKey);
			audit.event(AuditAction.LOGIN_FAILED, EntityType.USER, found.map(User::getId).orElse(null))
				.details(Map.of("email", email))
				.save();
			throw new ApiException(ErrorCode.UNAUTHENTICATED, "Email or password is incorrect");
		}
		User user = found.get();
		audit.event(AuditAction.LOGIN_SUCCEEDED, EntityType.USER, user.getId()).actor(user.getId()).save();
		JwtService.IssuedToken token = jwt.issue(user);
		return new LoginResponse(token.token(), "Bearer", token.expiresAt(), me(user));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> me(User user) {
		Map<String, Object> me = new LinkedHashMap<>();
		me.put("userId", user.getId());
		me.put("employeeCode", user.getEmployeeCode());
		me.put("name", user.getFullName());
		me.put("email", user.getEmail());
		me.put("role", user.getRole().name());
		me.put("departmentId", user.getDepartmentId());
		me.put("departmentName", departments.findById(user.getDepartmentId()).map(d -> d.getName()).orElse(null));
		me.put("managerId", user.getManagerId());
		me.putAll(capabilities.all(user));
		return me;
	}

	public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(max = 200) String password) {
	}

	public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Map<String, Object> user) {
	}

}
