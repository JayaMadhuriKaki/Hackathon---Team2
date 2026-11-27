package com.hackathon.backend.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.policy.ResultType;

/** Read-only projections for the dashboard (FR-015..017). Grouping happens in the service. */
public interface DashboardRepository extends Repository<Claim, UUID> {

	@Query("""
			select new com.hackathon.backend.repository.DashboardRepository$SpendRow(l.amount, l.category, l.expenseDate,
			u.departmentId) from ClaimLineItem l, Claim c, User u
			where l.claimId = c.id and c.claimantId = u.id
			and c.status in (com.hackathon.backend.domain.ClaimStatus.APPROVED,
			com.hackathon.backend.domain.ClaimStatus.IN_PAYOUT_BATCH, com.hackathon.backend.domain.ClaimStatus.PAID)""")
	List<SpendRow> spendRows();

	@Query("""
			select new com.hackathon.backend.repository.DashboardRepository$ViolationRow(r.ruleId, r.resultType, c.id,
			l.expenseDate, u.departmentId) from PolicyResultEntity r, Claim c, User u, ClaimLineItem l
			where r.claimId = c.id and r.submissionNo = c.submissionNo and c.claimantId = u.id and r.lineItemId = l.id""")
	List<ViolationRow> violationRows();

	@Query("""
			select new com.hackathon.backend.repository.DashboardRepository$ApprovalTimeRow(c.submittedAt, c.decidedAt,
			u.departmentId, c.sampleAuditSelected) from Claim c, User u
			where c.claimantId = u.id and c.submittedAt is not null and c.decidedAt is not null
			and c.status in (com.hackathon.backend.domain.ClaimStatus.APPROVED,
			com.hackathon.backend.domain.ClaimStatus.IN_PAYOUT_BATCH, com.hackathon.backend.domain.ClaimStatus.PAID)""")
	List<ApprovalTimeRow> approvalTimeRows();

	record SpendRow(BigDecimal amount, ExpenseCategory category, LocalDate expenseDate, UUID departmentId) {
	}

	record ViolationRow(String ruleId, ResultType resultType, UUID claimId, LocalDate expenseDate, UUID departmentId) {
	}

	record ApprovalTimeRow(Instant submittedAt, Instant decidedAt, UUID departmentId, Boolean sampleAuditSelected) {
	}

}
