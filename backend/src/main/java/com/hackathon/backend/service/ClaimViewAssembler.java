package com.hackathon.backend.service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.hackathon.backend.chain.StepAuthorization;
import com.hackathon.backend.domain.ApprovalStep;
import com.hackathon.backend.domain.AuditAction;
import com.hackathon.backend.domain.AuditEvent;
import com.hackathon.backend.domain.Claim;
import com.hackathon.backend.domain.ClaimComment;
import com.hackathon.backend.domain.ClaimLineItem;
import com.hackathon.backend.domain.ClaimStatus;
import com.hackathon.backend.domain.Department;
import com.hackathon.backend.domain.ExpenseCategory;
import com.hackathon.backend.domain.PolicyResultEntity;
import com.hackathon.backend.domain.Receipt;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.ClaimDtos.AuditEventView;
import com.hackathon.backend.dto.ClaimDtos.ClaimDetail;
import com.hackathon.backend.dto.ClaimDtos.ClaimSummary;
import com.hackathon.backend.dto.ClaimDtos.CommentView;
import com.hackathon.backend.dto.ClaimDtos.LineItemView;
import com.hackathon.backend.dto.ClaimDtos.PersonRef;
import com.hackathon.backend.dto.ClaimDtos.PolicyResultView;
import com.hackathon.backend.dto.ClaimDtos.ReceiptView;
import com.hackathon.backend.dto.ClaimDtos.StepView;
import com.hackathon.backend.repository.ApprovalStepRepository;
import com.hackathon.backend.repository.AuditEventRepository;
import com.hackathon.backend.repository.ClaimCommentRepository;
import com.hackathon.backend.repository.ClaimLineItemRepository;
import com.hackathon.backend.repository.ClaimRepository;
import com.hackathon.backend.repository.DepartmentRepository;
import com.hackathon.backend.repository.PolicyResultRepository;
import com.hackathon.backend.repository.ReceiptRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.service.support.Json;
import com.hackathon.backend.service.support.Money;

/** Builds API responses for claims (architecture/SPEC.md §7.1). Read-only. */
@Component
public class ClaimViewAssembler {

	private final ClaimLineItemRepository lines;

	private final ReceiptRepository receipts;

	private final PolicyResultRepository results;

	private final ApprovalStepRepository steps;

	private final ClaimCommentRepository comments;

	private final AuditEventRepository auditEvents;

	private final UserRepository users;

	private final DepartmentRepository departments;

	private final ClaimRepository claims;

	private final ClaimAccessPolicy access;

	private final Json json;

	public ClaimViewAssembler(ClaimLineItemRepository lines, ReceiptRepository receipts,
			PolicyResultRepository results, ApprovalStepRepository steps, ClaimCommentRepository comments,
			AuditEventRepository auditEvents, UserRepository users, DepartmentRepository departments,
			ClaimRepository claims, ClaimAccessPolicy access, Json json) {
		this.lines = lines;
		this.receipts = receipts;
		this.results = results;
		this.steps = steps;
		this.comments = comments;
		this.auditEvents = auditEvents;
		this.users = users;
		this.departments = departments;
		this.claims = claims;
		this.access = access;
		this.json = json;
	}

	public ClaimSummary summary(Claim c, int lineCount) {
		return new ClaimSummary(c.getId(), c.getClaimNumber(), c.getStatus(), Money.format(c.getTotalAmount()),
				c.getCreatedAt(), c.getSubmittedAt(), c.getDecidedAt(), lineCount);
	}

	public ClaimDetail detail(Claim claim, User viewer) {
		List<ClaimLineItem> claimLines = lines.findByClaimIdOrderByLineNoAsc(claim.getId());
		Map<UUID, Receipt> receiptByLine = receipts
			.findByLineItemIdIn(claimLines.stream().map(ClaimLineItem::getId).toList())
			.stream()
			.collect(Collectors.toMap(Receipt::getLineItemId, Function.identity()));
		List<PolicyResultEntity> latestResults = claim.getSubmissionNo() == 0 ? List.of()
				: results.findByClaimIdAndSubmissionNo(claim.getId(), claim.getSubmissionNo());
		List<ApprovalStep> claimSteps = steps.findByClaimIdOrderBySubmissionNoAscStepOrderAsc(claim.getId());
		List<ClaimComment> claimComments = comments.findByClaimIdOrderByCreatedAtAsc(claim.getId());

		Set<UUID> people = new HashSet<>();
		people.add(claim.getClaimantId());
		claimSteps.forEach(s -> {
			people.add(s.getAssignedApproverId());
			people.add(s.getActedById());
			people.add(s.getOnBehalfOfId());
		});
		claimComments.forEach(c -> people.add(c.getAuthorId()));
		people.remove(null);
		Map<UUID, User> userMap = userMap(people);
		User claimant = userMap.get(claim.getClaimantId());

		List<LineItemView> lineViews = claimLines.stream()
			.map(l -> lineView(l, receiptByLine.get(l.getId()),
					latestResults.stream().filter(r -> l.getId().equals(r.getLineItemId())).toList()))
			.toList();
		List<StepView> stepViews = claimSteps.stream()
			.map(s -> new StepView(s.getId(), s.getSubmissionNo(), s.getStepOrder(), s.getStepType().name(),
					s.getStatus().name(), ref(userMap.get(s.getAssignedApproverId())),
					s.getSkipReason() == null ? null : s.getSkipReason().name(), ref(userMap.get(s.getActedById())),
					ref(userMap.get(s.getOnBehalfOfId())), s.getActivatedAt(), s.getActedAt()))
			.toList();
		List<CommentView> commentViews = claimComments.stream()
			.map(c -> new CommentView(c.getId(), ref(userMap.get(c.getAuthorId())), c.getBody(), c.getCreatedAt()))
			.toList();

		String decisionReason = null;
		String sendBackComment = null;
		for (AuditEvent e : auditEvents.findByClaimIdOrderByIdAsc(claim.getId(), PageRequest.of(0, 1000))) {
			if (e.getAction() == AuditAction.CLAIM_AUTO_REJECTED || e.getAction() == AuditAction.CLAIM_REJECTED) {
				decisionReason = e.getComment();
			}
			if (e.getAction() == AuditAction.CLAIM_SENT_BACK) {
				sendBackComment = e.getComment();
			}
			if (e.getAction() == AuditAction.CLAIM_SUBMITTED) {
				decisionReason = null;
			}
		}
		Department dept = claimant == null ? null : departments.findById(claimant.getDepartmentId()).orElse(null);
		return new ClaimDetail(claim.getId(), claim.getClaimNumber(), claim.getStatus(),
				Money.format(claim.getTotalAmount()), ref(claimant), dept == null ? null : dept.getName(),
				claim.getCreatedAt(), claim.getUpdatedAt(), claim.getSubmittedAt(), claim.getDecidedAt(),
				claim.getSubmissionNo(), claim.getConfigVersion(), decisionReason, sendBackComment, lineViews,
				stepViews, commentViews, allowedActions(claim, viewer));
	}

	/** Actions the viewer may take now (architecture/SPEC.md §15); the backend still checks each one. */
	public List<String> allowedActions(Claim claim, User viewer) {
		Set<String> actions = new LinkedHashSet<>();
		boolean claimant = claim.getClaimantId().equals(viewer.getId());
		if (claimant && ClaimStatus.EDITABLE.contains(claim.getStatus())) {
			actions.add("EDIT");
			actions.add("SUBMIT");
		}
		boolean canAct = access.authorizeCurrent(viewer, claim).map(StepAuthorization::isAllowed).orElse(false);
		if (canAct) {
			actions.add("APPROVE");
			actions.add("REJECT");
			if (claim.getStatus().isPendingChain()) {
				actions.add("SEND_BACK");
			}
			actions.add("COMMENT");
		}
		else if (!claimant && access.hasActed(viewer, claim)) {
			actions.add("COMMENT");
		}
		return List.copyOf(actions);
	}

	public LineItemView lineView(ClaimLineItem l, Receipt receipt, List<PolicyResultEntity> lineResults) {
		Integer nights = l.getCategory() == ExpenseCategory.HOTEL && l.getCheckInDate() != null
				&& l.getCheckOutDate() != null ? (int) ChronoUnit.DAYS.between(l.getCheckInDate(), l.getCheckOutDate())
						: null;
		return new LineItemView(l.getId(), l.getLineNo(), l.getCategory(), Money.format(l.getAmount()),
				l.getExpenseDate(), l.getVendor(), l.getReceiptNumber(), l.getCity(), l.getCheckInDate(),
				l.getCheckOutDate(), nights,
				receipt == null ? null
						: new ReceiptView(receipt.getId(), receipt.getContentType(), receipt.getSizeBytes(),
								receipt.getCreatedAt()),
				lineResults.stream().map(this::resultView).toList());
	}

	@SuppressWarnings("unchecked")
	public PolicyResultView resultView(PolicyResultEntity r) {
		Object details = json.readObject(r.getDetails());
		return new PolicyResultView(r.getRuleId(), r.getResultType().name(), r.getConfigKey(),
				Money.format(r.getConfiguredLimit()), r.getActualValue(), r.getReason(),
				details instanceof Map<?, ?> m ? (Map<String, Object>) m : null);
	}

	public List<AuditEventView> auditViews(Collection<AuditEvent> events) {
		Set<UUID> people = new HashSet<>();
		Set<UUID> claimIds = new HashSet<>();
		events.forEach(e -> {
			people.add(e.getActorId());
			people.add(e.getOnBehalfOfId());
			claimIds.add(e.getClaimId());
		});
		people.remove(null);
		claimIds.remove(null);
		Map<UUID, User> userMap = userMap(people);
		Map<UUID, String> numbers = claims.findAllById(claimIds)
			.stream()
			.collect(Collectors.toMap(Claim::getId, Claim::getClaimNumber));
		List<AuditEventView> result = new ArrayList<>();
		for (AuditEvent e : events) {
			result.add(new AuditEventView(e.getId(), e.getOccurredAt(), e.getAction().name(),
					ref(userMap.get(e.getActorId())), ref(userMap.get(e.getOnBehalfOfId())), e.getEntityType().name(),
					e.getEntityId(), e.getClaimId(), numbers.get(e.getClaimId()),
					e.getFromState() == null ? null : e.getFromState().name(),
					e.getToState() == null ? null : e.getToState().name(), e.getComment(),
					json.readObject(e.getDetails())));
		}
		return result;
	}

	public Map<UUID, User> userMap(Collection<UUID> ids) {
		return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
	}

	public static PersonRef ref(User u) {
		return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getEmployeeCode());
	}

}
