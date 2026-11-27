package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.PayoutBatch;
import com.hackathon.backend.domain.PayoutBatchStatus;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.repository.PayoutBatchRepository;
import com.hackathon.backend.service.support.Money;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Payout batches and the bank-file export (API-024..029; FR-012..014, CL-06, CL-11, CL-14,
 * B-09). Finance head / admin only. Export is one transaction: CSV built and stored once,
 * batch locked as EXPORTED, every claim PAID, audit rows written. Re-downloads return the
 * same bytes and SHA-256.
 */
@Service
public class PayoutService {

	private final PayoutBatchRepository batches;

	private final ClaimRepository claims;

	private final DepartmentRepository departments;

	private final CapabilityService capabilities;

	private final DisplayIdGenerator ids;

	private final ClaimViewAssembler views;

	private final AuditService audit;

	private final FileStore files;

	private final AppClock clock;

	public PayoutService(PayoutBatchRepository batches, ClaimRepository claims, DepartmentRepository departments,
			CapabilityService capabilities, DisplayIdGenerator ids, ClaimViewAssembler views, AuditService audit,
			FileStore files, AppClock clock) {
		this.batches = batches;
		this.claims = claims;
		this.departments = departments;
		this.capabilities = capabilities;
		this.ids = ids;
		this.views = views;
		this.audit = audit;
		this.files = files;
		this.clock = clock;
	}

	/** API-024. */
	@Transactional(readOnly = true)
	public PageResponse<BatchSummary> list(User caller, Integer page, Integer pageSize) {
		requireAdmin(caller);
		Pageable pageable = PageResponse.pageable(page, pageSize, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<PayoutBatch> result = batches.findAll(pageable);
		return PageResponse.of(result, b -> summary(b, claims.findByPayoutBatchIdOrderByClaimNumberAsc(b.getId())));
	}

	/** API-025: APPROVED and in no batch. Sampled claims are not APPROVED until reviewed (CL-14). */
	@Transactional(readOnly = true)
	public PageResponse<PayoutClaim> eligible(User caller, Integer page, Integer pageSize) {
		requireAdmin(caller);
		Pageable pageable = PageResponse.pageable(page, pageSize, null, Set.of(),
				Sort.by(Sort.Direction.ASC, "decidedAt"));
		Page<Claim> result = claims.findByStatusAndPayoutBatchIdIsNull(ClaimStatus.APPROVED, pageable);
		Map<UUID, User> userMap = views.userMap(result.getContent().stream().map(Claim::getClaimantId).toList());
		Map<UUID, String> deptNames = deptNames();
		return PageResponse.of(result, c -> payoutClaim(c, userMap.get(c.getClaimantId()), deptNames));
	}

	/** API-026: all-or-nothing (architecture/SPEC.md §15). T-14 for every claim. */
	@Transactional
	public BatchDetail create(User caller, CreateBatchRequest request) {
		requireAdmin(caller);
		List<UUID> requested = new ArrayList<>(new LinkedHashSet<>(request.claimIds()));
		List<Claim> locked = claims.findAllForUpdate(requested);
		Map<UUID, Claim> byId = locked.stream().collect(Collectors.toMap(Claim::getId, c -> c));
		List<UUID> ineligible = requested.stream()
			.filter(id -> !byId.containsKey(id) || byId.get(id).getStatus() != ClaimStatus.APPROVED
					|| byId.get(id).getPayoutBatchId() != null)
			.toList();
		if (!ineligible.isEmpty()) {
			throw new ApiException(ErrorCode.CLAIM_NOT_ELIGIBLE_FOR_PAYOUT,
					"Only approved claims that are not in a batch can be added", Map.of("claimIds", ineligible));
		}
		PayoutBatch batch = new PayoutBatch();
		batch.setBatchNumber(ids.nextBatchNumber());
		batch.setStatus(PayoutBatchStatus.CREATED);
		batch.setCreatedBy(caller.getId());
		batches.saveAndFlush(batch);
		audit.event(AuditAction.PAYOUT_BATCH_CREATED, EntityType.PAYOUT_BATCH, batch.getId())
			.actor(caller.getId())
			.details(Map.of("batchNumber", batch.getBatchNumber(), "claimCount", locked.size()))
			.save();
		for (Claim c : locked) {
			c.setStatus(ClaimStatus.IN_PAYOUT_BATCH);
			c.setPayoutBatchId(batch.getId());
			audit.claim(AuditAction.CLAIM_ADDED_TO_BATCH, c.getId())
				.actor(caller.getId())
				.states(ClaimStatus.APPROVED, ClaimStatus.IN_PAYOUT_BATCH)
				.details(Map.of("batchId", batch.getId(), "batchNumber", batch.getBatchNumber()))
				.save();
		}
		claims.saveAllAndFlush(locked);
		return detail(batch);
	}

	/** API-027. */
	@Transactional(readOnly = true)
	public BatchDetail get(User caller, UUID batchId) {
		requireAdmin(caller);
		return detail(batches.findById(batchId).orElseThrow(PayoutService::notFound));
	}

	/** API-028: T-15 for every claim, one transaction (CL-06). */
	@Transactional
	public ExportSummary export(User caller, UUID batchId) {
		requireAdmin(caller);
		PayoutBatch batch = batches.findForUpdate(batchId).orElseThrow(PayoutService::notFound);
		if (batch.getStatus() != PayoutBatchStatus.CREATED) {
			throw new ApiException(ErrorCode.STATE_CONFLICT, "The batch has already been exported");
		}
		List<Claim> batchClaims = claims.findAllForUpdate(
				claims.findByPayoutBatchIdOrderByClaimNumberAsc(batch.getId()).stream().map(Claim::getId).toList());
		batchClaims.sort((a, b) -> a.getClaimNumber().compareTo(b.getClaimNumber()));
		if (batchClaims.isEmpty()) {
			throw new ApiException(ErrorCode.STATE_CONFLICT, "The batch has no claims");
		}
		Map<UUID, User> userMap = views.userMap(batchClaims.stream().map(Claim::getClaimantId).toList());
		Map<UUID, String> deptNames = deptNames();
		List<PayoutCsvWriter.Row> rows = new ArrayList<>();
		BigDecimal total = Money.ZERO;
		for (Claim c : batchClaims) {
			User u = userMap.get(c.getClaimantId());
			rows.add(new PayoutCsvWriter.Row(c.getClaimNumber(), u.getEmployeeCode(), u.getFullName(),
					u.getBankAccountNumber(), u.getIfscCode(), c.getTotalAmount(), deptNames.get(u.getDepartmentId())));
			total = total.add(c.getTotalAmount());
		}
		Instant now = clock.now();
		LocalDate valueDate = clock.todayIst();
		byte[] bytes = PayoutCsvWriter.write(batch.getBatchNumber(), valueDate, rows);
		String fileName = PayoutCsvWriter.fileName(batch.getBatchNumber(), valueDate);
		String sha = ClaimService.sha256(bytes);
		files.writeExport(fileName, bytes);
		deleteOnRollback(fileName);

		batch.setStatus(PayoutBatchStatus.EXPORTED);
		batch.setExportedBy(caller.getId());
		batch.setExportedAt(now);
		batch.setRowCount(rows.size());
		batch.setTotalAmount(Money.scale(total));
		batch.setFileSha256(sha);
		batch.setFilePath(fileName);
		for (Claim c : batchClaims) {
			c.setStatus(ClaimStatus.PAID);
			audit.claim(AuditAction.CLAIM_PAID, c.getId())
				.actor(caller.getId())
				.states(ClaimStatus.IN_PAYOUT_BATCH, ClaimStatus.PAID)
				.details(Map.of("batchNumber", batch.getBatchNumber()))
				.save();
		}
		claims.saveAllAndFlush(batchClaims);
		batches.saveAndFlush(batch);
		audit.event(AuditAction.PAYOUT_BATCH_EXPORTED, EntityType.PAYOUT_BATCH, batch.getId())
			.actor(caller.getId())
			.details(Map.of("fileName", fileName, "rowCount", rows.size(), "totalAmount", Money.format(total),
					"fileSha256", sha))
			.save();
		return new ExportSummary(batch.getId(), batch.getBatchNumber(), fileName, rows.size(), Money.format(total), sha,
				now);
	}

	/** API-029: the stored bytes, only after export. */
	@Transactional(readOnly = true)
	public ClaimService.StoredFile file(User caller, UUID batchId) {
		requireAdmin(caller);
		PayoutBatch batch = batches.findById(batchId).orElseThrow(PayoutService::notFound);
		if (batch.getStatus() != PayoutBatchStatus.EXPORTED) {
			throw new ApiException(ErrorCode.STATE_CONFLICT, "Export the batch before downloading its file");
		}
		return new ClaimService.StoredFile(files.readExport(batch.getFilePath()), "text/csv", batch.getFilePath());
	}

	private BatchDetail detail(PayoutBatch b) {
		List<Claim> batchClaims = claims.findByPayoutBatchIdOrderByClaimNumberAsc(b.getId());
		Map<UUID, User> userMap = views.userMap(batchClaims.stream().map(Claim::getClaimantId).toList());
		Map<UUID, String> deptNames = deptNames();
		return new BatchDetail(summary(b, batchClaims),
				batchClaims.stream().map(c -> payoutClaim(c, userMap.get(c.getClaimantId()), deptNames)).toList());
	}

	private BatchSummary summary(PayoutBatch b, List<Claim> batchClaims) {
		BigDecimal total = batchClaims.stream().map(Claim::getTotalAmount).reduce(Money.ZERO, BigDecimal::add);
		return new BatchSummary(b.getId(), b.getBatchNumber(), b.getStatus().name(), batchClaims.size(),
				Money.format(b.getTotalAmount() != null ? b.getTotalAmount() : total), b.getCreatedAt(), b.getCreatedBy(),
				b.getExportedAt(), b.getExportedBy(), b.getFilePath(), b.getFileSha256());
	}

	private static PayoutClaim payoutClaim(Claim c, User u, Map<UUID, String> deptNames) {
		return new PayoutClaim(c.getId(), c.getClaimNumber(), c.getStatus().name(), Money.format(c.getTotalAmount()),
				ClaimViewAssembler.ref(u), u == null ? null : deptNames.get(u.getDepartmentId()), c.getDecidedAt());
	}

	private Map<UUID, String> deptNames() {
		Map<UUID, String> names = new HashMap<>();
		departments.findAll().forEach(d -> names.put(d.getId(), d.getName()));
		return names;
	}

	private void requireAdmin(User caller) {
		if (!capabilities.isAdmin(caller)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only the finance head or an admin can manage payouts");
		}
	}

	private void deleteOnRollback(String fileName) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						files.deleteExport(fileName);
					}
				}
			});
		}
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.NOT_FOUND, "Payout batch not found");
	}

	public record CreateBatchRequest(@NotEmpty @Size(max = 500) List<UUID> claimIds) {
	}

	public record BatchSummary(UUID batchId, String batchNumber, String status, int claimCount, String totalAmount,
			Instant createdAt, UUID createdBy, Instant exportedAt, UUID exportedBy, String fileName, String fileSha256) {
	}

	public record PayoutClaim(UUID claimId, String claimNumber, String status, String total, PersonRef claimant,
			String department, Instant approvedAt) {
	}

	public record BatchDetail(BatchSummary batch, List<PayoutClaim> claims) {
	}

	public record ExportSummary(UUID batchId, String batchNumber, String fileName, int rowCount, String totalAmount,
			String fileSha256, Instant exportedAt) {
	}

}
