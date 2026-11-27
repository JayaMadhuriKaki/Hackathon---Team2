# ADR-001 — How approval chains are modelled

| Field | Value |
|---|---|
| Status | Accepted (design); implementation in Phase 4b |
| Date | 2026-11-27 |
| Sources | FR-005, FR-008, CL-03, CL-09, CL-12, CL-13, CL-16, ASM-009, SPEC-Q-14, SPEC-Q-16; `architecture/SPEC.md` §5 |

## Context

The source asks for an ADR on how approval chains are modelled. The chain depends on the claim total (manager → finance auditor above 25,000 → finance head above 1,00,000), on who the claimant is (CL-16, ASM-009), on missing approvers (CL-09) and on delegation (FR-008, CL-13). Approvers can send back, which restarts the chain (CL-12). A config change must not re-route claims already in flight (B-08).

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. Nested `if` statements in the approve endpoint | Quick to write | Untestable in isolation; thresholds hard-coded; easy to bypass a step (B-06); violates CLAUDE.md §4.2 |
| B. Recompute the next step from the claim on every action | No stored steps | A config change re-routes in-flight claims (B-08); no record of skipped steps |
| **C. Compute the whole chain at submit and store it as `approval_steps` rows** | Auditable; frozen against config changes; skips recorded with a reason; a pure, testable engine | One more table; resubmission must supersede old rows |

## Decision

**Option C.**

1. Levels are data: `MANAGER` (always), `FINANCE_AUDITOR` (total > `approval.auditor_threshold`), `FINANCE_HEAD` (total > `approval.finance_head_threshold`). Thresholds come from the claim's policy config version; comparisons are strictly greater than (CL-03).
2. A pure `ApprovalChainEngine` (no Spring, no database) computes the plan at submit:
   - Finance head claimant → exactly one `FINANCE_AUDITOR` step, whatever the amount; never auto-approved (CL-16, SPEC-Q-14).
   - A level with no approver (CL-09) or where the claimant would approve themselves (ASM-009) is `SKIPPED` with `NO_APPROVER` or `CLAIMANT_IS_APPROVER`, and the **next level is added** if the amount had not required it.
   - First remaining step `PENDING` (with `activated_at`), the rest `WAITING`.
3. The plan is stored as `approval_steps` rows with the submission number. Approving activates the next `WAITING` step; the last approval moves the claim to `APPROVED`.
4. Send-back and resubmission mark the old rows `SUPERSEDED` and compute a fresh plan from the new total (CL-12, B-05).
5. Manager steps are assigned to one person; finance-auditor and finance-head steps are pools (any holder of the role except the claimant). One finance head may approve both the manager step and the finance-head step of an auditor's claim, as separate approvals (SPEC-Q-16).
6. Delegation changes **who** may act on an individual (manager) step during an inclusive IST date window; it never changes which steps exist. Delegates must be finance auditors or finance heads (CL-13); one hop only (B-03).

## Consequences

- The engine is unit-tested with JUnit before implementation, covering every threshold boundary, skip path and the finance-head rule (CLAUDE.md §16.2).
- Queue size and oldest pending approval are read directly from `PENDING` rows and `activated_at` (NFR-008).
- Auditors can see in the claim history exactly which steps were required, skipped and by whom they were approved.
