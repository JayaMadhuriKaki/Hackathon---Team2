package com.hackathon.backend.service.support;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Thin wrapper over the application's Jackson mapper for JSONB columns and hashing. */
@Component
public class Json {

	private final ObjectMapper mapper;

	public Json(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public String write(Object value) {
		return value == null ? null : mapper.writeValueAsString(value);
	}

	public JsonNode read(String json) {
		return json == null ? null : mapper.readTree(json);
	}

	public Object readObject(String json) {
		return json == null ? null : mapper.readValue(json, Object.class);
	}

	public ObjectMapper mapper() {
		return mapper;
	}

}
