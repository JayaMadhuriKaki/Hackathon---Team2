package com.hackathon.backend.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.exception.ErrorResponse;
import com.hackathon.backend.exception.TraceIdFilter;
import com.hackathon.backend.service.support.Json;

import jakarta.servlet.http.HttpServletResponse;

/** Writes the standard error body from the security filter chain (CLAUDE.md §11.4). */
@Component
public class JsonErrorWriter {

	private final Json json;

	public JsonErrorWriter(Json json) {
		this.json = json;
	}

	public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter()
			.write(json.write(new ErrorResponse(code.name(), message, null, TraceIdFilter.current())));
	}

}
