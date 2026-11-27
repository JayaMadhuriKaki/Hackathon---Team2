package com.hackathon.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.hackathon.backend.domain.PolicyRule;

/** Insert-only (CL-10, B-08): no update or delete method. */
public interface PolicyRuleRepository extends Repository<PolicyRule, UUID> {

	PolicyRule save(PolicyRule rule);

	@Query("select coalesce(max(r.configVersion), 0) from PolicyRule r")
	int currentVersion();

	@Query("select r from PolicyRule r where r.configVersion <= :version order by r.ruleKey, r.configVersion desc")
	List<PolicyRule> findUpToVersion(@Param("version") int version);

}
