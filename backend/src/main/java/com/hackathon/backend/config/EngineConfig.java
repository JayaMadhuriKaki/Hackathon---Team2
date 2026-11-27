package com.hackathon.backend.config;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hackathon.backend.chain.ApprovalChainEngine;
import com.hackathon.backend.policy.PolicyEngine;
import com.hackathon.backend.policy.RoutingDecider;
import com.hackathon.backend.policy.SampleAuditSelector;

/** Wires the pure engines into Spring (backend/SPEC.md §2). */
@Configuration
@EnableScheduling
public class EngineConfig {

	@Bean
	PolicyEngine policyEngine() {
		return PolicyEngine.withDocumentedRules();
	}

	@Bean
	RoutingDecider routingDecider() {
		return new RoutingDecider();
	}

	@Bean
	ApprovalChainEngine approvalChainEngine() {
		return new ApprovalChainEngine();
	}

	/** SecureRandom per draw; never a known seed (architecture/SPEC.md §5.5, B-12). */
	@Bean
	SampleAuditSelector sampleAuditSelector() {
		SecureRandom random = new SecureRandom();
		return new SampleAuditSelector(random::nextDouble);
	}

}
