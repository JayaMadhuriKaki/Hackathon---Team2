package com.hackathon.backend.policy;

import static com.hackathon.backend.policy.RuleSupport.money;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.hackathon.backend.domain.ExpenseCategory;

/**
 * Shared logic of POL-002 and POL-003. Nights = check-out − check-in (ASM-005); the line
 * breaches when amount > per-night limit × nights. Tier-1 city match is trimmed and
 * case-insensitive against the configured list, which includes the aliases (ASM-006).
 */
final class HotelNightlyLimitSupport {

	private HotelNightlyLimitSupport() {
	}

	static boolean isTier1(String city, PolicyConfigSnapshot config) {
		String wanted = RuleSupport.normaliseText(city);
		return config.hotelTier1Cities().stream().map(RuleSupport::normaliseText).anyMatch(wanted::equals);
	}

	static List<PolicyResult> evaluate(LineInput line, EvalContext context, boolean tier1Rule, String ruleId,
			String configKey) {
		if (line.category() != ExpenseCategory.HOTEL || line.checkInDate() == null || line.checkOutDate() == null) {
			return List.of();
		}
		long nights = ChronoUnit.DAYS.between(line.checkInDate(), line.checkOutDate());
		if (nights < 1) {
			return List.of(); // rejected earlier by field validation (architecture/SPEC.md §4.4)
		}
		PolicyConfigSnapshot config = context.config();
		if (isTier1(line.city(), config) != tier1Rule) {
			return List.of();
		}
		BigDecimal perNight = tier1Rule ? config.hotelMaxPerNightTier1() : config.hotelMaxPerNightOther();
		BigDecimal limit = perNight.multiply(BigDecimal.valueOf(nights));
		if (line.amount().compareTo(limit) <= 0) {
			return List.of();
		}
		return RuleSupport.one(line, ruleId, ResultType.LIMIT_BREACH, configKey, limit, money(line.amount()),
				"Hotel in " + line.city().trim() + " for " + nights + " night(s) costs INR " + money(line.amount())
						+ ", above the limit of INR " + money(limit) + " (INR " + money(perNight) + " per night)",
				Map.of("nights", Long.toString(nights), "perNightLimit", money(perNight)));
	}

}
