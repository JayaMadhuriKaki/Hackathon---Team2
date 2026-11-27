package com.hackathon.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hackathon.backend.exception.TraceIdFilter;

/** API-001 and the standard error shape (CLAUDE.md §11.4, architecture/SPEC.md §9). */
@SpringBootTest
class HealthControllerTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private TraceIdFilter traceIdFilter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(traceIdFilter).build();
	}

	@Test
	void healthReportsUpWithDatabaseAndTraceHeader() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(header().exists(TraceIdFilter.HEADER))
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void unknownPathReturns404InStandardShapeWithInboundTraceId() throws Exception {
		mockMvc.perform(get("/api/v1/does-not-exist").header(TraceIdFilter.HEADER, "trace-test-0001"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.details").doesNotExist())
				.andExpect(jsonPath("$.traceId").value("trace-test-0001"));
	}

	@Test
	void unsafeInboundTraceIdIsReplaced() throws Exception {
		mockMvc.perform(get("/api/v1/health").header(TraceIdFilter.HEADER, "bad id<script>"))
				.andExpect(status().isOk())
				.andExpect(header().string(TraceIdFilter.HEADER, org.hamcrest.Matchers.matchesPattern(
						"^[0-9a-f-]{36}$")));
	}

	@Test
	void unsupportedMethodReturns405InStandardShape() throws Exception {
		mockMvc.perform(delete("/api/v1/health"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

}
