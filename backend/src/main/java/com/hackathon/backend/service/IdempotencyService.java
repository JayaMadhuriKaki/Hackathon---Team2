package com.hackathon.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.IdempotencyKey;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.IdempotencyKeyRepository;
import com.hackathon.backend.service.support.Json;

/**
 * {@code Idempotency-Key} for API-012, 015, 016, 017, 023, 026, 028 (architecture/SPEC.md §8,
 * backend/SPEC.md §7.3). Same key + same request replays the stored response without running
 * the action again; same key + different request is 422 IDEMPOTENCY_KEY_REUSED; a duplicate
 * that arrives while the first is still running is 409. Keys are kept 24 hours. Without the
 * header the state guards still return 409 on repeats.
 */
@Service
public class IdempotencyService {

	private static final Duration TTL = Duration.ofHours(24);

	private final IdempotencyKeyRepository keys;

	private final Json json;

	private final AppClock clock;

	private final TransactionTemplate newTx;

	public IdempotencyService(IdempotencyKeyRepository keys, Json json, AppClock clock,
			PlatformTransactionManager txManager) {
		this.keys = keys;
		this.json = json;
		this.clock = clock;
		this.newTx = new TransactionTemplate(txManager);
		this.newTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
	}

	public ResponseEntity<Object> run(User user, String operation, String key, String path, Object body,
			HttpStatus successStatus, Supplier<Object> action) {
		if (key == null) {
			return ResponseEntity.status(successStatus).body(action.get());
		}
		if (key.isBlank() || key.length() > 100) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1 to 100 characters");
		}
		String hash = sha256(operation + "|" + path + "|" + (body == null ? "" : json.write(body)));
		Optional<IdempotencyKey> existing = live(user, operation, key);
		if (existing.isPresent()) {
			return replay(existing.get(), hash);
		}
		IdempotencyKey row = new IdempotencyKey();
		row.setUserId(user.getId());
		row.setOperation(operation);
		row.setIdempotencyKey(key);
		row.setRequestHash(hash);
		try {
			newTx.executeWithoutResult(s -> keys.saveAndFlush(row));
		}
		catch (DataIntegrityViolationException ex) {
			return replay(live(user, operation, key).orElseThrow(() -> conflict()), hash);
		}
		Object result;
		try {
			result = action.get();
		}
		catch (RuntimeException ex) {
			// The action failed and changed nothing: free the key so the client can retry.
			newTx.executeWithoutResult(s -> keys.deleteById(row.getId()));
			throw ex;
		}
		String stored = json.write(result);
		newTx.executeWithoutResult(s -> keys.findById(row.getId()).ifPresent(k -> {
			k.setResponseStatus((short) successStatus.value());
			k.setResponseBody(stored);
			keys.save(k);
		}));
		return ResponseEntity.status(successStatus).body(result);
	}

	private Optional<IdempotencyKey> live(User user, String operation, String key) {
		return keys.findByUserIdAndOperationAndIdempotencyKey(user.getId(), operation, key)
			.filter(k -> k.getCreatedAt() == null || k.getCreatedAt().isAfter(clock.now().minus(TTL)));
	}

	private ResponseEntity<Object> replay(IdempotencyKey k, String hash) {
		if (!k.getRequestHash().equals(hash)) {
			throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
					"This Idempotency-Key was already used for a different request");
		}
		if (k.getResponseStatus() == null) {
			throw conflict();
		}
		return ResponseEntity.status(k.getResponseStatus()).body(json.readObject(k.getResponseBody()));
	}

	private static ApiException conflict() {
		return new ApiException(ErrorCode.STATE_CONFLICT, "The same request is still being processed");
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void purgeExpired() {
		keys.deleteOlderThan(clock.now().minus(TTL));
	}

	static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
