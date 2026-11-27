package com.hackathon.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.hackathon.backend.exception.ErrorCode;

/**
 * Stateless Spring Security (backend/SPEC.md §7.7). Public: API-001 health, API-002 login and
 * the actuator health/metrics endpoints. Everything else needs a valid bearer token; role
 * and object-level checks happen in the services. CSRF is off because no cookies are used.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonErrorWriter errors) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**")
				.permitAll()
				.requestMatchers("/api/v1/health", "/api/v1/auth/login", "/actuator/health/**", "/actuator/health",
						"/actuator/prometheus", "/actuator/info", "/error")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
				.authenticationEntryPoint((req, res, ex) -> errors.write(res, ErrorCode.UNAUTHENTICATED,
						"Sign in to continue: the access token is missing, invalid or expired")))
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> errors.write(res, ErrorCode.UNAUTHENTICATED,
						"Sign in to continue: the access token is missing, invalid or expired"))
				.accessDeniedHandler((req, res, ex) -> errors.write(res, ErrorCode.FORBIDDEN,
						"You are not allowed to do this")));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtService jwtService) {
		return jwtService.decoder();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
