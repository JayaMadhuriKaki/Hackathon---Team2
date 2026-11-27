package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.domain.PolicyRule;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.policy.PolicyConfigSnapshot;
import com.hackathon.backend.repository.PolicyRuleRepository;
import com.hackathon.backend.service.support.Json;
import com.hackathon.backend.service.support.Money;

import tools.jackson.databind.JsonNode;

/**
 * Policy as configuration (architecture/SPEC.md §4.1, CL-10, B-08): builds the typed snapshot
 * at a config version, lists and changes rule values. Changes insert a new row with the next
 * version, so claims already submitted keep the version they stored.
 */
@Service
public class PolicyConfigService {

	public static final List<String> KEYS = List.of("meals.max_per_day", "hotel.max_per_night.tier1",
			"hotel.tier1_cities", "hotel.max_per_night.other", "local_cab.max_per_trip", "receipt.mandatory_above",
			"duplicate.match_fields", "duplicate.match_receipt_hash", "weekend.flag_for_manager",
			"duplicate.match_date_receipt_no", "approval.auditor_threshold", "approval.finance_head_threshold",
			"sample_audit.rate");

	private static final Set<String> POSITIVE_MONEY = Set.of("meals.max_per_day", "hotel.max_per_night.tier1",
			"hotel.max_per_night.other", "local_cab.max_per_trip", "approval.auditor_threshold",
			"approval.finance_head_threshold");

	private static final Set<String> BOOLEANS = Set.of("duplicate.match_receipt_hash", "weekend.flag_for_manager",
			"duplicate.match_date_receipt_no");

	private static final Set<String> MATCH_FIELDS = Set.of("vendor", "amount", "expenseDate");

	public static final List<String> RECEIPT_TYPES = List.of("application/pdf", "image/jpeg", "image/png");

	private final PolicyRuleRepository repository;

	private final AuditService audit;

	private final CapabilityService capabilities;

	private final Json json;

	public PolicyConfigService(PolicyRuleRepository repository, AuditService audit, CapabilityService capabilities,
			Json json) {
		this.repository = repository;
		this.audit = audit;
		this.capabilities = capabilities;
		this.json = json;
	}

	public int currentVersion() {
		return repository.currentVersion();
	}

	/** Latest row per key at or below {@code version}. */
	public Map<String, PolicyRule> rowsAt(int version) {
		Map<String, PolicyRule> rows = new LinkedHashMap<>();
		for (PolicyRule rule : repository.findUpToVersion(version)) {
			rows.putIfAbsent(rule.getRuleKey(), rule);
		}
		return rows;
	}

	public PolicyConfigSnapshot snapshot(int version) {
		Map<String, JsonNode> v = new LinkedHashMap<>();
		rowsAt(version).forEach((key, rule) -> v.put(key, json.read(rule.getValue())));
		for (String key : KEYS) {
			if (!v.containsKey(key)) {
				throw new IllegalStateException("Policy configuration key missing: " + key);
			}
		}
		return new PolicyConfigSnapshot(version, money(v.get("meals.max_per_day")),
				money(v.get("hotel.max_per_night.tier1")), new LinkedHashSet<>(strings(v.get("hotel.tier1_cities"))),
				money(v.get("hotel.max_per_night.other")), money(v.get("local_cab.max_per_trip")),
				money(v.get("receipt.mandatory_above")), strings(v.get("duplicate.match_fields")),
				v.get("duplicate.match_receipt_hash").asBoolean(), v.get("weekend.flag_for_manager").asBoolean(),
				v.get("duplicate.match_date_receipt_no").asBoolean(), money(v.get("approval.auditor_threshold")),
				money(v.get("approval.finance_head_threshold")), new BigDecimal(v.get("sample_audit.rate").asString()));
	}

	public PolicyConfigSnapshot current() {
		return snapshot(currentVersion());
	}

	/** API-034: latest value of each key. Admin only (CL-10). */
	@Transactional(readOnly = true)
	public List<RuleView> list(User caller) {
		requireAdmin(caller);
		List<RuleView> result = new ArrayList<>();
		rowsAt(currentVersion()).forEach((key, rule) -> result.add(view(rule)));
		result.sort((a, b) -> Integer.compare(KEYS.indexOf(a.ruleKey()), KEYS.indexOf(b.ruleKey())));
		return result;
	}

	/** API-035: validate, insert the next version, audit old and new value (CL-10, B-08). */
	@Transactional
	public synchronized RuleView update(User caller, String ruleKey, JsonNode value) {
		requireAdmin(caller);
		if (!KEYS.contains(ruleKey)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "Unknown policy rule key");
		}
		int version = currentVersion();
		Map<String, PolicyRule> rows = rowsAt(version);
		JsonNode normalised = validate(ruleKey, value, rows);
		PolicyRule rule = new PolicyRule();
		rule.setRuleKey(ruleKey);
		rule.setConfigVersion(version + 1);
		rule.setValue(json.write(normalised));
		rule.setCreatedBy(caller.getId());
		repository.save(rule);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("ruleKey", ruleKey);
		details.put("oldValue", json.readObject(rows.get(ruleKey).getValue()));
		details.put("newValue", json.readObject(rule.getValue()));
		details.put("configVersion", version + 1);
		audit.event(AuditAction.POLICY_RULE_CHANGED, EntityType.POLICY_RULE, rule.getId())
			.actor(caller.getId())
			.details(details)
			.save();
		return new RuleView(ruleKey, json.readObject(rule.getValue()), version + 1, rule.getCreatedBy(), Instant.now());
	}

	/** API-037: what the UI needs for client-side validation (§10.3, §15). */
	public Map<String, Object> clientConfig() {
		PolicyConfigSnapshot s = current();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("receiptMandatoryAbove", Money.format(s.receiptMandatoryAbove()));
		result.put("tier1Cities", List.copyOf(s.hotelTier1Cities()));
		result.put("categories", Arrays.stream(ExpenseCategory.values()).map(Enum::name).toList());
		result.put("allowedReceiptTypes", RECEIPT_TYPES);
		result.put("maxReceiptBytes", 5_242_880);
		result.put("configVersion", s.configVersion());
		return result;
	}

	private void requireAdmin(User caller) {
		if (!capabilities.isAdmin(caller)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only the finance head or an admin can manage policy rules");
		}
	}

	private RuleView view(PolicyRule rule) {
		return new RuleView(rule.getRuleKey(), json.readObject(rule.getValue()), rule.getConfigVersion(),
				rule.getCreatedBy(), rule.getCreatedAt());
	}

	private JsonNode validate(String key, JsonNode value, Map<String, PolicyRule> rows) {
		if (value == null || value.isNull()) {
			throw invalid(key, "a value is required");
		}
		if (BOOLEANS.contains(key)) {
			if (!value.isBoolean()) {
				throw invalid(key, "must be true or false");
			}
			return value;
		}
		if (key.equals("hotel.tier1_cities") || key.equals("duplicate.match_fields")) {
			List<String> items = value.isArray() ? strings(value) : List.of();
			if (items.isEmpty() || items.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 100)) {
				throw invalid(key, "must be a non-empty list of names");
			}
			if (key.equals("duplicate.match_fields") && !MATCH_FIELDS.containsAll(items)) {
				throw invalid(key, "may only contain " + MATCH_FIELDS);
			}
			return json.read(json.write(items.stream().map(String::trim).distinct().toList()));
		}
		BigDecimal number = decimal(key, value);
		if (key.equals("sample_audit.rate")) {
			if (number.signum() <= 0 || number.compareTo(BigDecimal.ONE) > 0) {
				throw invalid(key, "must be greater than 0 and at most 1");
			}
			return json.read(json.write(number.toPlainString()));
		}
		if (number.scale() > 2 || number.precision() - number.scale() > 10) {
			throw invalid(key, "must be an amount with at most 2 decimals");
		}
		if (POSITIVE_MONEY.contains(key) ? number.signum() <= 0 : number.signum() < 0) {
			throw invalid(key, POSITIVE_MONEY.contains(key) ? "must be greater than 0" : "must not be negative");
		}
		BigDecimal auditor = key.equals("approval.auditor_threshold") ? number
				: money(json.read(rows.get("approval.auditor_threshold").getValue()));
		BigDecimal head = key.equals("approval.finance_head_threshold") ? number
				: money(json.read(rows.get("approval.finance_head_threshold").getValue()));
		if (head.compareTo(auditor) <= 0) {
			throw invalid(key, "the finance head threshold must be greater than the auditor threshold");
		}
		return json.read(json.write(Money.format(number)));
	}

	private static BigDecimal decimal(String key, JsonNode value) {
		try {
			if (value.isNumber()) {
				return value.decimalValue();
			}
			if (value.isString()) {
				return new BigDecimal(value.asString().trim());
			}
		}
		catch (NumberFormatException ex) {
			// fall through
		}
		throw invalid(key, "must be a number");
	}

	private static ApiException invalid(String key, String problem) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid value for " + key + ": " + problem,
				Map.of("fields", Map.of("value", problem)));
	}

	private static BigDecimal money(JsonNode node) {
		return Money.scale(node.isNumber() ? node.decimalValue() : new BigDecimal(node.asString()));
	}

	private static List<String> strings(JsonNode node) {
		List<String> result = new ArrayList<>();
		node.forEach(n -> result.add(n.isString() ? n.asString() : null));
		return result;
	}

	public record RuleView(String ruleKey, Object value, int configVersion, UUID updatedBy, Instant updatedAt) {
	}

}
