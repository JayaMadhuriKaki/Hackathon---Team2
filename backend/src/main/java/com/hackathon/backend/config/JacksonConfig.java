package com.hackathon.backend.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;

/**
 * Rejects unknown JSON fields with 400 VALIDATION_FAILED (CLAUDE.md §11.1, mass-assignment
 * guard §13.3). Jackson 3 turns this off by default, so it is enabled explicitly.
 */
@Configuration
public class JacksonConfig {

	@Bean
	JsonMapperBuilderCustomizer rejectUnknownProperties() {
		return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

}
