package com.hackathon.backend.chain;

import com.hackathon.backend.domain.UserRole;

/** Answers whether anyone can fill a pool step. Supplied by the service layer. */
@FunctionalInterface
public interface ApproverDirectory {

	boolean hasUserWithRole(UserRole role);

}
