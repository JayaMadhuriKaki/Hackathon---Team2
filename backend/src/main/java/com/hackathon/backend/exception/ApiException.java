package com.hackathon.backend.exception;

/**
 * Thrown by services for any expected failure. The {@link ErrorCode} decides the HTTP
 * status; {@code details} is optional structured data (e.g. per-line receipt errors).
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final transient Object details;

	public ApiException(ErrorCode code, String message) {
		this(code, message, null);
	}

	public ApiException(ErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}

}
