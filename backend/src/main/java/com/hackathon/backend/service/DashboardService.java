package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.DashboardRepository;
import com.hackathon.backend.repository.DashboardRepository.ApprovalTimeRow;
import com.hackathon.backend.repository.DashboardRepository.SpendRow;
import com.hackathon.backend.repository.DashboardRepository.ViolationRow;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.service.support.Money;

/**
 * Dashboard figures (API-030..032; FR-015..017, architecture/SPEC.md §7.3, SPEC-Q-08/11):
 * aggregates only, for finance auditors and admins (ASM-010). Spend counts claims in
 * APPROVED, IN_PAYOUT_BATCH or PAID; violations use each claim's latest submission;
 * approval time is calendar time from the latest submit to the final decision.
 */
@Service
public class DashboardService {

	private final DashboardRepository repository;

	private final DepartmentRepository departments;

	private final CapabilityService capabilities;

	public DashboardService(DashboardRepository repository, DepartmentRepository departments,
			CapabilityService capabilities) {
		this.repository = repository;
		this.departments = departments;
		this.capabilities = capabilities;
	}

	@Transactional(readOnly = true)
	public SpendReport spend(User caller, String groupBy, LocalDate from, LocalDate to, UUID departmentId) {
		requireAccess(caller);
		String group = groupBy == null ? "category" : groupBy;
		if (!List.of("category", "department", "month").contains(group)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "groupBy must be category, department or month");
		}
		Map<UUID, String> deptNames = deptNames();
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		BigDecimal grand = Money.ZERO;
		for (SpendRow row : repository.spendRows()) {
			if (!inRange(row.expenseDate(), from, to) || (departmentId != null && !departmentId.equals(row.departmentId()))) {
				continue;
			}
			String key = switch (group) {
				case "department" -> deptNames.getOrDefault(row.departmentId(), "Unknown");
				case "month" -> row.expenseDate().toString().substring(0, 7);
				default -> row.category().name();
			};
			totals.merge(key, row.amount(), BigDecimal::add);
			counts.merge(key, 1, Integer::sum);
			grand = grand.add(row.amount());
		}
		List<SpendItem> items = new ArrayList<>();
		totals.forEach((k, v) -> items.add(new SpendItem(k, Money.format(v), counts.get(k))));
		items.sort(group.equals("month") ? Comparator.comparing(SpendItem::key)
				: Comparator.comparing((SpendItem i) -> new BigDecimal(i.total())).reversed());
		return new SpendReport(group, items, Money.format(grand));
	}

	@Transactional(readOnly = true)
	public ViolationReport violations(User caller, LocalDate from, LocalDate to, UUID departmentId) {
		requireAccess(caller);
		Map<String, Integer> byRule = new LinkedHashMap<>();
		Map<String, String> typeByRule = new HashMap<>();
		Map<String, java.util.Set<UUID>> claimsByRule = new HashMap<>();
		int total = 0;
		for (ViolationRow row : repository.violationRows()) {
			if (!inRange(row.expenseDate(), from, to) || (departmentId != null && !departmentId.equals(row.departmentId()))) {
				continue;
			}
			byRule.merge(row.ruleId(), 1, Integer::sum);
			typeByRule.put(row.ruleId(), row.resultType().name());
			claimsByRule.computeIfAbsent(row.ruleId(), k -> new java.util.HashSet<>()).add(row.claimId());
			total++;
		}
		List<ViolationItem> items = byRule.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> new ViolationItem(e.getKey(), typeByRule.get(e.getKey()), e.getValue(),
					claimsByRule.get(e.getKey()).size()))
			.toList();
		return new ViolationReport(items, total);
	}

	@Transactional(readOnly = true)
	public ApprovalTimeReport approvalTime(User caller, LocalDate from, LocalDate to, UUID departmentId) {
		requireAccess(caller);
		long count = 0;
		long seconds = 0;
		long routedCount = 0;
		long routedSeconds = 0;
		for (ApprovalTimeRow row : repository.approvalTimeRows()) {
			LocalDate submitted = row.submittedAt().atZone(AppClock.IST).toLocalDate();
			if (!inRange(submitted, from, to) || (departmentId != null && !departmentId.equals(row.departmentId()))) {
				continue;
			}
			long s = Math.max(0, Duration.between(row.submittedAt(), row.decidedAt()).getSeconds());
			count++;
			seconds += s;
			if (row.sampleAuditSelected() == null) {
				routedCount++;
				routedSeconds += s;
			}
		}
		return new ApprovalTimeReport(count, hours(seconds, count), routedCount, hours(routedSeconds, routedCount));
	}

	private static String hours(long seconds, long count) {
		if (count == 0) {
			return null;
		}
		return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600L * count), 2, RoundingMode.HALF_UP)
			.toPlainString();
	}

	private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
		return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
	}

	private Map<UUID, String> deptNames() {
		Map<UUID, String> names = new HashMap<>();
		departments.findAll().forEach(d -> names.put(d.getId(), d.getName()));
		return names;
	}

	private void requireAccess(User caller) {
		if (!capabilities.canSeeDashboard(caller)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "The dashboard is for finance auditors and admins");
		}
	}

	public record SpendItem(String key, String total, int lineCount) {
	}

	public record SpendReport(String groupBy, List<SpendItem> items, String grandTotal) {
	}

	public record ViolationItem(String ruleId, String resultType, int count, int claimCount) {
	}

	public record ViolationReport(List<ViolationItem> items, int total) {
	}

	/** Average hours: all approved claims, and claims that went through the chain only. */
	public record ApprovalTimeReport(long approvedClaims, String averageHours, long routedClaims,
			String averageHoursRouted) {
	}

}
