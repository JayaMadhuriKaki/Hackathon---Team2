package com.hackathon.backend.exception;

/**
 * The one error body the API returns (CLAUDE.md §11.4):
 * {@code {"code", "message", "details", "traceId"}}. {@code details} is null when unused.
 */
public record ErrorResponse(String code, String message, Object details, String traceId) {
}
