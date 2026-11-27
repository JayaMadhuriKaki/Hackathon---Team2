package com.hackathon.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.backend.chain.ApprovalChainEngine;
import com.hackathon.backend.chain.ChainInput;
import com.hackathon.backend.chain.ChainPlan;
import com.hackathon.backend.chain.PlannedStep;
import com.hackathon.backend.config.AppClock;
import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimLineItem;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.PolicyResultEntity;
import com.hackathon.backend.domain.Receipt;
import com.hackathon.backend.domain.StepStatus;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.ClaimDetail;
import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.policy.EarlierLine;
import com.hackathon.backend.policy.LineInput;
import com.hackathon.backend.policy.PolicyConfigSnapshot;
import com.hackathon.backend.policy.PolicyEngine;
import com.hackathon.backend.policy.PolicyEvaluation;
import com.hackathon.backend.policy.PolicyInput;
import com.hackathon.backend.policy.PolicyResult;
import com.hackathon.backend.policy.ResultType;
import com.hackathon.backend.policy.RoutingDecider;
import com.hackathon.backend.policy.RoutingDecision;
import com.hackathon.backend.policy.RoutingOutcome;
import com.hackathon.backend.policy.SampleAuditSelector;
import com.hackathon.backend.policy.SampleDraw;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.ClaimLineItemRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.PolicyResultRepository;
import com.hackathon.backend.repository.ReceiptRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.service.support.Json;
import com.hackathon.backend.service.support.Money;

/**
 * The submit pipeline (architecture/SPEC.md §3.3, backend/SPEC.md §6.3), in one transaction
 * with the claim row locked: checks, policy engine, routing decision, then auto-reject (T-03),
 * route through the computed chain (T-04) or auto-approve with the sample draw (T-05..T-07).
 * A resubmission after send-back restarts everything (CL-12, B-05).
 */
@Service
public class SubmitService {

	private static final Set<StepStatus> SUPERSEDABLE = EnumSet.of(StepStatus.WAITING, StepStatus.PENDING,
			StepStatus.APPROVED);

	private final ClaimRepository claims;

	private final ClaimLineItemRepository lines;

	private final ReceiptRepository receipts;

	private final PolicyResultRepository results;

	private final ApprovalStepRepository steps;

	private final UserRepository users;

	private final PolicyConfigService config;

	private final PolicyEngine engine;

	private final RoutingDecider router;

	private final ApprovalChainEngine chainEngine;

	private final SampleAuditSelector sampler;

	private final AuditService audit;

	private final ClaimAccessPolicy access;

	private final ClaimViewAssembler views;

	private final AppClock clock;

	private final Json json;

	public SubmitService(ClaimRepository claims, ClaimLineItemRepository lines, ReceiptRepository receipts,
			PolicyResultRepository results, ApprovalStepRepository steps, UserRepository users,
			PolicyConfigService config, PolicyEngine engine, RoutingDecider router, ApprovalChainEngine chainEngine,
			SampleAuditSelector sampler, AuditService audit, ClaimAccessPolicy access, ClaimViewAssembler views,
			AppClock clock, Json json) {
		this.claims = claims;
		this.lines = lines;
		this.receipts = receipts;
		this.results = results;
		this.steps = steps;
		this.users = users;
		this.config = config;
		this.engine = engine;
		this.router = router;
		this.chainEngine = chainEngine;
		this.sampler = sampler;
		this.audit = audit;
		this.access = access;
		this.views = views;
		this.clock = clock;
		this.json = json;
	}

	@Transactional
	public ClaimDetail submit(User caller, UUID claimId) {
		// 1. Lock, owner, state.
		Claim claim = claims.findForUpdate(claimId).orElseThrow(ClaimAccessPolicy::notFound);
		if (!claim.getClaimantId().equals(caller.getId())) {
			if (access.canView(caller, claim)) {
				throw new ApiException(ErrorCode.FORBIDDEN, "Only the claimant can submit this claim");
			}
			throw ClaimAccessPolicy.notFound();
		}
		ClaimStatus from = claim.getStatus();
		if (!ClaimStatus.EDITABLE.contains(from)) {
			throw new ApiException(ErrorCode.STATE_CONFLICT, "Only a draft or sent-back claim can be submitted (now "
					+ from + ")");
		}

		// 2. Lines and receipts (ASM-003): state unchanged on failure.
		List<ClaimLineItem> claimLines = lines.findByClaimIdOrderByLineNoAsc(claim.getId());
		if (claimLines.isEmpty()) {
			throw new ApiException(ErrorCode.CLAIM_HAS_NO_LINES, "Add at least one line item before submitting");
		}
		int version = config.currentVersion();
		PolicyConfigSnapshot snapshot = config.snapshot(version);
		Map<UUID, Receipt> receiptByLine = receipts.findByLineItemIdIn(ids(claimLines))
			.stream()
			.collect(Collectors.toMap(Receipt::getLineItemId, Function.identity()));
		List<Map<String, Object>> missing = new ArrayList<>();
		for (ClaimLineItem l : claimLines) {
			if (l.getAmount().compareTo(snapshot.receiptMandatoryAbove()) > 0 && !receiptByLine.containsKey(l.getId())) {
				missing.add(Map.of("lineItemId", l.getId(), "code", "RECEIPT_REQUIRED", "message",
						"Line " + l.getLineNo() + " is above INR " + Money.format(snapshot.receiptMandatoryAbove())
								+ " and needs a receipt"));
			}
		}
		if (!missing.isEmpty()) {
			throw new ApiException(ErrorCode.RECEIPT_REQUIRED, "Upload a receipt for every line above INR "
					+ Money.format(snapshot.receiptMandatoryAbove()), Map.of("lines", missing));
		}

		// 3. Restart on resubmission (CL-12): earlier steps no longer count.
		Instant now = clock.now();
		if (from == ClaimStatus.SENT_BACK) {
			for (ApprovalStep step : steps.findByClaimIdOrderBySubmissionNoAscStepOrderAsc(claim.getId())) {
				if (SUPERSEDABLE.contains(step.getStatus())) {
					step.setStatus(StepStatus.SUPERSEDED);
				}
			}
		}
		claim.setSubmissionNo(claim.getSubmissionNo() + 1);
		claim.setSubmittedAt(now);
		claim.setConfigVersion(version);
		claim.setDecidedAt(null);
		claim.setSampleAuditSelected(null);
		claim.setSampleAuditSelectedAt(null);
		audit.claim(AuditAction.CLAIM_SUBMITTED, claim.getId())
			.actor(caller.getId())
			.states(from, ClaimStatus.SUBMITTED)
			.details(Map.of("submissionNo", claim.getSubmissionNo(), "configVersion", version, "total",
					Money.format(claim.getTotalAmount())))
			.save();

		// 4. Policy engine on every line.
		PolicyEvaluation evaluation = engine.evaluate(policyInput(claim, claimLines, receiptByLine, snapshot));
		for (PolicyResult r : evaluation.results()) {
			if (r.resultType() != ResultType.VALIDATION) {
				results.save(entity(claim, r));
			}
		}
		audit.claim(AuditAction.POLICY_EVALUATED, claim.getId())
			.details(evaluationSummary(evaluation))
			.save();

		// 5. Route in the fixed order of CLAUDE.md §2.7.
		RoutingDecision decision = router.decide(evaluation, claim.getTotalAmount(), caller.getRole(),
				snapshot.approvalAuditorThreshold());
		if (from == ClaimStatus.SENT_BACK && decision.outcome() == RoutingOutcome.AUTO_APPROVE) {
			// CL-12: after send-back, approval begins again at the manager; a resubmission is never
			// auto-approved, so lowering the amount cannot skip the chain (B-05).
			decision = new RoutingDecision(RoutingOutcome.ROUTE, List.of(),
					"Routed for approval: resubmitted after being sent back (CL-12)");
		}
		switch (decision.outcome()) {
			case AUTO_REJECT -> autoReject(claim, decision, now);
			case ROUTE -> route(claim, caller, decision, snapshot, now);
			case AUTO_APPROVE -> autoApprove(claim, snapshot, now);
		}
		claims.saveAndFlush(claim);
		return views.detail(claim, caller);
	}

	private void autoReject(Claim claim, RoutingDecision decision, Instant now) {
		claim.setStatus(ClaimStatus.AUTO_REJECTED);
		claim.setDecidedAt(now);
		List<Map<String, Object>> breaches = decision.drivingResults()
			.stream()
			.map(r -> Map.<String, Object>of("lineItemId", r.lineItemId(), "ruleId", r.ruleId(), "limit",
					Money.format(r.configuredLimit()), "actual", r.actualValue()))
			.toList();
		audit.claim(AuditAction.CLAIM_AUTO_REJECTED, claim.getId())
			.states(ClaimStatus.SUBMITTED, ClaimStatus.AUTO_REJECTED)
			.comment(decision.reason())
			.details(Map.of("breaches", breaches))
			.save();
	}

	private void route(Claim claim, User claimant, RoutingDecision decision, PolicyConfigSnapshot snapshot,
			Instant now) {
		ChainPlan plan = chainEngine.compute(new ChainInput(claimant.getId().toString(), claimant.getRole(),
				claimant.getManagerId() == null ? null : claimant.getManagerId().toString(), claim.getTotalAmount(),
				snapshot.approvalAuditorThreshold(), snapshot.approvalFinanceHeadThreshold(), users::existsByRole));
		ApprovalStep pending = null;
		for (PlannedStep planned : plan.steps()) {
			ApprovalStep step = new ApprovalStep();
			step.setClaimId(claim.getId());
			step.setSubmissionNo(claim.getSubmissionNo());
			step.setStepOrder((short) planned.level());
			step.setStepType(planned.stepType());
			step.setAssignedApproverId(
					planned.assignedApproverId() == null ? null : UUID.fromString(planned.assignedApproverId()));
			step.setStatus(planned.status());
			step.setSkipReason(planned.skipReason());
			if (planned.status() == StepStatus.PENDING) {
				step.setActivatedAt(now);
				pending = step;
			}
			steps.save(step);
			if (planned.status() == StepStatus.SKIPPED) {
				audit.claim(AuditAction.STEP_SKIPPED, claim.getId())
					.details(Map.of("stepType", planned.stepType().name(), "level", planned.level(), "skipReason",
							planned.skipReason().name()))
					.save();
			}
		}
		ClaimStatus target = plan.claimStatus();
		claim.setStatus(target);
		audit.claim(AuditAction.CLAIM_ROUTED, claim.getId())
			.states(ClaimStatus.SUBMITTED, target)
			.comment(decision.reason())
			.details(Map.of("steps", plan.steps().stream().map(s -> s.stepType() + ":" + s.status()).toList()))
			.save();
		audit.claim(AuditAction.STEP_ACTIVATED, claim.getId())
			.details(Map.of("stepType", pending.getStepType().name(), "level", (int) pending.getStepOrder()))
			.save();
	}

	private void autoApprove(Claim claim, PolicyConfigSnapshot snapshot, Instant now) {
		audit.claim(AuditAction.CLAIM_AUTO_APPROVED, claim.getId())
			.states(ClaimStatus.SUBMITTED, ClaimStatus.AUTO_APPROVED)
			.comment("Passed every policy check")
			.save();
		SampleDraw draw = sampler.draw(snapshot.sampleAuditRate());
		Map<String, Object> details = Map.of("draw", draw.value(), "rate", snapshot.sampleAuditRate().toPlainString());
		claim.setSampleAuditSelected(draw.selected());
		if (draw.selected()) {
			claim.setStatus(ClaimStatus.SAMPLE_AUDIT_PENDING);
			claim.setSampleAuditSelectedAt(now);
			audit.claim(AuditAction.SAMPLE_AUDIT_SELECTED, claim.getId())
				.states(ClaimStatus.AUTO_APPROVED, ClaimStatus.SAMPLE_AUDIT_PENDING)
				.details(details)
				.save();
		}
		else {
			claim.setStatus(ClaimStatus.APPROVED);
			claim.setDecidedAt(now);
			audit.claim(AuditAction.SAMPLE_AUDIT_NOT_SELECTED, claim.getId())
				.states(ClaimStatus.AUTO_APPROVED, ClaimStatus.APPROVED)
				.details(details)
				.save();
		}
	}

	/** Loads the "earlier lines" of architecture/SPEC.md §4.3 and builds the engine input. */
	private PolicyInput policyInput(Claim claim, List<ClaimLineItem> claimLines, Map<UUID, Receipt> receiptByLine,
			PolicyConfigSnapshot snapshot) {
		Collection<ClaimStatus> excluded = ClaimStatus.NOT_COUNTED_FOR_POLICY;
		Map<UUID, ClaimLineItem> earlier = new LinkedHashMap<>();
		lines.findClaimantEarlierLines(claim.getClaimantId(), claim.getId(), excluded)
			.forEach(l -> earlier.put(l.getId(), l));
		Set<LocalDate> dates = claimLines.stream().map(ClaimLineItem::getExpenseDate).collect(Collectors.toSet());
		lines.findEarlierLinesOnDates(claim.getId(), dates, excluded).forEach(l -> earlier.putIfAbsent(l.getId(), l));
		Set<String> hashes = receiptByLine.values().stream().map(Receipt::getSha256).collect(Collectors.toSet());
		if (!hashes.isEmpty()) {
			lines.findEarlierLinesWithHashes(claim.getId(), hashes, excluded)
				.forEach(l -> earlier.putIfAbsent(l.getId(), l));
		}
		Map<UUID, Receipt> earlierReceipts = receipts.findByLineItemIdIn(earlier.keySet())
			.stream()
			.collect(Collectors.toMap(Receipt::getLineItemId, Function.identity()));
		Set<UUID> claimIds = new HashSet<>();
		earlier.values().forEach(l -> claimIds.add(l.getClaimId()));
		Map<UUID, Claim> earlierClaims = claims.findAllById(claimIds)
			.stream()
			.collect(Collectors.toMap(Claim::getId, Function.identity()));

		List<EarlierLine> earlierLines = earlier.values()
			.stream()
			.map(l -> {
				Claim c = earlierClaims.get(l.getClaimId());
				return new EarlierLine(c.getId().toString(), c.getClaimNumber(), c.getClaimantId().toString(),
						lineInput(l, earlierReceipts.get(l.getId())));
			})
			.toList();
		List<LineInput> inputs = claimLines.stream().map(l -> lineInput(l, receiptByLine.get(l.getId()))).toList();
		return new PolicyInput(claim.getId().toString(), claim.getClaimNumber(), claim.getClaimantId().toString(),
				inputs, earlierLines, snapshot);
	}

	private static LineInput lineInput(ClaimLineItem l, Receipt receipt) {
		return new LineInput(l.getId().toString(), l.getCategory(), l.getAmount(), l.getExpenseDate(), l.getVendor(),
				l.getReceiptNumber(), receipt == null ? null : receipt.getSha256(), l.getCity(), l.getCheckInDate(),
				l.getCheckOutDate());
	}

	private PolicyResultEntity entity(Claim claim, PolicyResult r) {
		PolicyResultEntity e = new PolicyResultEntity();
		e.setClaimId(claim.getId());
		e.setSubmissionNo(claim.getSubmissionNo());
		e.setLineItemId(UUID.fromString(r.lineItemId()));
		e.setRuleId(r.ruleId());
		e.setResultType(r.resultType());
		e.setConfigKey(r.configKey());
		e.setConfiguredLimit(r.configuredLimit() == null ? null : Money.scale(r.configuredLimit()));
		e.setActualValue(r.actualValue() == null ? null : truncate(r.actualValue(), 200));
		e.setReason(truncate(r.reason(), 2000));
		e.setDetails(r.details() == null || r.details().isEmpty() ? null : json.write(r.details()));
		return e;
	}

	private static Map<String, Object> evaluationSummary(PolicyEvaluation evaluation) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("configVersion", evaluation.configVersion());
		for (ResultType type : ResultType.values()) {
			summary.put(type.name(), evaluation.of(type).size());
		}
		summary.put("rules", evaluation.results().stream().map(PolicyResult::ruleId).distinct().sorted().toList());
		return summary;
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static List<UUID> ids(List<ClaimLineItem> items) {
		return items.stream().map(ClaimLineItem::getId).toList();
	}

}
