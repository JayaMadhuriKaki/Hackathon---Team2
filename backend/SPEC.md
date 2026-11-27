# ExpenseFlow — Backend Specification

| Field | Value |
|---|---|
| Status | Draft v0.2 — for team review before any code |
| Date | 2026-11-27 |
| Parent spec | `architecture/SPEC.md` **v0.2** (backbone — all shared names come from there) |
| Sources | `CLAUDE.md` §2 (FR/POL/DATA/NFR), §2.6 CL-01–CL-18, §2.7 routing, §2.8 payout CSV, §4, §5, §6 (Q-##, ASM-###), §8, §9, §11–§13, §16 |
| Code base | `backend/` — Spring Boot 4.1.3, Java 21, Maven wrapper, package `com.hackathon.backend` |
| Changes in v0.2 | Aligned to backbone v0.2: skip-to-next-level chain rule, `step_status`, current-step approver, capabilities, visibility rule, API-037/038, conventions, new error codes and audit actions, `idempotency_keys`, display IDs, payout status + stored file, DB roles, SecureRandom sampling, metric labels, SPEC-Q-02..15, ASM-S-01..06. v0.1 issues I-01..I-19 resolved except those kept in §13 |

**Rule:** this spec adds no requirement. Anything undecided points to `SPEC-Q-##`, `Q-##`, `ASM-###` or `ASM-S-##`. Items marked **Proposed** need ADR-000 (CLAUDE.md §8.1) before code. Backbone gaps are listed in §13, not fixed here.

---

## 1. Scope

In scope: the Spring Boot service implementing API-001..API-038, the policy engine, the approval-chain engine, payout export, audit trail and SRE metrics. Out of scope: frontend, seed script and migrations content (`database/`), pytest suites (`tests/`), Prometheus/Grafana config (`ops/`).

### 1.1 Requirement coverage

| Requirement | Implemented in (section / class) |
|---|---|
| FR-001, FR-002 | §6.2 `ClaimService`, `LineItemService`, `ReceiptService` (API-005, 007–010) |
| FR-003, FR-004 | §6.3 `SubmitService` + `policy` engine (API-012) |
| FR-005, CL-03, CL-09, CL-16, ASM-009 | §5 `chain` engine, `ApprovalService` (API-015) |
| FR-006 | `CommentService` (API-018) |
| FR-007, CL-12 | `ApprovalService.sendBack` (API-017), `SubmitService` restart (`SUPERSEDED`) |
| FR-008, CL-13, ASM-007, ASM-S-04 | `DelegationService` (API-019–021, 038), `CurrentStepAuthorizer` |
| FR-009, FR-010 | `ReviewQueueService` (API-022, 023) |
| FR-011, CL-14, ASM-S-05 | `SampleAuditSelector` in `SubmitService`; sample-audit branch of API-015/016 |
| FR-012–FR-014, CL-06, CL-11, §2.8 | `PayoutService`, `PayoutCsvWriter`, `ExportFileStore` (API-024–029) |
| FR-015–FR-017 | `DashboardService` (API-030–032), backbone §7.3 |
| FR-018, NFR-004 | `AuditService` (insert-only), API-013, API-033; DB roles + trigger |
| FR-019, NFR-005, CL-15, ASM-008 | `ClaimAccessPolicy` (visibility rule, 404 when not visible) |
| FR-020, CL-04 | `ApprovalService.reject` (API-016) |
| POL-001–POL-009, CL-01, CL-05, CL-07, CL-08 | §4 `policy` engine |
| CL-02, §2.7, SPEC-Q-14 | `RoutingDecider` (§4.4) |
| CL-10 | `PolicyRuleService` (API-034, 035, 037) |
| DATA-005 | `users` bank fields read by `PayoutCsvWriter` |
| NFR-006 | API-001, `/actuator/health`, Flyway |
| NFR-008 | `ApprovalMetrics` (§7.10) |
| NFR-009, §13 | §7.5 receipts, §7.6 CSV, §7.7 security |
| CL-17, CL-18 | Stack per §8; JUnit 5 for engines (§10) |

---

## 2. Package structure (CLAUDE.md §9.2)

Root `com.hackathon.backend`. Layer packages, plus two **framework-free** engine packages.

| Package | Contents (main types) |
|---|---|
| `controller` | `HealthController`, `AuthController`, `MeController`, `ClaimController`, `LineItemController`, `ApprovalController`, `DelegationController`, `ReviewQueueController`, `PayoutBatchController`, `DashboardController`, `AuditEventController`, `PolicyRuleController`, `DepartmentController`. HTTP mapping only. |
| `service` | `ClaimService`, `LineItemService`, `ReceiptService`, `SubmitService`, `ApprovalService`, `CommentService`, `DelegationService`, `ReviewQueueService`, `PayoutService`, `PayoutCsvWriter`, `ExportFileStore`, `DashboardService`, `AuditService`, `PolicyRuleService`, `PolicyConfigLoader`, `SampleAuditSelector`, `ClaimAccessPolicy`, `CurrentStepAuthorizer`, `CapabilityService`, `DisplayIdGenerator`, `IdempotencyService`, `AuthService` |
| `repository` | Spring Data repositories, one per backbone §6 table. `AuditEventRepository extends Repository<AuditEvent, Long>` exposing only `save` and finders; `PolicyRuleRepository` likewise insert-only. |
| `domain` | Entities (`Department`, `User`, `Claim`, `ClaimLineItem`, `Receipt`, `PolicyRule`, `PolicyResultEntity`, `ApprovalStep`, `ClaimComment`, `Delegation`, `PayoutBatch`, `IdempotencyKey`, `AuditEvent`) and enums `UserRole`, `ClaimStatus`, `StepType`, `StepStatus`, `ExpenseCategory`, `PayoutBatchStatus`, `EntityType`, `AuditAction` |
| `dto` | Request/response records per API (camelCase), Bean Validation; money fields typed as `String` via a `Money` Jackson serializer (§3) |
| `config` | `WebConfig` (existing), `SecurityConfig`, `ClockConfig` (`Clock` bean), `RandomConfig` (`RandomSource` bean = `SecureRandom`), `EngineConfig`, `MetricsConfig`, `JacksonConfig` |
| `security` | `JwtService`, `JwtAuthenticationFilter`, `CurrentUser` (principal: userId, role), `RateLimiter` |
| `exception` | `ErrorCode` enum (backbone §9), `ApiException`, `ErrorResponse` record, `GlobalExceptionHandler`, `TraceIdFilter` |
| `policy` (pure) | `PolicyEngine`, `PolicyRule` (interface), 9 rule classes, `PolicyInput`, `LineInput`, `EarlierLine`, `PolicyConfigSnapshot`, `PolicyResult`, `ResultType`, `RoutingDecider`, `RoutingOutcome` |
| `chain` (pure) | `ApprovalChainEngine`, `Level`, `ChainInput`, `ChainPlan`, `PlannedStep`, `SkipReason`, `ApproverDirectory` (interface), `DelegationRules` |

Purity rule for `policy` and `chain`: no imports from `org.springframework`, `jakarta.*`, `java.sql`; inputs are records; time and randomness are parameters. `EnginePurityTest` (JUnit) scans these source folders and fails on such imports. Existing `common/` and `health/` packages move into this layout (§9).

---

## 3. Domain constants used by the backend

| Item | Values | Source |
|---|---|---|
| `UserRole` | `EMPLOYEE`, `FINANCE_AUDITOR`, `FINANCE_HEAD`, `ADMIN`; one role per user | backbone §2 |
| Capabilities | `isManager`, `isApprover`, `canDelegate`, `isAdmin` — computed by `CapabilityService` per backbone §2, used for every authorization check and returned by API-003 | backbone §2 |
| `ADMIN` | never approves chain steps or sample audits; no seeded user (ASM-S-01) | backbone §2 |
| `ClaimStatus` | the 13 values of backbone §3.1; `SUBMITTED`, `AUTO_APPROVED` never persisted on `claims` | backbone §3.1 |
| `StepType` | `MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD` | backbone §5.1 |
| `StepStatus` | `WAITING`, `PENDING`, `APPROVED`, `REJECTED`, `SENT_BACK`, `SKIPPED`, `SUPERSEDED` | backbone §5.3 |
| `ExpenseCategory` | `MEALS`, `HOTEL`, `LOCAL_CAB` (SPEC-Q-02) | backbone §4.4 |
| `PayoutBatchStatus` | `CREATED`, `EXPORTED` | backbone §6 |
| `EntityType` | `CLAIM`, `LINE_ITEM`, `DELEGATION`, `PAYOUT_BATCH`, `POLICY_RULE`, `USER` | backbone §6 |
| Display IDs | `employee_code` `EMP0001`; `claim_number` `CLM-YYYY-NNNNN` (set at creation); `batch_number` `PB-YYYY-NNNN`. `DisplayIdGenerator` uses a DB sequence per year | backbone §6 |
| Money | `BigDecimal` scale 2, `NUMERIC(12,2)`; JSON string with exactly 2 decimals (`"27450.00"`); never `double` | backbone §8 |
| Time | `Instant` UTC; `LocalDate` for expense/delegation dates; "today" for delegation = IST date (ASM-002); injected `Clock` | backbone §5.4, §8 |

---

## 4. Policy engine (`policy` package)

### 4.1 Interface

`PolicyEvaluation PolicyEngine.evaluate(PolicyInput input)`

| Type | Fields |
|---|---|
| `PolicyInput` | `claimId`, `claimNumber`, `claimantId`, `List<LineInput> lines` (in creation order), `List<EarlierLine> earlierLines`, `PolicyConfigSnapshot config` |
| `LineInput` | `lineItemId`, `category`, `amount`, `expenseDate`, `vendor`, `receiptNumber`, `receiptHash` (nullable), `city`, `checkInDate`, `checkOutDate`, `hasReceipt` |
| `EarlierLine` | `LineInput` fields + `claimId`, `claimNumber`, `claimantId` — loaded by `SubmitService` per the scope in §4.3 |
| `PolicyConfigSnapshot` | `configVersion` + typed values of every backbone §4.1 key |
| `PolicyResult` | `lineItemId`, `ruleId` (`POL-###`), `resultType`, `configKey`, `configuredLimit`, `actualValue` (duplicates: matching `claimNumber` + line), `reason` |
| `PolicyEvaluation` | `List<PolicyResult> results`, `configVersion` |
| `ResultType` | `LIMIT_BREACH`, `VALIDATION`, `DUPLICATE_FLAG`, `WEEKEND_FLAG` |

`PolicyConfigLoader` builds the snapshot from `policy_rules` at the claim's `config_version`; the engine never reads the DB. Every rule runs on every line and all results are returned (CL-07 lists every breaching line).

### 4.2 Evaluators (`PolicyRule`: `ruleId()`, `evaluate(LineInput, EvalContext)`)

| Rule | Class | Applies to | Fires when (values from snapshot) | Result |
|---|---|---|---|---|
| POL-001 | `MealsDailyLimitRule` | `MEALS` | sum of `MEALS` amounts on the same `expenseDate` (this claim + in-scope earlier lines, SPEC-Q-04) > `meals.max_per_day` | `LIMIT_BREACH` |
| POL-002 | `HotelTier1NightlyLimitRule` | `HOTEL`, city ∈ `hotel.tier1_cities` (trimmed, case-insensitive, aliases ASM-006) | `amount > limit × nights`, nights = `checkOutDate − checkInDate` ≥ 1 (ASM-005); actual = amount ÷ nights, HALF_UP 2dp | `LIMIT_BREACH` |
| POL-003 | `HotelOtherNightlyLimitRule` | `HOTEL`, city ∉ tier-1 | same with `hotel.max_per_night.other` | `LIMIT_BREACH` |
| POL-004 | `LocalCabTripLimitRule` | `LOCAL_CAB` | `amount > local_cab.max_per_trip` | `LIMIT_BREACH` |
| POL-005 | `ReceiptRequiredRule` | all | `amount > receipt.mandatory_above` and no receipt | `VALIDATION` (blocks submit, never stored — ASM-003) |
| POL-006 | `VendorAmountDateDuplicateRule` | all | earlier line of the **claimant** equal on `duplicate.match_fields`; vendor trimmed, inner spaces collapsed, case-insensitive; amount exact (ASM-S-02) | `DUPLICATE_FLAG` |
| POL-007 | `ReceiptHashDuplicateRule` | key true, line has receipt | earlier line of **any employee** with same SHA-256 | `DUPLICATE_FLAG` |
| POL-008 | `WeekendSpendRule` | key true | `expenseDate` is SATURDAY/SUNDAY (ASM-001; hotel `expenseDate` = check-in, ASM-S-03) | `WEEKEND_FLAG` |
| POL-009 | `DateReceiptNumberDuplicateRule` | key true | earlier line of **any employee** with same `expenseDate` + receipt number (trimmed, upper-cased) | `DUPLICATE_FLAG` |

All limit comparisons are **strictly greater than** (500.00 needs no receipt; 1,500.00 meals passes). "Earlier lines" (backbone §4.3) = lines of other claims not in `DRAFT`, `AUTO_REJECTED`, `REJECTED`, plus lines of the same claim created before this line. A line never matches itself.

### 4.3 Scope of earlier lines (loaded by `SubmitService`, ASM-S-02 / SPEC-Q-05)

| Query | Used by |
|---|---|
| Claimant's lines in other claims (state filter above) | POL-001 (see §13 R-02), POL-006 |
| Every employee's lines with same `receiptHash` (state filter above) | POL-007 |
| Every employee's lines with same `expenseDate` + normalised `receiptNumber` (state filter above) | POL-009 |

### 4.4 `RoutingDecider` (fixed order, CLAUDE.md §2.7, backbone §3.3 step 5)

| Step | Condition | `RoutingOutcome` | Transition |
|---|---|---|---|
| 1 | any `LIMIT_BREACH` | `AUTO_REJECT` (reason lists every breaching line: rule, limit, actual) | T-03 |
| 2 | total > `approval.auditor_threshold` OR any `WEEKEND_FLAG` OR any `DUPLICATE_FLAG` OR claimant role is `FINANCE_HEAD` (SPEC-Q-14, blocking) | `ROUTE` | T-04 |
| 3 | otherwise | `AUTO_APPROVE` | T-05 → T-06 / T-07 |

---

## 5. Approval-chain engine (`chain` package)

### 5.1 Computation — `ChainPlan ApprovalChainEngine.compute(ChainInput in)`

`ChainInput`: `claimantId`, `claimantRole`, `managerId` (nullable), `total`, thresholds from the snapshot, `ApproverDirectory` (`boolean hasUserWithRole(UserRole)`).

| # | Rule (backbone §5.2) | Source |
|---|---|---|
| 1 | Claimant role `FINANCE_HEAD` → exactly one `FINANCE_AUDITOR` step; stop | CL-16 |
| 2 | Required levels: `MANAGER` always; `FINANCE_AUDITOR` if total > `approval.auditor_threshold`; `FINANCE_HEAD` if total > `approval.finance_head_threshold` | §5.1, CL-03 |
| 3 | Walk levels 1→3. A level is `SKIPPED` when it has no approver (`managerId == null`, or no user holds the pool role) → `SkipReason.NO_APPROVER` (CL-09); or the claimant would approve it (`managerId == claimantId`, or claimant holds the pool role) → `SkipReason.SELF_APPROVAL` (ASM-009) | CL-09, ASM-009 |
| 4 | A skipped level **adds the next level if the amount did not require it** (e.g. FA claimant at 30,000 → MANAGER, FA `SKIPPED`, FH added; no-manager employee, weekend, 3,000 → MANAGER `SKIPPED`, FA added) | backbone §5.2 |
| 5 | First non-skipped step → `PENDING` (`activated_at` = now); later ones → `WAITING`. Claim state: `MANAGER`→`PENDING_MANAGER`, `FINANCE_AUDITOR`→`PENDING_AUDITOR`, `FINANCE_HEAD`→`PENDING_FINANCE_HEAD` | T-04 |
| 6 | Plan never empty (backbone §5.2 rule 4); if it is anyway, throw `IllegalStateException` (500) — a seed defect, not a business path | backbone §5.2 |

`PlannedStep`: `level`, `stepType`, `assignedApproverId` (manager steps only; null for pool), `status`, `skipReason`. Persisted as `approval_steps` rows with the submission number; never recomputed while in flight (B-08). On approval (T-08) the next `WAITING` step becomes `PENDING` with `activated_at`; none left → T-09.

### 5.2 Current-step authorization — `CurrentStepAuthorizer.check(actor, claim, pendingStep, delegations, todayIst)`

| Order | Check | Fail |
|---|---|---|
| 1 | actor == claimant (direct, as delegate, any role) | `422 SELF_APPROVAL_FORBIDDEN` |
| 2 | actor role `ADMIN` | `403 NOT_CURRENT_APPROVER` |
| 3 | `MANAGER` step: actor == `assignedApproverId` (delegator keeps the right, ASM-S-04), **or** an uncancelled delegation exists with delegator == `assignedApproverId`, delegate == actor, `startDate ≤ todayIst ≤ endDate` (inclusive) → `onBehalfOf` = delegator | `403 NOT_CURRENT_APPROVER` |
| 4 | `FINANCE_AUDITOR` / `FINANCE_HEAD` step: actor holds that role (pool; no delegation) | `403 NOT_CURRENT_APPROVER` |
| 5 | Sample audit (`SAMPLE_AUDIT_PENDING`): actor role `FINANCE_AUDITOR` | `403 NOT_CURRENT_APPROVER` |

Delegation is one hop only: a delegate's own delegations are never followed (B-03). Pure rules live in `chain.DelegationRules`; the service supplies data.

### 5.3 Delegation creation (API-020, backbone §5.4)

| Check | Fail |
|---|---|
| caller `canDelegate` | `403 FORBIDDEN` |
| `startDate ≤ endDate`, valid dates | `400 VALIDATION_FAILED` |
| delegate role `FINANCE_AUDITOR`/`FINANCE_HEAD` (CL-13), delegate ≠ delegator | `422 INVALID_DELEGATE` |
| overlaps an uncancelled delegation of the same delegator (ASM-007) | `422 DELEGATION_OVERLAP` |

---

## 6. Service behaviour per API

Common: Bearer JWT except API-001/002; errors per §7.1; lists use backbone §8 paging (`page`, `pageSize` ≤ 100, `sort`) and return `{items, page, pageSize, total}`; money as 2-decimal strings. **Visibility** (`ClaimAccessPolicy.canView`, backbone §7): claimant; anyone who may act on the current `PENDING` step per §5.2 (incl. active delegates and pool-role holders, never the claimant); anyone who acted on the claim. Finance head/admin get **no** visibility by role. Not visible → `404 NOT_FOUND`. Full schemas: `architecture/api-contracts.yaml` (Phase 3).

### 6.1 Platform, auth, reference

| API | Request | Response | Rules | Audit | Errors |
|---|---|---|---|---|---|
| API-001 GET `/health` | — | `status`, `database`, `timestamp` | public; app + DB check | — | — |
| API-002 POST `/auth/login` | `email`, `password` (SPEC-Q-07) | `accessToken`, `expiresAt`, `user` | public; BCrypt; rate-limited (SPEC-Q-13) | `LOGIN_SUCCEEDED` / `LOGIN_FAILED` (entity `USER`; no password logged) | 400, 401 `UNAUTHENTICATED`, 429 |
| API-003 GET `/me` | — | `userId`, `employeeCode`, `name`, `email`, `role`, `departmentId`, `isManager`, `isApprover`, `canDelegate`, `isAdmin` | any | — | 401 |
| API-036 GET `/departments` | — | `departmentId`, `name` | any | — | 401 |
| API-037 GET `/policy-rules/client-config` | — | `receiptMandatoryAbove`, `tier1Cities`, `categories` (active config) | any | — | 401 |

### 6.2 Claims and line items

| API | Request | Response | Rules | T / Audit | Errors |
|---|---|---|---|---|---|
| API-004 GET `/claims` | `status?`, paging | `claimId`, `claimNumber`, `status`, `total`, `submittedAt`, `lineCount` | caller's own claims only | — | 401 |
| API-005 POST `/claims` | per contract | 201 claim detail | claimant = caller; `claimNumber` assigned | T-01 `CLAIM_CREATED` | 400 |
| API-006 GET `/claims/{claimId}` | — | backbone §7.1: header (`claimNumber`, status, total, claimant name + department, timestamps), lines + policy results + receipt metadata (no hash), steps (status, level, approver or pool, decision, `onBehalfOf`), comments (incl. latest send-back comment), `allowedActions` | visible | — | 404 |
| API-007 POST `.../line-items` | backbone §4.4 fields: `category`, `amount`, `expenseDate`, `vendor`, `receiptNumber`, `city`, `checkInDate`, `checkOutDate` | 201 line | claimant; `DRAFT`/`SENT_BACK` (B-04); field rules of §4.4 incl. hotel fields, `expenseDate` not future, hotel `expenseDate` = `checkInDate` (ASM-S-03); `receiptNumber` on every line (SPEC-Q-15); total recomputed | `LINE_ADDED` | 400, 404, 409 |
| API-008 PUT `.../line-items/{lineItemId}` | same | 200 line | as API-007; line must belong to claim | `LINE_UPDATED` | 400, 404, 409 |
| API-009 DELETE `.../line-items/{lineItemId}` | — | 204 | as API-007 | `LINE_DELETED` | 404, 409 |
| API-010 POST `.../receipt` | multipart `file` | 201 `receiptId`, `contentType`, `sizeBytes` | as API-007; §7.5; rate-limited; replaces existing receipt (one per line, SPEC-Q-06) | `RECEIPT_UPLOADED` | 404, 409, 413, 415, 429 |
| API-011 GET `.../receipt` | — | file stream, inline | visible | — | 404 |
| API-012 POST `/{claimId}/submit` | `Idempotency-Key` | claim detail (outcome in status) | §6.3 | §6.3 | 404, 409, 422 `CLAIM_HAS_NO_LINES`/`RECEIPT_REQUIRED`/`IDEMPOTENCY_KEY_REUSED` |
| API-013 GET `/{claimId}/audit-events` | paging | `action`, `actorId`, `onBehalfOfId`, `entityType`, `entityId`, `fromState`, `toState`, `comment`, `details`, `occurredAt` | visible | — | 404 |

### 6.3 Submit pipeline (`SubmitService.submit`, one transaction, backbone §3.3)

| Step | Action | Audit |
|---|---|---|
| 1 | Lock claim (`PESSIMISTIC_WRITE`); caller ≠ claimant → 404; state ∉ {`DRAFT`,`SENT_BACK`} → 409 | — |
| 2 | ≥ 1 line else `422 CLAIM_HAS_NO_LINES`; POL-005 else `422 RECEIPT_REQUIRED` with `details.lines[]`; state unchanged | — |
| 3 | Resubmission: earlier steps → `SUPERSEDED` (CL-12). Store current `config_version` on claim | `CLAIM_SUBMITTED` (→ `SUBMITTED`) |
| 4 | Load earlier lines (§4.3), run engine, persist non-`VALIDATION` results with submission number | `POLICY_EVALUATED` (`details` = summary) |
| 5a | `AUTO_REJECT` → `AUTO_REJECTED`, reason stored | `CLAIM_AUTO_REJECTED` (T-03) |
| 5b | `ROUTE` → compute chain, insert steps | `STEP_SKIPPED` per skipped step, `CLAIM_ROUTED` (T-04), `STEP_ACTIVATED` for the first step |
| 5c | `AUTO_APPROVE` → `SampleAuditSelector.draw(rate)` | `CLAIM_AUTO_APPROVED` (T-05), then `SAMPLE_AUDIT_SELECTED` (T-06) or `SAMPLE_AUDIT_NOT_SELECTED` (T-07); drawn value in `details` |

`SampleAuditSelector`: `selected = random.nextDouble() < sample_audit.rate` using the injected `RandomSource` (`SecureRandom` in production; fixed stub in tests; never a known seed — backbone §5.5, ASM-S-05). Selection time stored on the claim for metrics.

### 6.4 Approvals, comments, delegation

| API | Request | Rules | T / Audit | Errors |
|---|---|---|---|---|
| API-014 GET `/approvals` | paging | `PENDING` manager steps assigned to caller or to a delegator for whom caller is active delegate, plus `PENDING_FINANCE_HEAD` claims for finance heads; never own claims; empty unless `isApprover` | — | 401 |
| API-015 POST `/{claimId}/approve` | `comment?`, `Idempotency-Key` | lock claim; `PENDING_*` or `SAMPLE_AUDIT_PENDING` else 409; §5.2 | T-08 `STEP_APPROVED` + `STEP_ACTIVATED`; T-09 `STEP_APPROVED` + `CLAIM_APPROVED`; T-12 `CLAIM_APPROVED` | 403 `NOT_CURRENT_APPROVER`, 404, 409, 422 `SELF_APPROVAL_FORBIDDEN`/`IDEMPOTENCY_KEY_REUSED` |
| API-016 POST `/{claimId}/reject` | `comment` (non-blank), `Idempotency-Key` | as API-015; comment saved in `claim_comments` | T-10 / T-13 `CLAIM_REJECTED` | as API-015 + 422 `COMMENT_REQUIRED` |
| API-017 POST `/{claimId}/send-back` | `comment` (non-blank, SPEC-Q-03), `Idempotency-Key` | `PENDING_*` only; §5.2 rules 1–4 | T-11 `CLAIM_SENT_BACK`; step → `SENT_BACK` | as API-016 |
| API-018 POST `/{claimId}/comments` | `body` (non-blank) | current-step approver or a user who acted on the claim | `COMMENT_ADDED` | 403 `NOT_CURRENT_APPROVER`, 404 |
| API-019 GET `/delegations` | paging | `canDelegate` else 403; caller's delegations as delegator or delegate | — | 403 |
| API-020 POST `/delegations` | `delegateId`, `startDate`, `endDate` | §5.3 | `DELEGATION_CREATED` | 400, 403, 422 `INVALID_DELEGATE`/`DELEGATION_OVERLAP` |
| API-021 DELETE `/delegations/{delegationId}` | — | delegator only (others → 404); sets `cancelled` | `DELEGATION_CANCELLED` | 404 |
| API-038 GET `/delegations/eligible-delegates` | — | `canDelegate`; users with `FINANCE_AUDITOR`/`FINANCE_HEAD`, excluding caller | — | 403 |

Every acting audit entry stores `actor_id` and `on_behalf_of_id`.

### 6.5 Review queue (`FINANCE_AUDITOR` only)

| API | Request | Rules | T / Audit | Errors |
|---|---|---|---|---|
| API-022 GET `/review-queue` | `departmentId`, `category`, `minAmount`, `maxAmount`, `fromDate`, `toDate`, `flag` (`DUPLICATE`,`WEEKEND`), `queue` (`AUDITOR_STEP`,`SAMPLE_AUDIT`), paging (SPEC-Q-09) | `PENDING_AUDITOR` + `SAMPLE_AUDIT_PENDING`, never own | — | 403 |
| API-023 POST `/review-queue/bulk-approve` | `{claimIds}` 1–100, `Idempotency-Key` | each claim through API-015 logic in **its own transaction**; flagged allowed | per claim as API-015 | 200 `{results:[{claimId, outcome: APPROVED\|FAILED, code, message}]}`; 400, 403, 422 `IDEMPOTENCY_KEY_REUSED` |

### 6.6 Payout (`isAdmin`, CLAUDE.md §2.8)

| API | Request | Rules | T / Audit | Errors |
|---|---|---|---|---|
| API-024 GET `/payout-batches` | paging | — | — | 403 |
| API-025 GET `/payout-batches/eligible-claims` | paging | `APPROVED`, `payout_batch_id` null (sampled claims never `APPROVED` until reviewed, CL-14) | — | 403 |
| API-026 POST `/payout-batches` | `claimIds[]` ≥ 1, `Idempotency-Key` | lock claims; each `APPROVED` and in no batch; status `CREATED`; `batchNumber` assigned; atomicity see §13 R-03 | `PAYOUT_BATCH_CREATED`; T-14 `CLAIM_ADDED_TO_BATCH` per claim | 403, 409, 422 `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` |
| API-027 GET `/payout-batches/{batchId}` | — | header + claims | — | 403, 404 |
| API-028 POST `/payout-batches/{batchId}/export` | `Idempotency-Key` | status `CREATED` else 409; one transaction: build CSV (§7.6), store file once (`ExportFileStore`), save SHA-256/row count/total/path, status `EXPORTED`, claims → `PAID` | `PAYOUT_BATCH_EXPORTED`; T-15 `CLAIM_PAID` per claim | 403, 404, 409 |
| API-029 GET `/payout-batches/{batchId}/file` | — | `EXPORTED` else 409; returns stored bytes, `text/csv` attachment | — | 403, 404, 409 |

Export response = control summary `fileName`, `rowCount`, `totalAmount`, `fileSha256`. If the file write succeeds but the DB commit fails, the orphan file is deleted (no claim is `PAID` without a stored file).

### 6.7 Dashboard, audit, policy config

| API | Request | Rules (backbone §7.3, SPEC-Q-11) | Audit | Errors |
|---|---|---|---|---|
| API-030 GET `/dashboard/spend` | `groupBy` (`category`/`department`/`month`), `fromDate`, `toDate`, `departmentId` | `FINANCE_AUDITOR` or `isAdmin`; sum of line amounts of claims in `APPROVED`, `IN_PAYOUT_BATCH`, `PAID`; month of `expenseDate` | — | 403 |
| API-031 GET `/dashboard/policy-violations` | same filters | count of `policy_results` by rule ID, latest submission per claim | — | 403 |
| API-032 GET `/dashboard/approval-time` | same filters | average calendar time submit → final approval (SPEC-Q-08) | — | 403 |
| API-033 GET `/audit-events` | `claimId`, `actorId`, `action`, `entityType`, date range, paging | `isAdmin` | — | 403 |
| API-034 GET `/policy-rules` | — | `isAdmin`; latest row per key + `configVersion` | — | 403 |
| API-035 PUT `/policy-rules/{ruleKey}` | `value` | `isAdmin`; type/range per backbone §4.1 (incl. FH threshold > auditor threshold) else 400; inserts new row with next `config_version`; in-flight claims keep theirs (B-08) | `POLICY_RULE_CHANGED` (`details` = old/new) | 400, 403, 404 |

---

## 7. Cross-cutting

### 7.1 Errors
`ErrorResponse(code, message, details, traceId)`; `code` ∈ backbone §9 only. `GlobalExceptionHandler`: bean-validation / unreadable body / unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES`) → 400 `VALIDATION_FAILED` with `details.fields`; `AuthenticationException` → 401; `AccessDeniedException` → 403 `FORBIDDEN`; `HttpRequestMethodNotSupportedException` → 405 `METHOD_NOT_ALLOWED`; `MaxUploadSizeExceededException` → 413; `ApiException` → its status/code; else 500 `INTERNAL_ERROR` (stack trace logged server-side only). Security entry point and access-denied handler write the same body.

### 7.2 Trace ID
`TraceIdFilter` (first in chain): reuse inbound `X-Trace-Id` if `^[A-Za-z0-9-]{8,64}$`, else new UUID; MDC `traceId`; response header; copied to `audit_events.trace_id` and error bodies.

### 7.3 Idempotency (API-012, 015, 016, 017, 023, 026, 028)
`IdempotencyService` on `idempotency_keys`: key (1–64 chars) + user + operation (API ID) unique. Same request hash → replay stored status/body, no re-execution. Different hash → `422 IDEMPOTENCY_KEY_REUSED`. Rows older than 24 h ignored and purged by a scheduled job. Key row is inserted in the same transaction as the action (for API-023, in a separate transaction recording the aggregated response). Header optional; without it, state guards still return 409 on repeats.

### 7.4 Transactions and concurrency
State change + audit rows + step rows in one `@Transactional` service method. Claims locked `PESSIMISTIC_WRITE` before state checks; the losing concurrent approver gets 409. `open-in-view=false` (already set).

### 7.5 Receipt storage (§13.5)
Docker volume `RECEIPT_STORAGE_DIR`, file name = generated UUID. Content sniffed from magic bytes; allow-list/size per SPEC-Q-06 (proposed PDF/JPEG/PNG, 5 MB) → 415/413. SHA-256 computed while streaming. Served only via API-011 after `canView`, with `X-Content-Type-Options: nosniff`. Contents never logged.

### 7.6 Payout CSV (`PayoutCsvWriter`, CLAUDE.md §2.8)
File `EXPENSEFLOW_PAYOUT_<batchNumber>_<YYYYMMDD>.csv`; UTF-8, comma, header row, every text field quoted (inner `"` doubled); 13 columns in §2.8 order; `amount` = `toPlainString()` 2dp; ISO dates; `value_date` = export date (IST); `payment_mode` `NEFT`; `currency` `INR`; `employee_id` = `employee_code`; `claim_id` = `claim_number`; `payment_reference` = `<batchNumber>-<4-digit row no>`; `narration` cut to 30 chars (backbone §8). **Neutralisation:** a value starting with `=`, `+`, `-`, `@`, `\t`, `\r` gets a leading `'` (§13.4). Rows ordered by `claim_number`. Control summary from the exact stored bytes.

### 7.7 Security — **Proposed pending ADR-000**
Stateless Spring Security; `JwtAuthenticationFilter` validates HS256 Bearer (`sub` = userId, `role`, `exp`), secret `JWT_SECRET` (≥ 32 bytes, else startup fails), lifetime per SPEC-Q-13. BCrypt for seeded passwords. Public: API-001, API-002, `/actuator/health`. `/actuator/prometheus` limited to the Compose network (`ops/SPEC.md`). Role/capability checks in services via `CapabilityService`; object-level via `ClaimAccessPolicy`. CSRF off (no cookies). Runtime DB user `expenseflow_app` (no DDL; `SELECT`/`INSERT` only on `audit_events`); Flyway runs as `expenseflow_owner` (backbone §6).

### 7.8 Rate limiting
API-002 per IP and API-010 per user, in-memory fixed window → 429 `RATE_LIMITED`. Values per SPEC-Q-13 (proposed: login 5/min per IP, uploads 30/min per user), read from config.

### 7.9 Logging
Key=value or JSON with `traceId`, `userId`, method, path, status, duration. Never log passwords, tokens, `Authorization`, receipt bytes or bank account numbers.

### 7.10 Metrics (NFR-008, backbone §10)
`ApprovalMetrics` registers gauges for every `step` label (`MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD`, `SAMPLE_AUDIT`), refreshed by a scheduled query; series always present, 0 when empty. `expenseflow_approval_queue_size{step}` = claims waiting; `expenseflow_oldest_pending_approval_seconds{step}` = now − oldest `activated_at` of a `PENDING` step (sample audit: selection time). `http_server_requests_seconds` with `management.metrics.distribution.percentiles-histogram.http.server.requests=true`. Page latency: SPEC-Q-12.

---

## 8. Configuration / environment variables

| Variable | Status | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Existing | Runtime connection as `expenseflow_app` |
| Flyway owner credentials | Proposed (names per `database/SPEC.md` / ADR-000) | `spring.flyway.user/password` = `expenseflow_owner` |
| `CORS_ALLOWED_ORIGINS`, `JPA_SHOW_SQL` | Existing | |
| `JWT_SECRET`, `JWT_TTL_MINUTES` | Proposed (SPEC-Q-13: 8 h) | Token |
| `RECEIPT_STORAGE_DIR`, `RECEIPT_MAX_BYTES`, `RECEIPT_ALLOWED_TYPES` | Proposed (SPEC-Q-06) | §7.5 |
| `EXPORT_STORAGE_DIR` | Proposed | Stored payout files (backbone §6) |
| `RATE_LIMIT_LOGIN_PER_MIN`, `RATE_LIMIT_UPLOAD_PER_MIN` | Proposed (SPEC-Q-13) | §7.8 |
| `METRICS_REFRESH_SECONDS` | Proposed | §7.10 |

Business values (limits, thresholds, sample rate) are **never** env vars — they live in `policy_rules`. There is no sampling seed variable (backbone §5.5). `.env.example` holds placeholders only.

---

## 9. Gap analysis vs current base code

| Item | Current | Required | Action |
|---|---|---|---|
| Health | `/api/health` | API-001 `/api/v1/health`; `/actuator/health` for container | Move to `controller`; update `HealthControllerTests` |
| Error shape | RFC 9457 `ProblemDetail`; `spring.mvc.problemdetails.enabled=true` | `{code,message,details,traceId}` | Rewrite handler; remove property; drop `IllegalArgumentException`→400 |
| `ResourceNotFoundException` | message includes id | 404 `NOT_FOUND`, generic message | Replace with `ApiException` |
| 405 | Spring default body | `METHOD_NOT_ALLOWED` shape | Add handler |
| Trace ID | none | `TraceIdFilter` | Add |
| Schema | `ddl-auto=update`, single DB user | Flyway (**Proposed**) as owner, `ddl-auto=validate`, runtime as `expenseflow_app`; migrations in `database/migrations/` as Maven resource; Docker build context moves to repo root (backbone §13) | After ADR-000 |
| Security | none | Spring Security + JWT + BCrypt (**Proposed**) | Add starter, JWT library per ADR-000, `spring-security-test` |
| Packages | `common/`, `health/` | §2 layout | Move |
| IDs | `BaseEntity` `Long` IDENTITY | UUIDv7 PKs; `audit_events` BIGINT identity (ASM-S-06) | Replace `BaseEntity` id; `AuditEvent` `@Immutable`, no `@UpdateTimestamp` |
| JSON | unknown fields accepted; money as number | reject unknown; money as string | `JacksonConfig` |
| Tests DB | H2 PostgreSQL mode (not in §8.1 stack; can't prove NFR-004) | Engines need no DB; DB behaviour via pytest on Docker stack (backbone §13) | Keep H2 only for context tests, or drop per ADR-000 |
| Actuator | health, info, prometheus exposed | prometheus not public; histogram on | `SecurityConfig`, property |
| `README.md` | Node-style placeholders | Maven wrapper commands, `/api/v1/health` | Phase 4c |
| Dockerfile healthcheck | `curl` on `eclipse-temurin:21-jre` | curl may be absent | Verify in Phase 7 |
| Dependency check | none | OWASP Dependency-Check (NFR-007) | Phase 7 |

---

## 10. Implementation tasks (test-first)

| # | Phase | Task | Requirements | Done-check |
|---|---|---|---|---|
| 1 | 4a | Base fixes: health path, error shape + 405, `TraceIdFilter`, package moves, Jackson config | API-001, §11.4 | `mvnw verify` green; `GET /api/v1/health` 200; 404 body has 4 fields |
| 2 | 4a | `policy` records + `PolicyRule` + `EnginePurityTest` (no logic) | §9.2 | Compiles; purity test passes |
| 3 | 4a | **Failing** JUnit tests, one class per rule: POL-001 (1500.00 ok, 1500.01 breach, two lines same day summed, earlier-claim line counted, rejected claim ignored); POL-002/003 (6000×n and 4000×n ± 0.01, 3 nights, `mumbai`, `Bengaluru`, `New Delhi`, untrimmed city); POL-004 (2000.00/2000.01); POL-005 (500.00 ok, 500.01 blocked); POL-006 (vendor case/space, other claimant no match, DRAFT ignored); POL-007 (renamed file same hash, other employee matches); POL-009 (`r-1` vs `R-1 `); POL-008 (Sat, Sun, Fri, key false, hotel check-in Sat) | POL-001–009, CL-01/05/07/08, ASM-001/005/006, ASM-S-02/03 | Tests run and **fail**; commit `test(policy)` |
| 4 | 4a | Implement 9 evaluators + `PolicyEngine` | FR-004 | Task-3 tests pass |
| 5 | 4a | Failing then passing `RoutingDeciderTest`: breach beats flags; totals 24,999.99 / 25,000.00 auto-approve, 25,000.01 route; weekend at 100.00 routes; duplicate routes; FH claimant routes (SPEC-Q-14) | CL-02, CL-03, CL-07 | Green |
| 6 | 4b | Failing `ApprovalChainEngineTest`: 24,999.99/25,000.00/25,000.01 and 99,999.99/1,00,000.00/1,00,000.01 → step lists; no manager weekend 3,000 → FA added; FA claimant 30,000 → FH added; FA claimant 1,50,000; FH claimant any amount → single FA; first `PENDING`, rest `WAITING` | FR-005, CL-03, CL-09, CL-16, ASM-009 | Fails, then passes |
| 7 | 4b | Failing `CurrentStepAuthorizerTest` / `DelegationRulesTest`: active window (start, end inclusive, IST date), before start, after end, cancelled, delegate not FA/FH, self-delegation, overlap, delegate is claimant, delegation-of-delegation ignored, delegator still acts (ASM-S-04), ADMIN refused, pool step any holder, sample audit FA only | FR-008, CL-13, CL-14, ASM-007, B-02, B-03 | Fails, then passes |
| 8 | 4b | `SampleAuditSelectorTest` with stub random (0.0999 → selected, 0.10 → not) | FR-011, ASM-S-05 | Green |
| 9 | 4b | Flyway wiring, entities, repositories (schema from `database/SPEC.md`); app runs as `expenseflow_app` | NFR-004 | Boots on PostgreSQL 18; audit `UPDATE` denied |
| 10 | 4b | Security, API-002/003, capabilities (after ADR-000) | Q-22, §13 | pytest: 401 without token; 429 on burst; `/me` flags correct |
| 11 | 4c | Claims/lines/receipts API-004–011, 037, `ClaimAccessPolicy` | FR-001/002/019, CL-15 | pytest: other's claim → 404; finance head on unrelated claim → 404; edit after submit → 409; bad file → 415/413 |
| 12 | 4c | `SubmitService` API-012, audit, idempotency | FR-003/004/011, CL-07/12/14 | pytest: each §2.7 route; resubmit → old steps `SUPERSEDED`, restart at manager; reused key + new body → 422 |
| 13 | 4c | Approvals API-014–018, delegation API-019–021, 038 | FR-005–008/020, CL-04 | pytest: reject w/o comment → 422; self-approve → 422; wrong approver → 403 `NOT_CURRENT_APPROVER`; double approve → 409 |
| 14 | 4c | Review queue API-022/023 | FR-009/010 | pytest: filters; bulk with one invalid → mixed results |
| 15 | 4c | Payout API-024–029; `PayoutCsvWriterTest` (quoting, neutralisation, 2dp, narration 30) | FR-012–014, CL-06/11/14, §2.8 | Sampled claim not eligible; re-download same SHA-256 |
| 16 | 4c | Dashboard API-030–032, audit API-013/033, policy API-034/035, API-036 | FR-015–018, CL-10 | Config change audited; in-flight claim keeps version; bad value → 400 |
| 17 | 4c | Metrics, rate limits, logging review | NFR-008, §13.8 | `/actuator/prometheus` shows 4 label series for both gauges |

---

## 11. Acceptance criteria

| # | Given | When | Then |
|---|---|---|---|
| AC-01 | Employee draft, one MEALS line 1,500.00 on a Wednesday, total ≤ 25,000 | submit | `CLAIM_AUTO_APPROVED` audited, then `APPROVED` or `SAMPLE_AUDIT_PENDING` with drawn value in `details` |
| AC-02 | Lines: cab 2,000.01 and meals 1,600.00 | submit | `AUTO_REJECTED`; reason lists both lines with rule, limit, actual (CL-07) |
| AC-03 | Line 500.01, no receipt / claim with no lines | submit | 422 `RECEIPT_REQUIRED` / `CLAIM_HAS_NO_LINES`; state unchanged |
| AC-04 | Clean lines totalling 25,000.00 / 25,000.01 | submit | auto-approve / `PENDING_MANAGER` with FA step `WAITING` |
| AC-05 | Total 1,00,000.01, claimant has manager | each step approves | `PENDING_MANAGER` → `PENDING_AUDITOR` → `PENDING_FINANCE_HEAD` → `APPROVED`; `STEP_ACTIVATED` each time |
| AC-06 | Claimant is finance head, total 2,00,000 | submit | single FA step; `PENDING_AUDITOR` (CL-16) |
| AC-07 | Claimant has no manager, weekend line, total 3,000 | submit | MANAGER `SKIPPED` + `STEP_SKIPPED`; FA step added; `PENDING_AUDITOR` |
| AC-08 | Finance auditor's own claim, total 30,000 | submit | MANAGER `PENDING`, FA `SKIPPED`, FH `WAITING` (ASM-009) |
| AC-09 | Same receipt file renamed, on another employee's claim | submit second | POL-007 `DUPLICATE_FLAG` naming the first claim number; routed |
| AC-10 | Approver on `PENDING_MANAGER` | reject with empty comment | 422 `COMMENT_REQUIRED`; state unchanged |
| AC-11 | Sent back after manager + auditor approved; amount lowered to 20,000 | resubmit | old steps `SUPERSEDED`; policy re-runs; new chain from manager (CL-12) |
| AC-12 | Manager delegated to an FA; today (IST) = `endDate` | FA approves | `STEP_APPROVED`, `actor_id` = FA, `on_behalf_of_id` = manager |
| AC-13 | Same delegation; today = `endDate` + 1 | FA approves | 403 `NOT_CURRENT_APPROVER` |
| AC-14 | FA is the claimant and the manager's delegate | FA approves | 422 `SELF_APPROVAL_FORBIDDEN` |
| AC-15 | Employee A; finance head with no link to claim | GET employee B's claim | 404 `NOT_FOUND` for both |
| AC-16 | Claim in `SAMPLE_AUDIT_PENDING` | add to payout batch | 422 `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` |
| AC-17 | Batch of 3 `APPROVED` claims, a beneficiary name starting with `=` | export, then download twice | value prefixed `'`; claims `PAID`; status `EXPORTED`; summary matches; both downloads identical bytes |
| AC-18 | Submit with key K, then same K with a different body | second call | 422 `IDEMPOTENCY_KEY_REUSED`; same K + same body → first response replayed |
| AC-19 | Any audit row | `UPDATE`/`DELETE` as `expenseflow_app` | denied (grants + trigger, NFR-004) |
| AC-20 | Bulk approve 3 claims, one is the caller's own | POST API-023 | 200; two `APPROVED`, one `FAILED` with `SELF_APPROVAL_FORBIDDEN` |

---

## 12. Open questions and assumptions referenced

| ID | Affects here |
|---|---|
| **SPEC-Q-02** | `ExpenseCategory` values |
| **SPEC-Q-03** | API-017 comment mandatory (implemented as mandatory) |
| SPEC-Q-04 / Q-11 | POL-001 scope (§4.3) |
| **SPEC-Q-05** | POL-006/007/009 scope (§4.3, ASM-S-02) |
| SPEC-Q-06 / Q-21 | receipt types/size, one file per line |
| SPEC-Q-07 / Q-22 | API-002 login method |
| SPEC-Q-08 / Q-23 | API-032 measure |
| SPEC-Q-09 / Q-24 | API-022 filters, API-023 flagged claims |
| SPEC-Q-10 / Q-25 | receipt at draft, withdrawal (not built) |
| SPEC-Q-11 | API-030/031 definitions |
| SPEC-Q-12 | page latency metric |
| SPEC-Q-13 | rate limits, JWT lifetime |
| **SPEC-Q-14** | FH clean claim ≤ 25,000 always routed (`RoutingDecider` step 2) |
| SPEC-Q-15 | `receiptNumber` required on every line |
| ASM-S-01..06 | ADMIN unseeded; duplicate matching; hotel date; delegator keeps right; per-claim draw; UUIDv7 keys |
| Q-06, Q-13, Q-20; ASM-001–010 | not built / used as logged, unconfirmed |

---

## 13. Issues found in `architecture/SPEC.md`

**Resolved by backbone v0.2** (v0.1 IDs): I-01 skip rule (§5.2), I-02 idempotency table + `IDEMPOTENCY_KEY_REUSED`, I-03 `CLAIM_ROUTED`, I-04 `CLAIM_ADDED_TO_BATCH`, I-05 `step_status` + `SUPERSEDED`, I-06 visibility rule, I-07 capabilities, I-08 `NOT_CURRENT_APPROVER`, I-09 bulk results, I-10 paging, I-11 dashboard (SPEC-Q-11), I-12 normalisation (ASM-S-02), I-13 key types, I-14 narration (§8 + owner note), I-15 (SPEC-Q-13), I-16 metric labels, I-17 SecureRandom + ASM-S-05, I-18 pool steps / sample audit FA only, I-19 display IDs + batch status.

**Still open:**

| # | Issue | Suggested resolution (backbone owner decides) |
|---|---|---|
| R-01 | The §7 visibility rule covers "anyone who may act on the current `PENDING` step", but `SAMPLE_AUDIT_PENDING` claims have no step. Finance auditors must open them (API-006/011) to review receipts, and the rule does not say so. | Add "finance auditors, for `SAMPLE_AUDIT_PENDING` claims (not their own)" to the visibility rule. |
| R-02 | POL-001 scope: SPEC-Q-04 says "across all **non-rejected** claims", while §4.3 "earlier lines" also excludes `DRAFT`. It is unclear whether `DRAFT` meal lines count toward the daily sum and whether "non-rejected" includes `AUTO_REJECTED`. This spec applies the §4.3 filter. | State the POL-001 state filter explicitly. |
| R-03 | API-026 (create batch): it is not stated whether one ineligible claim fails the whole request or only that claim (API-023 got per-claim results). This spec uses all-or-nothing with `422 CLAIM_NOT_ELIGIBLE_FOR_PAYOUT`. | Confirm. |
| R-04 | `LOGIN_FAILED` for an unknown email has no actor. Nullable `actor_id` and the `entity_id` value are not stated. | Say `actor_id` null, `entity_type` `USER`, `entity_id` null. |
| R-05 | The env/config names for the Flyway owner credentials and the export-file volume are not named in the backbone or §8 conventions, yet runtime and migration users differ (§6 DB roles). | Name them in `database/SPEC.md` / `ops/SPEC.md` or the backbone. |
| R-06 | The "sample-audit selection time" used by `expenseflow_oldest_pending_approval_seconds{step="SAMPLE_AUDIT"}` is not a named column; §6 says only "sample-audit fields". | Name the column (e.g. in `database/SPEC.md`). |
