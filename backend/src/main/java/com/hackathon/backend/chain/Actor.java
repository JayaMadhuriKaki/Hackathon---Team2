package com.hackathon.backend.chain;

import com.hackathon.backend.domain.UserRole;

/** The user trying to act. */
public record Actor(String userId, UserRole role) {
}
