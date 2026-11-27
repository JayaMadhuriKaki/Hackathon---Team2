package com.hackathon.backend.service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.AuditEvent;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimLineItem;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.EntityType;
import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.domain.Ids;
import com.hackathon.backend.domain.Receipt;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.AuditEventView;
import com.hackathon.backend.dto.ClaimDtos.ClaimDetail;
import com.hackathon.backend.dto.ClaimDtos.ClaimSummary;
import com.hackathon.backend.dto.ClaimDtos.LineItemRequest;
import com.hackathon.backend.dto.ClaimDtos.LineItemView;
import com.hackathon.backend.dto.ClaimDtos.ReceiptView;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.AuditEventRepository;
import com.hackathon.backend.repository.ClaimLineItemRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.ReceiptRepository;
import com.hackathon.backend.security.RateLimiter;
import com.hackathon.backend.service.support.Money;
import com.hackathon.backend.service.support.PageResponse;

/**
 * Claims, line items and receipts (API-004..011, API-013; FR-001, FR-002, FR-019, B-04).
 * Lines can change only while the claim is DRAFT or SENT_BACK, and only by the claimant.
 */
@Service
public class ClaimService {

	private final ClaimRepository claims;

	private final ClaimLineItemRepository lines;

	private final ReceiptRepository receipts;

	private final AuditEventRepository auditEvents;

	private final AuditService audit;

	private final DisplayIdGenerator ids;

	private final ClaimAccessPolicy access;

	private final ClaimViewAssembler views;

	private final FileStore files;

	private final RateLimiter rateLimiter;

	private final AppClock clock;

	private final long maxReceiptBytes;

	private final int uploadsPerMinute;

	public ClaimService(ClaimRepository claims, ClaimLineItemRepository lines, ReceiptRepository receipts,
			AuditEventRepository auditEvents, AuditService audit, DisplayIdGenerator ids, ClaimAccessPolicy access,
			ClaimViewAssembler views, FileStore files, RateLimiter rateLimiter, AppClock clock,
			@Value("${app.receipts.max-bytes:5242880}") long maxReceiptBytes,
			@Value("${app.rate-limit.upload-per-minute:30}") int uploadsPerMinute) {
		this.claims = claims;
		this.lines = lines;
		this.receipts = receipts;
		this.auditEvents = auditEvents;
		this.audit = audit;
		this.ids = ids;
		this.access = access;
		this.views = views;
		this.files = files;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
		this.maxReceiptBytes = maxReceiptBytes;
		this.uploadsPerMinute = uploadsPerMinute;
	}

	/** API-004: the caller's own claims only (FR-019, CL-15). */
	@Transactional(readOnly = true)
	public PageResponse<ClaimSummary> listOwn(User caller, ClaimStatus status, Integer page, Integer pageSize,
			String sort) {
		Pageable pageable = PageResponse.pageable(page, pageSize, sort,
				Set.of("createdAt", "submittedAt", "totalAmount", "claimNumber", "status"),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Claim> result = status == null ? claims.findByClaimantId(caller.getId(), pageable)
				: claims.findByClaimantIdAndStatus(caller.getId(), status, pageable);
		Map<UUID, Integer> counts = new LinkedHashMap<>();
		lines.findByClaimIdIn(result.getContent().stream().map(Claim::getId).toList())
			.forEach(l -> counts.merge(l.getClaimId(), 1, Integer::sum));
		return PageResponse.of(result, c -> views.summary(c, counts.getOrDefault(c.getId(), 0)));
	}

	/** API-005: a new draft owned by the caller (T-01). */
	@Transactional
	public ClaimDetail create(User caller) {
		Claim claim = new Claim();
		claim.setClaimNumber(ids.nextClaimNumber());
		claim.setClaimantId(caller.getId());
		claim.setStatus(ClaimStatus.DRAFT);
		claim.setTotalAmount(Money.ZERO);
		claims.saveAndFlush(claim);
		audit.claim(AuditAction.CLAIM_CREATED, claim.getId())
			.actor(caller.getId())
			.states(null, ClaimStatus.DRAFT)
			.details(Map.of("claimNumber", claim.getClaimNumber()))
			.save();
		return views.detail(claim, caller);
	}

	/** API-006: visible claims only; anything else is 404 (B-10). */
	@Transactional(readOnly = true)
	public ClaimDetail get(User caller, UUID claimId) {
		return views.detail(access.requireVisible(caller, claimId), caller);
	}

	/** API-007. */
	@Transactional
	public LineItemView addLine(User caller, UUID claimId, LineItemRequest request) {
		Claim claim = editableClaim(caller, claimId);
		ClaimLineItem line = new ClaimLineItem();
		line.setClaimId(claim.getId());
		short next = (short) (lines.findByClaimIdOrderByLineNoAsc(claim.getId())
			.stream()
			.mapToInt(ClaimLineItem::getLineNo)
			.max()
			.orElse(0) + 1);
		line.setLineNo(next);
		apply(line, request);
		lines.saveAndFlush(line);
		recomputeTotal(claim);
		audit.event(AuditAction.LINE_ADDED, EntityType.LINE_ITEM, line.getId())
			.claim(claim.getId())
			.actor(caller.getId())
			.details(lineDetails(line))
			.save();
		return views.lineView(line, null, List.of());
	}

	/** API-008. */
	@Transactional
	public LineItemView updateLine(User caller, UUID claimId, UUID lineItemId, LineItemRequest request) {
		Claim claim = editableClaim(caller, claimId);
		ClaimLineItem line = lineOf(claim, lineItemId);
		apply(line, request);
		lines.saveAndFlush(line);
		recomputeTotal(claim);
		audit.event(AuditAction.LINE_UPDATED, EntityType.LINE_ITEM, line.getId())
			.claim(claim.getId())
			.actor(caller.getId())
			.details(lineDetails(line))
			.save();
		return views.lineView(line, receipts.findByLineItemId(line.getId()).orElse(null), List.of());
	}

	/** API-009. */
	@Transactional
	public void deleteLine(User caller, UUID claimId, UUID lineItemId) {
		Claim claim = editableClaim(caller, claimId);
		ClaimLineItem line = lineOf(claim, lineItemId);
		receipts.findByLineItemId(line.getId()).ifPresent(r -> {
			receipts.delete(r);
			deleteAfterCommit(r.getStorageName());
		});
		lines.delete(line);
		lines.flush();
		recomputeTotal(claim);
		audit.event(AuditAction.LINE_DELETED, EntityType.LINE_ITEM, line.getId())
			.claim(claim.getId())
			.actor(caller.getId())
			.details(Map.of("lineNo", line.getLineNo()))
			.save();
	}

	/**
	 * API-010: one receipt per line, replacing any earlier one (SPEC-Q-06). The type is checked
	 * from the file content, not the name or header (CLAUDE.md §13.5); SHA-256 feeds POL-007.
	 */
	@Transactional
	public ReceiptView uploadReceipt(User caller, UUID claimId, UUID lineItemId, byte[] bytes) {
		rateLimiter.check("upload:" + caller.getId(), uploadsPerMinute);
		Claim claim = editableClaim(caller, claimId);
		ClaimLineItem line = lineOf(claim, lineItemId);
		if (bytes == null || bytes.length == 0) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "The receipt file is empty");
		}
		if (bytes.length > maxReceiptBytes) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, "Receipts can be at most 5 MB");
		}
		String contentType = sniff(bytes);
		if (contentType == null) {
			throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Receipts must be PDF, JPEG or PNG files");
		}
		receipts.findByLineItemId(line.getId()).ifPresent(old -> {
			receipts.delete(old);
			receipts.flush();
			deleteAfterCommit(old.getStorageName());
		});
		String storageName = Ids.newId().toString().replace("-", "");
		files.writeReceipt(storageName, bytes);
		deleteOnRollback(storageName);
		Receipt receipt = new Receipt();
		receipt.setLineItemId(line.getId());
		receipt.setStorageName(storageName);
		receipt.setContentType(contentType);
		receipt.setSizeBytes(bytes.length);
		receipt.setSha256(sha256(bytes));
		receipts.saveAndFlush(receipt);
		audit.event(AuditAction.RECEIPT_UPLOADED, EntityType.LINE_ITEM, line.getId())
			.claim(claim.getId())
			.actor(caller.getId())
			.details(Map.of("receiptId", receipt.getId(), "contentType", contentType, "sizeBytes", bytes.length))
			.save();
		return new ReceiptView(receipt.getId(), contentType, bytes.length, receipt.getCreatedAt());
	}

	/** API-011: only to users who can view the claim (NFR-002). */
	@Transactional(readOnly = true)
	public StoredFile downloadReceipt(User caller, UUID claimId, UUID lineItemId) {
		Claim claim = access.requireVisible(caller, claimId);
		ClaimLineItem line = lineOf(claim, lineItemId);
		Receipt receipt = receipts.findByLineItemId(line.getId())
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "This line has no receipt"));
		return new StoredFile(files.readReceipt(receipt.getStorageName()), receipt.getContentType(),
				"receipt-" + claim.getClaimNumber() + "-line-" + line.getLineNo() + extension(receipt.getContentType()));
	}

	/** API-013: the claim's audit history for anyone who can view it. */
	@Transactional(readOnly = true)
	public PageResponse<AuditEventView> auditEvents(User caller, UUID claimId, Integer page, Integer pageSize) {
		Claim claim = access.requireVisible(caller, claimId);
		Pageable pageable = PageResponse.pageable(page, pageSize, null, Set.of(), Sort.unsorted());
		Page<AuditEvent> events = auditEvents.findByClaimIdOrderByIdAsc(claim.getId(), pageable);
		return new PageResponse<>(views.auditViews(events.getContent()), events.getNumber() + 1, events.getSize(),
				events.getTotalElements());
	}

	private Claim editableClaim(User caller, UUID claimId) {
		Claim claim = claims.findForUpdate(claimId).orElseThrow(ClaimAccessPolicy::notFound);
		if (!claim.getClaimantId().equals(caller.getId())) {
			if (access.canView(caller, claim)) {
				throw new ApiException(ErrorCode.FORBIDDEN, "Only the claimant can change this claim");
			}
			throw ClaimAccessPolicy.notFound();
		}
		if (!ClaimStatus.EDITABLE.contains(claim.getStatus())) {
			throw new ApiException(ErrorCode.STATE_CONFLICT,
					"Lines can only be changed while the claim is a draft or sent back (now " + claim.getStatus() + ")");
		}
		return claim;
	}

	private ClaimLineItem lineOf(Claim claim, UUID lineItemId) {
		return lines.findById(lineItemId)
			.filter(l -> l.getClaimId().equals(claim.getId()))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Line item not found"));
	}

	/** Field rules of architecture/SPEC.md §4.4 beyond the bean-validation annotations. */
	private void apply(ClaimLineItem line, LineItemRequest r) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (r.expenseDate().isAfter(clock.todayIst())) {
			errors.put("expenseDate", "must not be in the future");
		}
		if (r.category() == ExpenseCategory.HOTEL) {
			if (r.city() == null || r.city().isBlank()) {
				errors.put("city", "is required for hotel lines");
			}
			if (r.checkInDate() == null) {
				errors.put("checkInDate", "is required for hotel lines");
			}
			if (r.checkOutDate() == null) {
				errors.put("checkOutDate", "is required for hotel lines");
			}
			if (r.checkInDate() != null && r.checkOutDate() != null && !r.checkOutDate().isAfter(r.checkInDate())) {
				errors.put("checkOutDate", "must be after the check-in date");
			}
			if (r.checkInDate() != null && !r.checkInDate().equals(r.expenseDate())) {
				errors.put("expenseDate", "must equal the check-in date for hotel lines");
			}
		}
		else if (r.city() != null || r.checkInDate() != null || r.checkOutDate() != null) {
			errors.put("category", "city and stay dates are only allowed on hotel lines");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid",
					Map.of("fields", errors));
		}
		line.setCategory(r.category());
		line.setAmount(Money.scale(r.amount()));
		line.setExpenseDate(r.expenseDate());
		line.setVendor(r.vendor().trim());
		line.setReceiptNumber(r.receiptNumber().trim());
		boolean hotel = r.category() == ExpenseCategory.HOTEL;
		line.setCity(hotel ? r.city().trim() : null);
		line.setCheckInDate(hotel ? r.checkInDate() : null);
		line.setCheckOutDate(hotel ? r.checkOutDate() : null);
	}

	private void recomputeTotal(Claim claim) {
		BigDecimal total = lines.findByClaimIdOrderByLineNoAsc(claim.getId())
			.stream()
			.map(ClaimLineItem::getAmount)
			.reduce(Money.ZERO, BigDecimal::add);
		claim.setTotalAmount(Money.scale(total));
		claims.save(claim);
	}

	private static Map<String, Object> lineDetails(ClaimLineItem line) {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("lineNo", line.getLineNo());
		d.put("category", line.getCategory().name());
		d.put("amount", Money.format(line.getAmount()));
		d.put("expenseDate", line.getExpenseDate().toString());
		return d;
	}

	/** Magic-byte check for the allow-list PDF, JPEG, PNG (SPEC-Q-06). */
	static String sniff(byte[] b) {
		if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
			return "application/pdf";
		}
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D
				&& b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
			return "image/png";
		}
		return null;
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String extension(String contentType) {
		return switch (contentType) {
			case "application/pdf" -> ".pdf";
			case "image/png" -> ".png";
			default -> ".jpg";
		};
	}

	private void deleteAfterCommit(String storageName) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					files.deleteReceipt(storageName);
				}
			});
		}
	}

	private void deleteOnRollback(String storageName) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						files.deleteReceipt(storageName);
					}
				}
			});
		}
	}

	public record StoredFile(byte[] bytes, String contentType, String fileName) {
	}

}
