package com.hackathon.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * HS256 bearer tokens (backend/SPEC.md §7.7): {@code sub} = user ID, {@code role}, {@code exp}.
 * Uses Spring Security's Nimbus integration rather than hand-written crypto.
 */
@Component
public class JwtService {

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	private final SecretKey key;

	private final long ttlMinutes;

	private final AppClock clock;

	private final JwtEncoder encoder;

	private final JwtDecoder decoder;

	public JwtService(@Value("${app.jwt.secret:}") String secret, @Value("${app.jwt.ttl-minutes:480}") long ttlMinutes,
			AppClock clock) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secret.isBlank()) {
			bytes = new byte[48];
			new SecureRandom().nextBytes(bytes);
			log.warn("JWT_SECRET is not set: using a random secret; tokens will not survive a restart");
		}
		else if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
		}
		this.key = new SecretKeySpec(bytes, "HmacSHA256");
		this.ttlMinutes = ttlMinutes;
		this.clock = clock;
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	public IssuedToken issue(User user) {
		Instant now = clock.now();
		Instant expiresAt = now.plusSeconds(ttlMinutes * 60);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(user.getId().toString())
			.claim("role", user.getRole().name())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.id(Base64.getUrlEncoder().withoutPadding().encodeToString(user.getId().toString().getBytes()))
			.build();
		String token = encoder
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	public JwtDecoder decoder() {
		return decoder;
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

}
