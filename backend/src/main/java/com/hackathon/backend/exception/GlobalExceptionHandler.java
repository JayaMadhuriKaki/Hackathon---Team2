package com.hackathon.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place that turns exceptions into {@link ErrorResponse} (CLAUDE.md §11.3–§11.4,
 * backend/SPEC.md §7.1). Stack traces are logged server-side only, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
		return error(ex.getCode(), ex.getMessage(), ex.getDetails());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
		return error(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", Map.of("fields", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return error(ErrorCode.VALIDATION_FAILED, "Request body is malformed or contains unknown fields", null);
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
		return error(ErrorCode.VALIDATION_FAILED, "A request parameter is missing or has the wrong type", null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex) {
		return error(ErrorCode.NOT_FOUND, "Not found", null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
		return error(ErrorCode.METHOD_NOT_ALLOWED, "This HTTP method is not supported here", null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
		return error(ErrorCode.FILE_TOO_LARGE, "The uploaded file is too large", null);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
		return error(ErrorCode.UNSUPPORTED_FILE_TYPE, "This content type is not supported", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled error", ex);
		return error(ErrorCode.INTERNAL_ERROR, "Something went wrong. Quote the trace ID if you report it.", null);
	}

	private static ResponseEntity<ErrorResponse> error(ErrorCode code, String message, Object details) {
		return ResponseEntity.status(code.status())
				.body(new ErrorResponse(code.name(), message, details, TraceIdFilter.current()));
	}

}
