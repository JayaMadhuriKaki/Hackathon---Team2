package com.hackathon.backend.chain;

/** Outcome plus, for a delegate, the delegator acted for. */
public record StepAuthorization(AuthorizationOutcome outcome, String onBehalfOfId) {

	public static StepAuthorization allowed(String onBehalfOfId) {
		return new StepAuthorization(AuthorizationOutcome.ALLOWED, onBehalfOfId);
	}

	public static StepAuthorization denied(AuthorizationOutcome outcome) {
		return new StepAuthorization(outcome, null);
	}

	public boolean isAllowed() {
		return outcome == AuthorizationOutcome.ALLOWED;
	}

}
