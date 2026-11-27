package com.hackathon.backend.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.UserRepository;

/**
 * Resolves the signed-in user from the bearer token. The role is re-read from the database on
 * every request, so a token never carries stale rights.
 */
@Component
public class CurrentUserService {

	private final UserRepository users;

	public CurrentUserService(UserRepository users) {
		this.users = users;
	}

	public User require() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in to continue");
		}
		UUID id;
		try {
			id = UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in to continue");
		}
		return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in to continue"));
	}

}
