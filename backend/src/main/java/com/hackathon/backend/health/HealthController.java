package com.hackathon.backend.health;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simple app-level health used by the frontend status card. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping
	public HealthResponse health() {
		String database;
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			database = "UP";
		}
		catch (Exception ex) {
			database = "DOWN";
		}
		return new HealthResponse("UP", database, Instant.now());
	}

	public record HealthResponse(String status, String database, Instant timestamp) {
	}

}
