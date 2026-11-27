package com.hackathon.backend.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.ApproveRequest;
import com.hackathon.backend.dto.ClaimDtos.AuditEventView;
import com.hackathon.backend.dto.ClaimDtos.ClaimDetail;
import com.hackathon.backend.dto.ClaimDtos.ClaimSummary;
import com.hackathon.backend.dto.ClaimDtos.CommentRequest;
import com.hackathon.backend.dto.ClaimDtos.CommentView;
import com.hackathon.backend.dto.ClaimDtos.DecisionRequest;
import com.hackathon.backend.dto.ClaimDtos.LineItemRequest;
import com.hackathon.backend.dto.ClaimDtos.LineItemView;
import com.hackathon.backend.dto.ClaimDtos.ReceiptView;
import com.hackathon.backend.security.CurrentUserService;
import com.hackathon.backend.service.ApprovalService;
import com.hackathon.backend.service.ClaimService;
import com.hackathon.backend.service.IdempotencyService;
import com.hackathon.backend.service.SubmitService;
import com.hackathon.backend.service.support.PageResponse;

import jakarta.validation.Valid;

/** Claims, line items, receipts, submit and approver actions (API-004..013, API-015..018). */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

	static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final ClaimService claims;

	private final SubmitService submit;

	private final ApprovalService approvals;

	private final IdempotencyService idempotency;

	private final CurrentUserService currentUser;

	public ClaimController(ClaimService claims, SubmitService submit, ApprovalService approvals,
			IdempotencyService idempotency, CurrentUserService currentUser) {
		this.claims = claims;
		this.submit = submit;
		this.approvals = approvals;
		this.idempotency = idempotency;
		this.currentUser = currentUser;
	}

	@GetMapping
	public PageResponse<ClaimSummary> list(@RequestParam(required = false) ClaimStatus status,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
			@RequestParam(required = false) String sort) {
		return claims.listOwn(currentUser.require(), status, page, pageSize, sort);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClaimDetail create() {
		return claims.create(currentUser.require());
	}

	@GetMapping("/{claimId}")
	public ClaimDetail get(@PathVariable UUID claimId) {
		return claims.get(currentUser.require(), claimId);
	}

	@PostMapping("/{claimId}/line-items")
	@ResponseStatus(HttpStatus.CREATED)
	public LineItemView addLine(@PathVariable UUID claimId, @Valid @RequestBody LineItemRequest request) {
		return claims.addLine(currentUser.require(), claimId, request);
	}

	@PutMapping("/{claimId}/line-items/{lineItemId}")
	public LineItemView updateLine(@PathVariable UUID claimId, @PathVariable UUID lineItemId,
			@Valid @RequestBody LineItemRequest request) {
		return claims.updateLine(currentUser.require(), claimId, lineItemId, request);
	}

	@DeleteMapping("/{claimId}/line-items/{lineItemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteLine(@PathVariable UUID claimId, @PathVariable UUID lineItemId) {
		claims.deleteLine(currentUser.require(), claimId, lineItemId);
	}

	@PostMapping(path = "/{claimId}/line-items/{lineItemId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ReceiptView uploadReceipt(@PathVariable UUID claimId, @PathVariable UUID lineItemId,
			@RequestParam("file") MultipartFile file) {
		try {
			return claims.uploadReceipt(currentUser.require(), claimId, lineItemId, file.getBytes());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@GetMapping("/{claimId}/line-items/{lineItemId}/receipt")
	public ResponseEntity<byte[]> downloadReceipt(@PathVariable UUID claimId, @PathVariable UUID lineItemId) {
		ClaimService.StoredFile file = claims.downloadReceipt(currentUser.require(), claimId, lineItemId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.fileName()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
			.body(file.bytes());
	}

	@PostMapping("/{claimId}/submit")
	public ResponseEntity<Object> submit(@PathVariable UUID claimId,
			@RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		return idempotency.run(user, "API-012", key, "/claims/" + claimId + "/submit", null, HttpStatus.OK,
				() -> submit.submit(user, claimId));
	}

	@GetMapping("/{claimId}/audit-events")
	public PageResponse<AuditEventView> auditEvents(@PathVariable UUID claimId,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		return claims.auditEvents(currentUser.require(), claimId, page, pageSize);
	}

	@PostMapping("/{claimId}/approve")
	public ResponseEntity<Object> approve(@PathVariable UUID claimId,
			@Valid @RequestBody(required = false) ApproveRequest request,
			@RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		String comment = request == null ? null : request.comment();
		return idempotency.run(user, "API-015", key, "/claims/" + claimId + "/approve", request, HttpStatus.OK,
				() -> approvals.approve(user, claimId, comment));
	}

	@PostMapping("/{claimId}/reject")
	public ResponseEntity<Object> reject(@PathVariable UUID claimId,
			@Valid @RequestBody(required = false) DecisionRequest request,
			@RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		String comment = request == null ? null : request.comment();
		return idempotency.run(user, "API-016", key, "/claims/" + claimId + "/reject", request, HttpStatus.OK,
				() -> approvals.reject(user, claimId, comment));
	}

	@PostMapping("/{claimId}/send-back")
	public ResponseEntity<Object> sendBack(@PathVariable UUID claimId,
			@Valid @RequestBody(required = false) DecisionRequest request,
			@RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String key) {
		User user = currentUser.require();
		String comment = request == null ? null : request.comment();
		return idempotency.run(user, "API-017", key, "/claims/" + claimId + "/send-back", request, HttpStatus.OK,
				() -> approvals.sendBack(user, claimId, comment));
	}

	@PostMapping("/{claimId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentView comment(@PathVariable UUID claimId, @Valid @RequestBody CommentRequest request) {
		return approvals.comment(currentUser.require(), claimId, request.body());
	}

}
