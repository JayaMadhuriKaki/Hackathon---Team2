package com.hackathon.backend.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.Department;
import com.hackathon.backend.domain.PolicyRule;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.domain.UserRole;
import com.hackathon.backend.exception.TraceIdFilter;
import com.hackathon.backend.policy.SampleAuditSelector;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.repository.PolicyRuleRepository;
import com.hackathon.backend.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base for API flow tests: real security chain, H2, a pinned clock (2026-11-27 12:00 IST), a
 * controllable sample draw, and the synthetic personas of database/SPEC.md §7.1. Every test
 * starts from empty claim tables.
 */
@SpringBootTest
@Import(ApiTestSupport.TestBeans.class)
abstract class ApiTestSupport {

	static final String PASSWORD = "Test-only-pw-1";

	/** The draw returned by the stub sampler; tests set it before submitting. */
	static final AtomicReference<Double> NEXT_DRAW = new AtomicReference<>(0.5);

	@TestConfiguration
	static class TestBeans {

		@Bean
		@Primary
		SampleAuditSelector stubSampleAuditSelector() {
			return new SampleAuditSelector(() -> NEXT_DRAW.get());
		}

	}

	@Autowired
	WebApplicationContext context;

	@Autowired
	TraceIdFilter traceIdFilter;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository users;

	@Autowired
	DepartmentRepository departments;

	@Autowired
	PolicyRuleRepository policyRules;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AppClock clock;

	MockMvc mvc;

	final Map<String, User> people = new HashMap<>();

	private final Map<String, String> tokens = new HashMap<>();

	@BeforeEach
	void resetData() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(traceIdFilter).build();
		clock.setClock(Clock.fixed(LocalDateTime.of(2026, 11, 27, 6, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
		NEXT_DRAW.set(0.5);
		jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
		for (String table : List.of("audit_events", "idempotency_keys", "delegations", "claim_comments",
				"approval_steps", "policy_results", "receipts", "claim_line_items", "claims", "payout_batches",
				"display_counters", "policy_rules", "users", "departments")) {
			jdbc.execute("TRUNCATE TABLE " + table);
		}
		jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
		tokens.clear();
		people.clear();
		seed();
	}

	private void seed() {
		Department consulting = dept("Consulting");
		Department finance = dept("Finance");
		Department operations = dept("Operations");
		User head = user("financehead", "Farah Head", UserRole.FINANCE_HEAD, null, finance, 1);
		User auditor = user("auditor", "Arjun Auditor", UserRole.FINANCE_AUDITOR, head, finance, 2);
		user("auditor2", "Bina Auditor", UserRole.FINANCE_AUDITOR, head, finance, 3);
		User manager = user("manager", "Manoj Manager", UserRole.EMPLOYEE, head, consulting, 4);
		user("employee", "=Eve Employee", UserRole.EMPLOYEE, manager, consulting, 5);
		user("employee2", "Ravi Employee", UserRole.EMPLOYEE, manager, consulting, 6);
		user("nomanager", "Neha Head", UserRole.EMPLOYEE, null, operations, 7);
		user("staff", "Sam Staff", UserRole.EMPLOYEE, auditor, finance, 8);
		String[][] baseline = { { "meals.max_per_day", "\"1500.00\"" }, { "hotel.max_per_night.tier1", "\"6000.00\"" },
				{ "hotel.tier1_cities",
						"[\"Kolkata\",\"Mumbai\",\"Delhi\",\"Chennai\",\"Bangalore\",\"Bengaluru\",\"New Delhi\"]" },
				{ "hotel.max_per_night.other", "\"4000.00\"" }, { "local_cab.max_per_trip", "\"2000.00\"" },
				{ "receipt.mandatory_above", "\"500.00\"" },
				{ "duplicate.match_fields", "[\"vendor\",\"amount\",\"expenseDate\"]" },
				{ "duplicate.match_receipt_hash", "true" }, { "weekend.flag_for_manager", "true" },
				{ "duplicate.match_date_receipt_no", "true" }, { "approval.auditor_threshold", "\"25000.00\"" },
				{ "approval.finance_head_threshold", "\"100000.00\"" }, { "sample_audit.rate", "\"0.10\"" } };
		for (String[] kv : baseline) {
			PolicyRule rule = new PolicyRule();
			rule.setRuleKey(kv[0]);
			rule.setConfigVersion(1);
			rule.setValue(kv[1]);
			policyRules.save(rule);
		}
	}

	private Department dept(String name) {
		Department d = new Department();
		d.setName(name);
		return departments.save(d);
	}

	private User user(String key, String name, UserRole role, User manager, Department dept, int n) {
		User u = new User();
		u.setEmployeeCode(String.format("EMP%04d", n));
		u.setFullName(name);
		u.setEmail(key + "@expenseflow.test");
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u.setRole(role);
		u.setManagerId(manager == null ? null : manager.getId());
		u.setDepartmentId(dept.getId());
		u.setBankAccountNumber(String.format("9%011d", n));
		u.setIfscCode("XFLB0" + String.format("%06d", n));
		users.save(u);
		people.put(key, u);
		return u;
	}

	// ---- HTTP helpers ----

	String token(String who) throws Exception {
		if (!tokens.containsKey(who)) {
			MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + who + "@expenseflow.test\",\"password\":\"" + PASSWORD + "\"}"))
				.andReturn();
			tokens.put(who, json(r).get("accessToken").asString());
		}
		return tokens.get(who);
	}

	MockHttpServletRequestBuilder as(String who, MockHttpServletRequestBuilder request) throws Exception {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(who));
	}

	MvcResult call(String who, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(as(who, request)).andReturn();
	}

	MvcResult callJson(String who, MockHttpServletRequestBuilder request, String body) throws Exception {
		return call(who, request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	JsonNode json(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return body.isEmpty() ? null : mapper.readTree(body);
	}

	/** Creates a draft for {@code who} and returns its id. */
	String newClaim(String who) throws Exception {
		MvcResult r = call(who, post("/api/v1/claims"));
		return json(r).get("claimId").asString();
	}

	String addLine(String who, String claimId, String category, String amount, String date, String vendor,
			String receiptNo) throws Exception {
		String body = "{\"category\":\"" + category + "\",\"amount\":\"" + amount + "\",\"expenseDate\":\"" + date
				+ "\",\"vendor\":\"" + vendor + "\",\"receiptNumber\":\"" + receiptNo + "\"}";
		MvcResult r = callJson(who, post("/api/v1/claims/" + claimId + "/line-items"), body);
		if (r.getResponse().getStatus() != 201) {
			throw new AssertionError("addLine failed: " + r.getResponse().getContentAsString());
		}
		return json(r).get("lineItemId").asString();
	}

	String addHotel(String who, String claimId, String amount, String city, String checkIn, String checkOut,
			String receiptNo) throws Exception {
		String body = "{\"category\":\"HOTEL\",\"amount\":\"" + amount + "\",\"expenseDate\":\"" + checkIn
				+ "\",\"vendor\":\"Hotel " + receiptNo + "\",\"receiptNumber\":\"" + receiptNo + "\",\"city\":\"" + city
				+ "\",\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkOut + "\"}";
		MvcResult r = callJson(who, post("/api/v1/claims/" + claimId + "/line-items"), body);
		if (r.getResponse().getStatus() != 201) {
			throw new AssertionError("addHotel failed: " + r.getResponse().getContentAsString());
		}
		return json(r).get("lineItemId").asString();
	}

	MvcResult upload(String who, String claimId, String lineId, byte[] content) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", content);
		return mvc.perform(multipart("/api/v1/claims/" + claimId + "/line-items/" + lineId + "/receipt").file(file)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(who))).andReturn();
	}

	static byte[] pdf(String text) {
		return ("%PDF-1.4\n% synthetic receipt " + text + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);
	}

	MvcResult submit(String who, String claimId) throws Exception {
		return call(who, post("/api/v1/claims/" + claimId + "/submit"));
	}

	JsonNode claim(String who, String claimId) throws Exception {
		return json(call(who, get("/api/v1/claims/" + claimId)));
	}

	MvcResult decide(String who, String claimId, String action, String comment) throws Exception {
		String body = comment == null ? "{}" : "{\"comment\":\"" + comment + "\"}";
		return callJson(who, post("/api/v1/claims/" + claimId + "/" + action), body);
	}

	static String id(User u) {
		return u.getId().toString();
	}

	static UUID uuid(String s) {
		return UUID.fromString(s);
	}

}
