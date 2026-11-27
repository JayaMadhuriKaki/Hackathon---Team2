package com.hackathon.backend.exception;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request a trace ID (CLAUDE.md §11.10, backend/SPEC.md §7.2). A safe inbound
 * {@code X-Trace-Id} is reused, otherwise a UUID is generated. The ID goes into the logging
 * MDC, the response header and every error body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Trace-Id";

	public static final String MDC_KEY = "traceId";

	private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String inbound = request.getHeader(HEADER);
		String traceId = (inbound != null && SAFE_ID.matcher(inbound).matches()) ? inbound
				: UUID.randomUUID().toString();
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/** The current request's trace ID, or {@code "none"} outside a request. */
	public static String current() {
		String traceId = MDC.get(MDC_KEY);
		return traceId != null ? traceId : "none";
	}

}
