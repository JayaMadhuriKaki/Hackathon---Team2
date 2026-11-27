# ExpenseFlow — System Specification (backbone)

| Field | Value |
|---|---|
| Status | Draft v0.3 — for team review before any code |
| Date | 2026-11-27 |
| Sources | `CLAUDE.md` (§2 requirements, §2.6 clarifications CL-01–CL-18, §2.7 routing, §2.8 payout, §4 domain, §5 bypass review, §6 open questions and assumptions, §8 stack), `requirements/requirements.docx` |
| Child specs | `backend/SPEC.md`, `frontend/SPEC.md`, `database/SPEC.md`, `tests/SPEC.md`, `ops/SPEC.md` |
| Changes | v0.2 resolved the child specs' issues against v0.1. v0.3 adds the follow-up decisions in §15 |

This file is the **single source of shared names and cross-cutting decisions**: roles, claim states, step statuses, entities, display IDs, API IDs, conventions, error codes, audit actions and metric names. Every child spec uses these names exactly. A child spec that needs a new name adds it here first.

**Rule:** nothing here goes beyond `CLAUDE.md` §2, an answered §6 question or a logged assumption. Proposed defaults are marked **(proposed, ASM-S-##)** and listed in §12 for the owner to confirm. Business-critical gaps are `SPEC-Q-##` in §11 and block the dependent work.

---

## 1. Scope

**In scope:** FR-001–FR-020, POL-001–POL-009, DATA-001–DATA-005, NFR-001–NFR-009, CL-01–CL-18.

**Out of scope:** anything not in `CLAUDE.md` §2 — e.g. notifications, OCR, claim-splitting detection (Q-06 open), public holidays (Q-13 open), user-management screens (Q-20 open).

## 2. Actors, roles and capabilities

| Role (enum `user_role`) | Who | Source |
|---|---|---|
| `EMPLOYEE` | Default role. Creates, saves and submits own claims | FR-001–003 |
| `FINANCE_AUDITOR` | Works the review queue (auditor steps and sample audits); can be a delegate | FR-005, FR-009–011, CL-13, CL-14 |
| `FINANCE_HEAD` | Approves the finance-head step; **also has every `ADMIN` capability** | FR-005, CL-10, §2.8 |
| `ADMIN` | Policy config, payout batches, full audit trail. **Never approves chain steps.** No seeded user holds it (proposed, ASM-S-01) | CL-10 |

Each user has exactly one role. Every user is also a claimant and sees their own claims (CL-15).

**Derived capabilities** (returned by API-003 so the UI never guesses):

| Capability | True when |
|---|---|
| `isManager` | At least one user has `manager_id` = this user (DATA-002) |
| `isApprover` | `isManager`, or role is `FINANCE_AUDITOR` or `FINANCE_HEAD`, or the user is an active delegate |
| `canDelegate` | `isManager` (delegation only covers individual manager steps — §5.4; pool roles need none) |
| `isAdmin` | Role is `FINANCE_HEAD` or `ADMIN` |

## 3. Claim lifecycle

### 3.1 States (enum `claim_status`)

| State | Meaning | Persisted? |
|---|---|---|
| `DRAFT` | Being edited by the claimant | Yes |
| `SUBMITTED` | Transient while the submit pipeline runs | Audit entry only |
| `AUTO_APPROVED` | Transient: passed policy, ≤ 25,000, no flags | Audit entry only |
| `AUTO_REJECTED` | A line broke a limit (CL-07). Terminal | Yes |
| `PENDING_MANAGER` | Waiting for the line manager or their delegate | Yes |
| `PENDING_AUDITOR` | Waiting for any finance auditor (pool) | Yes |
| `PENDING_FINANCE_HEAD` | Waiting for any finance head (pool) | Yes |
| `SENT_BACK` | Returned to the claimant; editable | Yes |
| `REJECTED` | Rejected by an approver or sample auditor with a comment (CL-04, CL-14). Terminal | Yes |
| `SAMPLE_AUDIT_PENDING` | Auto-approved, selected in the 10% sample; held from payout (CL-14) | Yes |
| `APPROVED` | Eligible for a payout batch | Yes |
| `IN_PAYOUT_BATCH` | In a batch not yet exported | Yes |
| `PAID` | Batch exported (CL-06). Terminal | Yes |

### 3.2 Transitions

Every transition is enforced by the backend, checks the actor, and writes `audit_events` rows in the same transaction (CLAUDE.md §4.1). Anything not listed → `409 STATE_CONFLICT`.

| # | From | To | Actor | Guard | Audit action | Source |
|---|---|---|---|---|---|---|
| T-01 | — | `DRAFT` | Claimant | — | `CLAIM_CREATED` | FR-001 |
| T-02 | `DRAFT`, `SENT_BACK` | `SUBMITTED` | Claimant | ≥ 1 line; every line > 500 has a receipt (ASM-003) | `CLAIM_SUBMITTED` | FR-003 |
| T-03 | `SUBMITTED` | `AUTO_REJECTED` | System | Any line breaks POL-001..004 | `CLAIM_AUTO_REJECTED` | CL-07 |
| T-04 | `SUBMITTED` | first `PENDING_*` of the computed chain | System | Must route (§3.3 step 5.2) | `CLAIM_ROUTED` | CL-02, CL-16, ASM-004 |
| T-05 | `SUBMITTED` | `AUTO_APPROVED` | System | Otherwise | `CLAIM_AUTO_APPROVED` | CL-02 |
| T-06 | `AUTO_APPROVED` | `SAMPLE_AUDIT_PENDING` | System | Selected by the sample draw | `SAMPLE_AUDIT_SELECTED` | FR-011, CL-14 |
| T-07 | `AUTO_APPROVED` | `APPROVED` | System | Not selected | `SAMPLE_AUDIT_NOT_SELECTED` | CL-02 |
| T-08 | `PENDING_*` | next `PENDING_*` | Current-step approver | Not the claimant; a later active step exists | `STEP_APPROVED` | FR-005 |
| T-09 | `PENDING_*` | `APPROVED` | Current-step approver | Not the claimant; last step | `STEP_APPROVED`, `CLAIM_APPROVED` | FR-005 |
| T-10 | `PENDING_*` | `REJECTED` | Current-step approver | Comment present | `CLAIM_REJECTED` | CL-04 |
| T-11 | `PENDING_*` | `SENT_BACK` | Current-step approver | Comment present (SPEC-Q-03) | `CLAIM_SENT_BACK` | FR-007 |
| T-12 | `SAMPLE_AUDIT_PENDING` | `APPROVED` | Finance auditor, not the claimant | — | `CLAIM_APPROVED` | CL-14 |
| T-13 | `SAMPLE_AUDIT_PENDING` | `REJECTED` | Finance auditor, not the claimant | Comment present | `CLAIM_REJECTED` | CL-14 |
| T-14 | `APPROVED` | `IN_PAYOUT_BATCH` | `isAdmin` | Claim in no batch | `CLAIM_ADDED_TO_BATCH` | FR-012, B-09 |
| T-15 | `IN_PAYOUT_BATCH` | `PAID` | `isAdmin` | Batch export, one transaction | `CLAIM_PAID` | FR-013–014, CL-06 |

"Current-step approver" is defined in §5.3. Resubmission (T-02 from `SENT_BACK`) **restarts** the claim: policy re-runs, the chain is recalculated, old steps become `SUPERSEDED` (CL-12, B-05).

### 3.3 Submit pipeline (one transaction, claim row locked)

1. Check state is `DRAFT` or `SENT_BACK` and the caller is the claimant.
2. Check the claim has ≥ 1 line (`422 CLAIM_HAS_NO_LINES`) and every line > `receipt.mandatory_above` has a receipt (`422 RECEIPT_REQUIRED`, per-line details). State unchanged (ASM-003).
3. Record the current policy config version on the claim (B-08).
4. Run the **policy engine** on every line → `policy_results` rows.
5. **Evaluate** (CLAUDE.md §2.7, fixed order):
   1. any `LIMIT_BREACH` → T-03;
   2. else if total > `approval.auditor_threshold`, any `WEEKEND_FLAG`, any `DUPLICATE_FLAG`, **or the claimant is a finance head** (SPEC-Q-14) → compute the chain (§5) → T-04;
   3. else → T-05 → sample draw → T-06 or T-07.
6. Audit entries for every state passed through. `details` holds the evaluation summary.

## 4. Policy engine (policy as configuration)

A **pure module**: input = claim lines + the earlier lines it needs for duplicate and meal checks + the config values; output = results. No database or HTTP inside rule code; JUnit 5 tests are written first (CL-18).

### 4.1 Configuration keys

| Key | Type | Seed value | Used by |
|---|---|---|---|
| `meals.max_per_day` | money > 0 | 1500.00 | POL-001 |
| `hotel.max_per_night.tier1` | money > 0 | 6000.00 | POL-002 |
| `hotel.tier1_cities` | non-empty list of strings | Kolkata, Mumbai, Delhi, Chennai, Bangalore, plus aliases Bengaluru, New Delhi (ASM-006) | POL-002 |
| `hotel.max_per_night.other` | money > 0 | 4000.00 | POL-003 |
| `local_cab.max_per_trip` | money > 0 | 2000.00 | POL-004 |
| `receipt.mandatory_above` | money ≥ 0 | 500.00 | POL-005 |
| `duplicate.match_fields` | non-empty list | vendor, amount, expenseDate | POL-006 |
| `duplicate.match_receipt_hash` | boolean | true | POL-007 |
| `weekend.flag_for_manager` | boolean | true | POL-008 |
| `duplicate.match_date_receipt_no` | boolean | true | POL-009 |
| `approval.auditor_threshold` | money > 0 | 25000.00 | Routing, chain |
| `approval.finance_head_threshold` | money > `approval.auditor_threshold` | 100000.00 | Chain |
| `sample_audit.rate` | decimal, 0 < x ≤ 1 | 0.10 | FR-011 |

**Versioning:** `policy_rules` is insert-only; each change inserts a new row with the next global `config_version`. A claim stores the `config_version` used at submit. API-035 validates the type and range above (`400 VALIDATION_FAILED`).

### 4.2 Rule outcomes

| Rule | Result type | Effect | Notes |
|---|---|---|---|
| POL-001 | `LIMIT_BREACH` | Auto-reject claim (CL-07) | Aggregation scope: SPEC-Q-04 |
| POL-002/003 | `LIMIT_BREACH` | Auto-reject claim | Breach when amount > limit × nights; nights = check-out − check-in ≥ 1 (ASM-005). City match: trimmed, case-insensitive, aliases (ASM-006) |
| POL-004 | `LIMIT_BREACH` | Auto-reject claim | Per line = per trip |
| POL-005 | validation | Blocks submit (ASM-003) | Never stored as a result |
| POL-006/007/009 | `DUPLICATE_FLAG` | Route, flag shown, never auto-approved (ASM-004) | Matching rules §4.3 |
| POL-008 | `WEEKEND_FLAG` | Route to the chain (CL-02) | Saturday/Sunday of `expenseDate` (ASM-001) |

Each result stores rule ID, config key, configured limit, actual value (for duplicates: the matching claim number and line), and a readable reason.

### 4.3 Duplicate matching (proposed, ASM-S-02; scope is SPEC-Q-05)

| Rule | Compared against | Normalisation |
|---|---|---|
| POL-006 vendor + amount + date | The claimant's earlier lines | Vendor trimmed, inner spaces collapsed, case-insensitive; amount exact |
| POL-007 receipt hash | Every employee's earlier lines | SHA-256 of file content |
| POL-009 date + receipt number | Every employee's earlier lines | Receipt number trimmed, upper-cased |

"Earlier lines" = lines in other claims whose state is not `DRAFT`, `AUTO_REJECTED` or `REJECTED`, plus earlier lines in the same claim.

### 4.4 Line-item fields (enum `expense_category`: `MEALS`, `HOTEL`, `LOCAL_CAB` — others: SPEC-Q-02)

| Field | Rule | Source |
|---|---|---|
| `category` | Required, enum | FR-001 |
| `amount` | Required, > 0, 2 decimals | FR-001 |
| `expenseDate` | Required, not in the future. For hotel = `checkInDate` (proposed, ASM-S-03) | FR-001 |
| `vendor` | Required, 1–120 chars | POL-006 |
| `receiptNumber` | Required, 1–60 chars (SPEC-Q-15 for lines without a receipt) | CL-05 |
| `city`, `checkInDate`, `checkOutDate` | Required for `HOTEL` only; check-out > check-in | CL-08, ASM-005 |
| receipt file | Uploaded separately (API-010); required at submit when amount > 500 | FR-001, POL-005 |

Field errors on add/update → `400 VALIDATION_FAILED`. Receipt and "no lines" checks happen at submit → `422`.

## 5. Approval-chain engine

A second **pure module** (CLAUDE.md §9.2), modelled as data (ADR-001).

### 5.1 Levels

| Level | Step type | Required by amount when | Approver |
|---|---|---|---|
| 1 | `MANAGER` | Always, once routed | The claimant's `manager_id` (individual) |
| 2 | `FINANCE_AUDITOR` | total > `approval.auditor_threshold` | Pool: any `FINANCE_AUDITOR` |
| 3 | `FINANCE_HEAD` | total > `approval.finance_head_threshold` | Pool: any `FINANCE_HEAD` |

### 5.2 Computing the steps at submit

1. **Finance head is the claimant** → exactly one `FINANCE_AUDITOR` step, whatever the amount (CL-16). Stop.
2. Otherwise start with the levels required by amount (level 1 always).
3. Walk the levels in order. A step is `SKIPPED` when it has **no approver** (no manager — CL-09) or **the claimant would approve it** (claimant holds that pool role — ASM-009). A skipped step **moves the claim to the next level, adding that level if the amount did not require it** (CL-09 "moves to the next level"; CLAUDE.md §4.1 `Submitted → PendingAuditor`).
   - Example: a finance auditor's own claim of 30,000 → manager step, auditor step skipped → finance head step added (ASM-009).
   - Example: an employee with no manager, weekend line, total 3,000 → manager skipped → auditor step added.
4. The chain can never end up empty, because level 3 is always reachable (at least one finance head exists — DATA seed rule).

Steps are stored as `approval_steps` rows; config changes never re-route a claim in flight (CLAUDE.md §4.2).

### 5.3 Step status (enum `step_status`) and the current-step approver

| Status | Meaning |
|---|---|
| `WAITING` | Later step, not yet active |
| `PENDING` | Active step; `activated_at` is set (used for approval age) |
| `APPROVED` / `REJECTED` / `SENT_BACK` | Decision recorded with `acted_by` and `on_behalf_of` |
| `SKIPPED` | Skipped by §5.2 rule 3; `skip_reason` = `NO_APPROVER` or `CLAIMANT_IS_APPROVER` |
| `SUPERSEDED` | Belonged to a submission replaced by a resubmission (CL-12) |

**Who may act on the `PENDING` step:**
- `MANAGER` step: the assigned manager, or their active delegate (§5.4).
- `FINANCE_AUDITOR` / `FINANCE_HEAD` step: any user with that role, except the claimant.
- Nobody may act on their own claim (B-02). The claimant trying to approve, reject or send back their own claim (including a sample audit) → `422 SELF_APPROVAL_FORBIDDEN`; this check comes first. Any other wrong actor on a visible claim → `403 NOT_CURRENT_APPROVER`.

### 5.4 Delegation

- Created by a user with `canDelegate`. Start and end dates are **inclusive, in IST** (ASM-002, ASM-007); one active delegation per delegator; overlap → `422 DELEGATION_OVERLAP`.
- Delegate must hold `FINANCE_AUDITOR` or `FINANCE_HEAD` (CL-13), must not be the delegator, and cannot pass it on (B-03) → otherwise `422 INVALID_DELEGATE`.
- Delegation covers the delegator's **individual** steps (manager steps). Pool steps need no delegation. The delegator may still act during the window (proposed, ASM-S-04).
- Audit entries record `actor_id` and `on_behalf_of_id`.

### 5.5 Sample audit

- Each auto-approved claim is selected with probability `sample_audit.rate` (a per-claim draw — proposed, ASM-S-05) using `SecureRandom`. The drawn value is stored in the audit entry `details` (B-12). Tests inject a fixed random source; production never uses a known seed.
- Only `FINANCE_AUDITOR` users review sample audits (CL-14). Sampled claims cannot enter a batch until reviewed.

## 6. Entities (PostgreSQL tables, snake_case plural)

Columns, constraints and indexes: `database/SPEC.md`.

| Table | Purpose | Notes |
|---|---|---|
| `departments` | 8 departments | DATA-001 |
| `users` | `employee_code` (`EMP0001`), name, email, role, `manager_id`, department, synthetic bank account + IFSC, password hash | DATA-001/002/005, §2.8 |
| `claims` | `claim_number` (`CLM-YYYY-NNNNN`, set at creation), claimant, status, total, `config_version`, `sample_audit_selected`, `sample_audit_selected_at`, `payout_batch_id` (at most one — B-09), timestamps | FR-001–014 |
| `claim_line_items` | Fields of §4.4 | FR-001, CL-05, CL-08 |
| `receipts` | Generated storage name, content type, size, SHA-256 | POL-007, §13.5 |
| `policy_rules` | Insert-only config rows with `config_version` | CL-10, B-08 |
| `policy_results` | One row per fired rule per line per submission | FR-004, FR-016 |
| `approval_steps` | Level, step type, assigned approver (manager steps), status, `activated_at`, `acted_by`, `on_behalf_of`, submission number | FR-005, CL-09, CL-12, CL-16 |
| `claim_comments` | Approver comments, and the mandatory comments of reject and send-back | FR-006, FR-007, CL-04 |
| `delegations` | Delegator, delegate, start, end, cancelled | FR-008, CL-13 |
| `payout_batches` | `batch_number` (`PB-YYYY-NNNN`), status (`CREATED`, `EXPORTED`), created/exported by and at, row count, total, file SHA-256, stored file path | FR-012–014, §2.8 |
| `idempotency_keys` | Key, user, operation, request hash, stored response, created at (kept 24 h) | §10.5 |
| `audit_events` | Append-only: `actor_id`, `on_behalf_of_id`, action, `entity_type`, `entity_id`, `claim_id`, from/to state, comment, `details` JSONB, timestamp, trace ID | FR-018, NFR-004 |

`entity_type` values: `CLAIM`, `LINE_ITEM`, `DELEGATION`, `PAYOUT_BATCH`, `POLICY_RULE`, `USER`.

**Exported payout files** are stored once (exports volume) and re-downloads return the same bytes and SHA-256 (§2.8).

**Database roles (NFR-004, §13.1):** `expenseflow_owner` runs Flyway migrations and the seed; `expenseflow_app` is the runtime user with no DDL rights and only `SELECT`/`INSERT` on `audit_events`. A trigger also rejects `UPDATE`/`DELETE`/`TRUNCATE` on `audit_events` for every role.

## 7. API catalogue

Contract: `architecture/api-contracts.yaml`. Base path `/api/v1`, Bearer JWT unless public. IDs are fixed; `api-inventory.xlsx` must match.

| ID | Method | Path | Who | Requirements |
|---|---|---|---|---|
| API-001 | GET | `/health` | Public | NFR-006 |
| API-002 | POST | `/auth/login` | Public | Q-22 / SPEC-Q-07 |
| API-003 | GET | `/me` | Any | §2 capabilities |
| API-004 | GET | `/claims` | Any — own claims only | FR-019, CL-15 |
| API-005 | POST | `/claims` | Any | FR-001, FR-002 |
| API-006 | GET | `/claims/{claimId}` | Claim visibility rule (below) | FR-019, CL-15, ASM-008 |
| API-007 | POST | `/claims/{claimId}/line-items` | Claimant; `DRAFT`/`SENT_BACK` | FR-001 |
| API-008 | PUT | `/claims/{claimId}/line-items/{lineItemId}` | Claimant; `DRAFT`/`SENT_BACK` | FR-001, B-04 |
| API-009 | DELETE | `/claims/{claimId}/line-items/{lineItemId}` | Claimant; `DRAFT`/`SENT_BACK` | FR-001 |
| API-010 | POST | `/claims/{claimId}/line-items/{lineItemId}/receipt` | Claimant; `DRAFT`/`SENT_BACK` | FR-001, POL-007 |
| API-011 | GET | `/claims/{claimId}/line-items/{lineItemId}/receipt` | Claim visibility rule | NFR-002 |
| API-012 | POST | `/claims/{claimId}/submit` | Claimant | FR-003, FR-004, CL-12 |
| API-013 | GET | `/claims/{claimId}/audit-events` | Claim visibility rule | FR-018 |
| API-014 | GET | `/approvals` | Any (empty unless `isApprover`) | FR-005, FR-008 |
| API-015 | POST | `/claims/{claimId}/approve` | Current-step approver; finance auditor for sample audits | FR-005, CL-14 |
| API-016 | POST | `/claims/{claimId}/reject` | Same as API-015 | FR-020, CL-04, CL-14 |
| API-017 | POST | `/claims/{claimId}/send-back` | Current-step approver | FR-007 |
| API-018 | POST | `/claims/{claimId}/comments` | Current-step approver, or a user who acted on the claim | FR-006 |
| API-019 | GET | `/delegations` | `canDelegate` (else 403) | FR-008 |
| API-020 | POST | `/delegations` | `canDelegate` | FR-008, CL-13 |
| API-021 | DELETE | `/delegations/{delegationId}` | The delegator | FR-008 |
| API-022 | GET | `/review-queue` | `FINANCE_AUDITOR` | FR-009, CL-14 |
| API-023 | POST | `/review-queue/bulk-approve` | `FINANCE_AUDITOR` | FR-010 |
| API-024 | GET | `/payout-batches` | `isAdmin` | FR-012 |
| API-025 | GET | `/payout-batches/eligible-claims` | `isAdmin` | FR-012, CL-14 |
| API-026 | POST | `/payout-batches` | `isAdmin` | FR-012 |
| API-027 | GET | `/payout-batches/{batchId}` | `isAdmin` | FR-012 |
| API-028 | POST | `/payout-batches/{batchId}/export` | `isAdmin` | FR-013, FR-014, CL-06 |
| API-029 | GET | `/payout-batches/{batchId}/file` | `isAdmin`; only after export (else 409) | FR-013, §2.8 |
| API-030 | GET | `/dashboard/spend` | `FINANCE_AUDITOR`, `isAdmin` | FR-015, ASM-010 |
| API-031 | GET | `/dashboard/policy-violations` | `FINANCE_AUDITOR`, `isAdmin` | FR-016, ASM-010 |
| API-032 | GET | `/dashboard/approval-time` | `FINANCE_AUDITOR`, `isAdmin` | FR-017, SPEC-Q-08 |
| API-033 | GET | `/audit-events` | `isAdmin` | FR-018, ASM-010 |
| API-034 | GET | `/policy-rules` | `isAdmin` | CL-10 |
| API-035 | PUT | `/policy-rules/{ruleKey}` | `isAdmin` | CL-10, B-08 |
| API-036 | GET | `/departments` | Any | Filters for FR-009, FR-015 |
| API-037 | GET | `/policy-rules/client-config` | Any | §10.3 client validation: `receiptMandatoryAbove`, `tier1Cities`, `categories` |
| API-038 | GET | `/delegations/eligible-delegates` | `canDelegate` | FR-008, CL-13 |

**Claim visibility rule (CL-15, ASM-008):** the claimant; anyone who may act on the current `PENDING` step (incl. delegates and pool-role holders, never the claimant); any finance auditor (except the claimant) for a `SAMPLE_AUDIT_PENDING` claim; anyone who has acted on the claim. **Not** the finance head or admin by role alone; they use API-033 for the full trail (ASM-010) and API-025/027 for payout data. Not visible → `404 NOT_FOUND`.

### 7.1 What API-006 returns

Claim header (`claimNumber`, status, total, claimant name and department, timestamps), lines with policy results and receipt metadata, approval steps (status, level, approver or pool, decision, `onBehalfOf`), comments (incl. the latest send-back comment shown to the claimant), and the actions allowed for the caller (`allowedActions`).

### 7.2 Queues

- **API-014 `/approvals`:** `PENDING` manager steps assigned to the caller or to someone they are the active delegate for, plus `PENDING_FINANCE_HEAD` claims for finance heads. Never the caller's own claims.
- **API-022 `/review-queue`:** `PENDING_AUDITOR` and `SAMPLE_AUDIT_PENDING` claims, never the caller's own. Filters (proposed, SPEC-Q-09): `departmentId`, `category`, `minAmount`, `maxAmount`, `fromDate`, `toDate`, `flag` (`DUPLICATE`, `WEEKEND`), `queue` (`AUDITOR_STEP`, `SAMPLE_AUDIT`).
- **API-023 bulk approve:** body `{ claimIds: [...] }`, max 100. Each claim is processed in its own transaction; response `200 { results: [{ claimId, outcome: "APPROVED" | "FAILED", code, message }] }`. Flagged claims are allowed (SPEC-Q-09).

### 7.3 Dashboard (proposed, SPEC-Q-11)

- **Spend:** sum of line amounts of claims in `APPROVED`, `IN_PAYOUT_BATCH`, `PAID`, grouped by `category`, claimant department, or month of `expenseDate`.
- **Policy violations:** count of `policy_results` by rule ID from each claim's latest submission.
- **Approval time:** submit → final approval, calendar time (SPEC-Q-08).
- Filters: `fromDate`, `toDate` (expense date), `departmentId`.

## 8. Conventions

| Topic | Rule |
|---|---|
| Lists | Query `page` (1-based), `pageSize` (default 20, max 100), `sort` (`field` or `-field`). Response `{ items, page, pageSize, total }` |
| Money in JSON | String with exactly 2 decimals, e.g. `"27450.00"`. DB `NUMERIC(12,2)`, Java `BigDecimal`. UI shows ₹1,00,000.00 |
| Dates / times | Dates `YYYY-MM-DD`; timestamps ISO-8601 UTC; UI shows IST (ASM-002) |
| Idempotency | `Idempotency-Key` header on API-012, 015, 016, 017, 023, 026, 028. Same key + same body → stored response replayed. Same key + different body → `422 IDEMPOTENCY_KEY_REUSED`. Kept 24 h |
| Trace | `X-Trace-Id` on every response, in logs and error bodies |
| Health | API-001 `/api/v1/health` = app + database check for the UI. `/actuator/health` = container healthcheck. `/actuator/prometheus` = metrics scrape |
| Unknown JSON fields | Rejected with `400 VALIDATION_FAILED` (CLAUDE.md §11.1) |
| Payout CSV | CLAUDE.md §2.8. Narration is cut to 30 characters (the §2.8 example is 36 — see §13 owner notes) |

## 9. Error codes

Body `{ "code", "message", "details", "traceId" }` (CLAUDE.md §11.4).

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Malformed body, wrong types, unknown fields, field rules; `details.fields` |
| 401 | `UNAUTHENTICATED` | Missing or invalid token |
| 403 | `FORBIDDEN` | Role not allowed for the operation |
| 403 | `NOT_CURRENT_APPROVER` | Claim is visible but the caller cannot act on its current step |
| 404 | `NOT_FOUND` | Not found **or not visible** (CL-15, B-10) |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method |
| 409 | `STATE_CONFLICT` | Transition not allowed from the current state |
| 413 | `FILE_TOO_LARGE` | Receipt above the limit (SPEC-Q-06) |
| 415 | `UNSUPPORTED_FILE_TYPE` | Receipt type not allowed, checked by content |
| 422 | `RECEIPT_REQUIRED`, `CLAIM_HAS_NO_LINES`, `COMMENT_REQUIRED`, `SELF_APPROVAL_FORBIDDEN`, `INVALID_DELEGATE`, `DELEGATION_OVERLAP`, `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT`, `IDEMPOTENCY_KEY_REUSED`, `BUSINESS_RULE_VIOLATION` | Business-rule failures |
| 429 | `RATE_LIMITED` | Login or upload rate limit (SPEC-Q-13) |
| 500 | `INTERNAL_ERROR` | Unexpected; no stack trace |

## 10. Audit actions and metrics

**Audit actions:** `CLAIM_CREATED`, `LINE_ADDED`, `LINE_UPDATED`, `LINE_DELETED`, `RECEIPT_UPLOADED`, `CLAIM_SUBMITTED`, `POLICY_EVALUATED`, `CLAIM_AUTO_REJECTED`, `CLAIM_ROUTED`, `CLAIM_AUTO_APPROVED`, `SAMPLE_AUDIT_SELECTED`, `SAMPLE_AUDIT_NOT_SELECTED`, `STEP_ACTIVATED`, `STEP_SKIPPED`, `STEP_APPROVED`, `CLAIM_APPROVED`, `CLAIM_REJECTED`, `CLAIM_SENT_BACK`, `COMMENT_ADDED`, `DELEGATION_CREATED`, `DELEGATION_CANCELLED`, `PAYOUT_BATCH_CREATED`, `CLAIM_ADDED_TO_BATCH`, `PAYOUT_BATCH_EXPORTED`, `CLAIM_PAID`, `POLICY_RULE_CHANGED`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED`.

**Metrics** (built in Phase 4c with the backend; dashboard and alert in Phase 8):

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `expenseflow_approval_queue_size` | Gauge | `step` = `MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD`, `SAMPLE_AUDIT` | Claims waiting at each step; 0 when empty (series always present) |
| `expenseflow_oldest_pending_approval_seconds` | Gauge | same `step` values | Now − oldest `activated_at` of a `PENDING` step (or sample-audit selection time); 0 when empty |
| `http_server_requests_seconds` | Histogram (percentiles-histogram enabled) | Spring defaults | API latency. Page latency: SPEC-Q-12 |

## 11. Open questions

**Bold** = business-critical, blocks the dependent work (CLAUDE.md §3.4). Answers go to `CLAUDE.md` §6 and here.

| ID | Question | Blocks | Proposed default |
|---|---|---|---|
| ~~SPEC-Q-01~~ | ~~Who approves when every step is skipped?~~ | — | **Resolved in v0.2** by reading CL-09 as "next level, added if needed" (§5.2); the chain can no longer be empty |
| **SPEC-Q-02** | Expense categories besides meals, hotel, local cab? Limits for them? | Line items, policy engine, UI | Only `MEALS`, `HOTEL`, `LOCAL_CAB` |
| **SPEC-Q-03** | Is a comment mandatory on send-back? | API-017, UI | Yes |
| SPEC-Q-04 | Q-11: meals limit = sum of the claimant's meal lines on that date across claims? | POL-001 | Yes — the same "earlier lines" set as §4.3 (excludes `DRAFT`, `AUTO_REJECTED`, `REJECTED`) plus this claim's lines |
| **SPEC-Q-05** | Duplicate-check scope and matching | POL-006/007/009 | §4.3 |
| SPEC-Q-06 | Q-21: receipt types and size | API-010 | PDF, JPG, PNG; 5 MB |
| SPEC-Q-07 | Q-22: login method | API-002 | Seeded users, email + password, JWT |
| SPEC-Q-08 | Q-23: average approval time measure | API-032 | Calendar time submit → final approval |
| SPEC-Q-09 | Q-24: review-queue filters; bulk approve on flagged claims | API-022/023 | §7.2 |
| SPEC-Q-10 | Q-25: receipt to save a draft? Withdraw a submitted claim? | API-007/010 | Receipt only at submit; no withdrawal |
| SPEC-Q-11 | Dashboard "spend": which states, which date for month | API-030/031 | §7.3 |
| SPEC-Q-12 | NFR-008 "page latency": API latency enough, or measure browser page loads? | Ops metrics | API latency + page timings from Playwright/Locust runs |
| SPEC-Q-13 | Rate limits and JWT lifetime | API-002, API-010 | Login 5/min per IP; uploads 30/min per user; JWT 8 h |
| **SPEC-Q-14** | CL-16: does a finance head's **clean** claim ≤ 25,000 go to an auditor, or can it auto-approve? | Routing | Always to an auditor (never auto-approved) |
| SPEC-Q-15 | CL-05: is `receiptNumber` required on lines without a receipt (≤ 500)? | Line items | Required on every line |
| **SPEC-Q-16** | Finance auditors report to the finance head, so an auditor's own claim above 25,000 has the same finance head on the `MANAGER` step and the added `FINANCE_HEAD` step. May one person approve two levels? | Chain engine | Yes — each step is approved separately; no extra approver exists |

## 12. Proposed assumptions (to log in CLAUDE.md §6.1 once accepted)

| ID | Assumption |
|---|---|
| ASM-S-01 | `ADMIN` role exists for CL-10 but no seeded user holds it; the finance head acts as admin |
| ASM-S-02 | Duplicate matching per §4.3 |
| ASM-S-03 | A hotel line's `expenseDate` is its check-in date (used for the weekend flag) |
| ASM-S-04 | A delegator can still act on their own steps during the delegation |
| ASM-S-05 | "Random 10%" = each auto-approved claim has a 10% chance (not an exact share) |
| ASM-S-06 | Primary keys are UUIDv7; `audit_events` uses a BIGINT identity for strict order (`database/SPEC.md`) |

## 13. Notes for the project owner (CLAUDE.md wording; not changed by this spec)

- §16.3 says 403 for another employee's claim; CL-15 and this spec use **404** (B-10).
- §19 lists CL-01..CL-17; CL-18 also applies.
- §2.8 narration example (`Expense reimbursement CLM-2026-01534`) is 36 characters; the 30-character rule wins.
- The base backend tests run on H2, which is not in the §8.1 stack and cannot prove PostgreSQL permissions (NFR-004); pytest against the Docker stack covers that.
- Infrastructure decisions in child specs that need ADR-000: Flyway with migrations in `database/migrations/` included as a Maven resource (backend Docker build context moves to the repo root; `ddl-auto=validate`); `psycopg` for the seed; pinned Prometheus/Grafana image tags; ESLint + Prettier vs the base's `oxlint`.

## 14. Build order (CLAUDE.md §14.1)

1. Phase 0 — `PROGRESS.md`, environment check (§8.3).
2. Phase 2 — ADR-000 (stack + "Proposed" items + §13 infrastructure), ADR-001 (approval chain), diagrams, `architecture/security-review.md`.
3. Phase 3 — `api-contracts.yaml` for API-001..API-038, lint, `api-inventory.xlsx`.
4. Phase 4a — policy engine, **JUnit tests first**. 4b — chain + delegation. 4c — remaining backend + metrics.
5. Phase 5 — frontend. 6 — validation. 7 — deployment. 8 — SRE dashboard, alert, runbook. 9 — demo.

## 15. Follow-up decisions (v0.3)

These close the small gaps the child specs raised against v0.2. Child specs that already chose the same value need no change.

| Topic | Decision |
|---|---|
| `allowedActions` values (API-006) | `EDIT`, `SUBMIT`, `APPROVE`, `REJECT`, `SEND_BACK`, `COMMENT` |
| API-037 client config | Also returns `allowedReceiptTypes` and `maxReceiptBytes` (values per SPEC-Q-06) |
| API-019 delegations | Returns delegations the caller gave and received, each with `direction` = `GIVEN` or `RECEIVED` |
| API-033 audit filters | `claimNumber`, `actorId`, `action`, `entityType`, `fromDate`, `toDate`, plus paging |
| API-023 bulk approve | Covers `PENDING_AUDITOR` and `SAMPLE_AUDIT_PENDING` claims. Idempotency: the stored response is written after every claim is processed; a duplicate request that arrives while the first is still running → `409 STATE_CONFLICT` |
| API-026 create payout batch | All-or-nothing: any ineligible claim → `422 CLAIM_NOT_ELIGIBLE_FOR_PAYOUT`, `details.claimIds` lists them |
| `422 RECEIPT_REQUIRED` details | `{ "lines": [ { "lineItemId", "code", "message" } ] }` |
| "Not in the future" | Compared with today's date in IST (ASM-002) |
| Hotel policy result | Stored limit = per-night limit × nights; actual = line amount; `details` holds nights and the per-night limit |
| Sample draw | Selected when the draw is **less than** `sample_audit.rate`. HTTP and E2E tests accept either outcome; deterministic cases are JUnit-only |
| Approval time (SPEC-Q-08) | Measured from the latest submission |
| `LOGIN_FAILED` audit row | `actor_id` null; `entity_type` `USER` with the user's ID if the email exists, else null; `details.email` holds the email entered (synthetic data only) |
| Config / env names | `DB_OWNER_USERNAME`, `DB_OWNER_PASSWORD` (Flyway + seed), `DB_USERNAME`, `DB_PASSWORD` (app), `RECEIPTS_DIR`, `EXPORTS_DIR` |
| Volumes | `pgdata`, `receipts`, `exports`, `prometheusdata`, `grafanadata` |
| Demo reset | Only `docker compose down -v` then `up` — the audit trigger blocks `TRUNCATE`/`DELETE` by design |
| Seed personas | Include at least two ordinary employees in the same department so isolation tests do not reuse the no-manager persona (`database/SPEC.md` to add) |
| Page latency (SPEC-Q-12) | Until answered, the Grafana dashboard shows API latency only; page timings are reported from Playwright/Locust runs |
| ADR-000 additions | Flyway runs inside the backend with the owner credentials (alternative: separate one-shot migrate container); `pgcrypto` extension created by a migration (seed BCrypt hashes, `$2a$` accepted by Spring Security); exact image tags for Postgres 18.x, Temurin 21, Node 26.13.1, nginx, Python 3.13, Prometheus, Grafana |
