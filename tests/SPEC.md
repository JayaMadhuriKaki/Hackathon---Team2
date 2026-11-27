# ExpenseFlow — Test Specification

| Field | Value |
|---|---|
| Status | Draft v0.2 — for team review before any test or engine code |
| Date | 2026-11-27 |
| Parent spec | `architecture/SPEC.md` **v0.2** (states, T-01..T-15, §4 policy, §5 chain, API-001..API-038, error codes, SPEC-Q-02..15, ASM-S-01..06) |
| Sources | `CLAUDE.md` §2 (FR-001..020, POL-001..009, DATA-001..005, NFR-001..009), §2.6 (CL-01..CL-18), §2.7, §2.8, §5 (B-01..B-12), §6 (Q-##, ASM-###), §8, §13, §16, §17, §19; `database/SPEC.md` §7 (seed personas and counts); `.claude/skills/quality-engineering/SKILL.md` |
| Deliverables fed | `deliverables/test-cases.xlsx` (Test Cases, Defects, Summary), `deliverables/validation-matrix.xlsx` (Validation, Summary) |
| Current state | `tests/backend`, `tests/frontend`, `tests/integration`, `tests/test-data` hold only `.gitkeep`. Existing JUnit: `BackendApplicationTests`, `HealthControllerTests` (calls `/api/health`, runs on H2 — see §11 task 6) |
| Changes in v0.2 | Aligned to backbone v0.2: skip-and-add chain rule, pool steps and `NOT_CURRENT_APPROVER`, capabilities (API-003), claim visibility rule, §4.4 field rules (400 on add, 422 at submit), hotel `amount > limit × nights`, §4.3 duplicate matching, idempotency, per-claim bulk results, delegation rules, injectable sample draw, `CLAIM_ADDED_TO_BATCH`, 405, API-037/038, seed personas from `database/SPEC.md`. Issues I-01..I-19 re-assessed in §13 |

**Rules.** Tests check documented behaviour only. Undecided behaviour is written but marked **Blocked by &lt;ID&gt;** and not automated until answered. Behaviour that rests on a backbone *proposed default* (ASM-S-##, or a non-blocking SPEC-Q default) is tested as written and the ID is listed in "Ref", so a different answer finds every affected case. Cases that exist only because of a proposed answer to a **business-critical** SPEC-Q (SPEC-Q-05, SPEC-Q-14, SPEC-Q-02, SPEC-Q-03) are Blocked. Names (states, step statuses, API IDs, error codes, audit actions) are the backbone's, verbatim. Where `database/SPEC.md` and the backbone differ, the backbone wins.

---

## 1. Test strategy per level

| Level | Tool | Location | Covers | How to run (PowerShell) | When |
|---|---|---|---|---|---|
| Unit (Java) | JUnit 5 via `spring-boot-starter-test` (CL-18) | `backend/src/test/java/com/hackathon/backend/` (`policy/`, `chain/`, `payout/`) | Policy engine (§3), chain engine, delegation, sample draw with injected random source and clock (§4), CSV writer (§2.8) — pure modules, no DB | `Set-Location backend; .\mvnw.cmd test` · one class: `.\mvnw.cmd test "-Dtest=PolicyEngineTest"` | Local before every commit; CI on every push |
| API (black box) | pytest + httpx, Python 3.13 (ASM-012) | `tests/backend/` | Every API-001..038: happy path, 400/401/403/404/405/409/413/415/422 (§5) | `py -3.13 -m venv .venv; .\.venv\Scripts\Activate.ps1; pip install pytest httpx; $env:API_BASE_URL="http://localhost:8080/api/v1"; pytest tests/backend -q` | Local against running stack; CI after `docker compose up` |
| Integration | pytest + httpx (+ `psycopg` read-only checks where a DB fact must be proven) | `tests/integration/` | Submit pipeline, routing matrix (§4), policy mirrors (§16.1), payout, audit trail, seed manifest (§8.1) | `pytest tests/integration -q` | Local; CI on every push |
| Component | Vitest + React Testing Library (Proposed, §8.1 — confirm in ADR-000) | `tests/frontend/component/` (config in `frontend/`) | Forms and client validation from API-037, loading/empty/error states, duplicate-submit prevention, bulk-action tables, `allowedActions`/capabilities-driven buttons | `Set-Location frontend; npx vitest run` | Local; CI on every push |
| E2E | Playwright 1.60+ | `tests/frontend/e2e/` | TC-E2E-001..010 (§6); mobile viewport for NFR-001 | `Set-Location frontend; npx playwright test` (fallback `--project=msedge`) | Local before demo; CI on demand (needs full stack) |
| Load | Locust (pip, Python 3.13) | `tests/load/` (planned, §7.1) | §9 scenarios; report only | `pip install locust; locust -f tests/load/locustfile.py --host http://localhost:8080 --headless -u 20 -r 2 -t 5m --csv tests/load/results/run` | Manual, Phase 6 and 8 |
| Security | Manual + automated; OWASP Dependency-Check, `npm audit` | Results in `architecture/security-review.md`; automated parts `tests/integration/test_security_*.py` | NFR-009, B-01..B-12, §13 (§7) | `Set-Location backend; .\mvnw.cmd org.owasp:dependency-check-maven:check` · `Set-Location frontend; npm audit --audit-level=high` · `pytest tests/integration -q -k security` | Phase 2 review, Phase 6 run; dependency check in CI (NFR-007) |

- Port `8080` and env var `API_BASE_URL` are proposals until the Compose file exists (Phase 7). Demo-login password comes from `$env:SEED_DEMO_PASSWORD` (same variable the seed uses, `database/SPEC.md` §7); never committed.
- pytest suites run against a **freshly seeded** stack (`docker compose down -v; docker compose up -d --build`); the seed is deterministic (`SEED = 20261127`).
- Tests that change policy config (API-035) run in one serial module and restore the value afterwards; config is global and versioned (backbone §4.1).
- Blocked cases are recorded in `test-cases.xlsx` with Status `Blocked`; they are never added as skipped tests to make a build pass (§14.3, §16.7).

## 2. IDs and traceability

| ID | Format | Range / meaning |
|---|---|---|
| Unit, policy engine | `TC-BE-001..099` | §3 matrix (JUnit) |
| Unit, chain / delegation / sample / payout / CSV | `TC-BE-101..199` | §4 matrix (JUnit) |
| API | `TC-BE-(200 + 10×(n−1) + k)` | `n` = API number 1..38 (→ `TC-BE-201..579`); `k`: 1 happy, 2 = 400, 3 = 401, 4 = 403, 5 = 404, 6 = 409, 7 = 413, 8 = 415, 9 = 422/405/429/other. Example: API-012 → 404 = `TC-BE-315` |
| Integration, policy mirrors | `TC-INT-001..099` | `TC-INT-0nn` re-runs `TC-BE-0nn` through API-007/010/012 (§16.1) |
| Integration, flows | `TC-INT-101..199` | `TC-INT-1nn` runs `TC-BE-1nn` through HTTP where marked "INT" in §4 |
| Seed checks | `TC-INT-201..209` | DATA-001..005 (§8.1) |
| Security | `TC-INT-801..899` | §7 (xlsx Level `Security`) |
| Performance | `TC-INT-901..999` | §9 (xlsx Level `Performance`) |
| Component | `TC-FE-001..` | Vitest |
| E2E | `TC-E2E-001..010` | §6 |
| Defect | `BUG-###` | Defects sheet of `test-cases.xlsx` |

**Linking.** One row per case in `test-cases.xlsx` › Test Cases: `Test Case ID`, `Level` (Unit/API/Integration/E2E/Performance/Security), `Requirement ID(s)` (FR/POL/NFR/DATA/CL/B-##), `API ID`, `Test Data` (path in `tests/test-data/`), `Expected Result` (from this spec), `Status` (Not Run/Pass/Fail/Blocked); blocking ID in `Remarks`. Automated tests carry the ID in the name (`@DisplayName("TC-BE-003 ...")`, `@pytest.mark.tc("TC-INT-003")`, `test("TC-E2E-001 ...")`). Failures become `BUG-###` rows. The template example rows (row 2, `/items`) are deleted when the first real row is entered.

---

## 3. Policy rule test matrix (JUnit first; each mirrored as TC-INT-0nn)

Defaults unless stated: claimant `EMP-A` (has a manager), weekday dates (2026-11-24 is a Tuesday), receipt attached, vendors prefixed `TST `, receipt numbers prefixed `TST-R-`, config = seed values (backbone §4.1), total ≤ 25,000. "Auto-approve path" = `AUTO_APPROVED` audit, then `APPROVED` or `SAMPLE_AUDIT_PENDING` (JUnit injects the draw; HTTP tests accept either and check the matching `SAMPLE_AUDIT_*` audit action — see N-01). Every fired result must carry rule ID, config key, limit, actual value and reason (backbone §4.2).

| ID | Rule | Type | Input | Engine result | Claim outcome | Ref / blocked |
|---|---|---|---|---|---|---|
| TC-BE-001 | POL-001 | Positive | Meals 1,200.00 on 2026-11-24 | none | Auto-approve path | CL-02 |
| TC-BE-002 | POL-001 | Boundary | Meals 1,500.00 | none (≤ limit) | Auto-approve path | CL-01 |
| TC-BE-003 | POL-001 | Boundary | Meals 1,500.01 | `LIMIT_BREACH` POL-001, `meals.max_per_day`, limit 1500.00, actual 1500.01 | `AUTO_REJECTED`; reason names line, rule, limit, actual | CL-07, T-03 |
| TC-BE-004 | POL-001 | Negative | Meals 4,000.00 | `LIMIT_BREACH` POL-001 | `AUTO_REJECTED` | CL-07 |
| TC-BE-005 | POL-001 | Edge | Two meal lines same date in one claim: 900.00 + 700.00 | breach (sum 1,600.00) under default | `AUTO_REJECTED` | Ref SPEC-Q-04 default (non-blocking) |
| TC-BE-006 | POL-001 | Edge | Meals 1,000.00 on 2026-11-24 in each of two claims (first one `APPROVED`); variant: first claim `REJECTED` | breach / none | `AUTO_REJECTED` / auto-approve path | Ref SPEC-Q-04 default |
| TC-BE-007 | POL-001 | Config | Config `meals.max_per_day` = 2000.00; meals 1,800.00 | none | Auto-approve path (limit read from config) | §2.2 |
| TC-BE-010 | POL-002 | Positive | Hotel Mumbai, check-in 2026-11-23, check-out 2026-11-24 (1 night), 5,500.00 | none | Auto-approve path | CL-08, ASM-005 |
| TC-BE-011 | POL-002 | Boundary | Hotel Mumbai 1 night 6,000.00 | none | Auto-approve path | CL-08 |
| TC-BE-012 | POL-002 | Boundary | Hotel Mumbai 1 night 6,000.01 | `LIMIT_BREACH` POL-002, `hotel.max_per_night.tier1` | `AUTO_REJECTED` | CL-07 |
| TC-BE-013 | POL-002 | Boundary | Hotel Mumbai 2026-11-23 → 2026-11-25 (2 nights) 12,000.00 (= 6,000 × 2) | none | Auto-approve path | backbone §4.2 |
| TC-BE-014 | POL-002 | Boundary | Same stay 12,000.01 (> 6,000 × 2; no per-night rounding) | `LIMIT_BREACH` POL-002 | `AUTO_REJECTED` | backbone §4.2; limit/actual shown per N-04 |
| TC-BE-015 | POL-002 | Edge | City `bengaluru`, 1 night 6,000.00 | none (tier-1 alias) | Auto-approve path | ASM-006 |
| TC-BE-016 | POL-002 | Edge | City `  NEW DELHI ` (spaces, upper case), 1 night 5,800.00 | none (trimmed, alias) | Auto-approve path | ASM-006, backbone §4.2 |
| TC-BE-017 | POL-002 | Positive | Parametrised `Kolkata`, `Delhi`, `Chennai`, `Bangalore`, `mumbai` at 6,000.00/night | none | Auto-approve path | CL-08 |
| TC-BE-018 | POL-002 | Negative | Check-out = check-in (0 nights) or before check-in | — | API-007/008 → `400 VALIDATION_FAILED` | backbone §4.4 |
| TC-BE-019 | POL-002 | Edge | Hotel check-in Saturday 2026-11-21 → 2026-11-23, Pune 7,000.00 | `WEEKEND_FLAG` (expenseDate = check-in) | Routed (§4) | ASM-S-03, ASM-001 |
| TC-BE-020 | POL-003 | Positive | Hotel Pune 1 night 3,500.00 | none | Auto-approve path | CL-08 |
| TC-BE-021 | POL-003 | Boundary | Hotel Pune 1 night 4,000.00 | none | Auto-approve path | — |
| TC-BE-022 | POL-003 | Boundary | Hotel Pune 1 night 4,000.01 | `LIMIT_BREACH` POL-003, `hotel.max_per_night.other` | `AUTO_REJECTED` | CL-07 |
| TC-BE-023 | POL-003 | Negative | Hotel Pune 1 night 6,000.00 | `LIMIT_BREACH` POL-003 | `AUTO_REJECTED` | CL-08 |
| TC-BE-024 | POL-003 | Boundary | Hotel Pune 2026-11-23 → 2026-11-26 (3 nights) 12,000.00 / 12,000.01 | none / `LIMIT_BREACH` | Auto-approve path / `AUTO_REJECTED` | backbone §4.2 |
| TC-BE-025 | POL-003 | Edge | City `Navi Mumbai` (not listed, not an alias), 1 night 5,000.00 | `LIMIT_BREACH` POL-003 | `AUTO_REJECTED` | CL-08, ASM-006 |
| TC-BE-030 | POL-004 | Positive | Local cab 1,999.99 | none | Auto-approve path | — |
| TC-BE-031 | POL-004 | Boundary | Local cab 2,000.00 | none | Auto-approve path | — |
| TC-BE-032 | POL-004 | Boundary | Local cab 2,000.01 | `LIMIT_BREACH` POL-004, `local_cab.max_per_trip` | `AUTO_REJECTED` | CL-07 |
| TC-BE-033 | POL-004 | Edge | Two cab lines same date 1,500.00 each | none (per line = per trip) | Auto-approve path | backbone §4.2 |
| TC-BE-040 | POL-005 | Boundary | Meals 500.00, no receipt | not a result | Submit accepted, auto-approve path | ASM-003 |
| TC-BE-041 | POL-005 | Boundary | Meals 500.01, no receipt | validation (not stored) | `422 RECEIPT_REQUIRED` with per-line details; state stays `DRAFT` | ASM-003, T-02 |
| TC-BE-042 | POL-005 | Positive | Meals 500.01 with receipt | none | Auto-approve path | — |
| TC-BE-043 | POL-005 | Edge | Local cab 2,000.01, no receipt | receipt check runs before policy | `422 RECEIPT_REQUIRED`, not `AUTO_REJECTED` | backbone §3.3 step 2 |
| TC-BE-044 | POL-005 | Edge | Save draft with a 900.00 line and no receipt | — | Draft saved (receipt only checked at submit) | Ref SPEC-Q-10 default |
| TC-BE-050 | POL-006 | Positive | Earlier submitted line of same claimant: vendor `TST Cafe Aroma`, 800.00, 2026-11-24. New claim: same vendor, amount, date (different receipt no. and file) | `DUPLICATE_FLAG` POL-006; actual = matching claim number and line | Routed, flag visible; never auto-approved | ASM-004 |
| TC-BE-051 | POL-006 | Negative | Same but amount 800.01 | none | Auto-approve path | — |
| TC-BE-052 | POL-006 | Negative | Same but date 2026-11-25 / vendor `TST Cafe Aroma Express` | none | Auto-approve path | — |
| TC-BE-053 | POL-006 | Edge | Vendor `  tst   cafe AROMA ` (normalised); earlier line in the **same** claim; earlier line in a `DRAFT`/`AUTO_REJECTED`/`REJECTED` claim (no flag); another employee's line (no flag) | per backbone §4.3 | — | **Blocked by SPEC-Q-05** (proposed ASM-S-02) |
| TC-BE-060 | POL-007 | Positive | Same claimant: `receipt-meal-001.pdf` on an earlier line; new line uploads byte-identical `taxi-final-renamed.pdf` | `DUPLICATE_FLAG` POL-007 (SHA-256) | Routed | B-07, ASM-004 |
| TC-BE-061 | POL-007 | Negative | Different bytes, same file name | none | Auto-approve path | B-07 |
| TC-BE-062 | POL-007 | Edge | Hash match against **another** employee's earlier line | `DUPLICATE_FLAG` per §4.3 | Routed | **Blocked by SPEC-Q-05** |
| TC-BE-070 | POL-008 | Positive | Meals 800.00 on Saturday 2026-11-21 | `WEEKEND_FLAG` | `PENDING_MANAGER` (total ≤ 25,000 still routed) | CL-02, ASM-001 |
| TC-BE-071 | POL-008 | Positive | Meals 800.00 on Sunday 2026-11-22 | `WEEKEND_FLAG` | `PENDING_MANAGER` | ASM-001 |
| TC-BE-072 | POL-008 | Boundary | Friday 2026-11-20 and Monday 2026-11-23 | none | Auto-approve path | ASM-001 |
| TC-BE-073 | POL-008 | Edge | Expense date Friday 2026-11-20, submitted on a Saturday | none (line date counts) | Auto-approve path | ASM-001 |
| TC-BE-074 | POL-008 | Edge | Weekend meals 1,500.01 | `LIMIT_BREACH` + `WEEKEND_FLAG` | `AUTO_REJECTED` (breach first, never routed) | §2.7 order |
| TC-BE-075 | POL-008 | Edge | Public holiday on a weekday | — | — | Out of scope (Q-13 open) |
| TC-BE-080 | POL-009 | Positive | Same claimant: earlier line 2026-11-24, receipt no. `TST-R-1001`; new line same date + receipt no., different vendor and amount | `DUPLICATE_FLAG` POL-009 | Routed | CL-05, ASM-004 |
| TC-BE-081 | POL-009 | Negative | Same receipt no., date 2026-11-25 | none | Auto-approve path | CL-05 |
| TC-BE-082 | POL-009 | Negative | Same date, receipt no. `TST-R-1002` | none | Auto-approve path | CL-05 |
| TC-BE-083 | POL-009 | Edge | ` tst-r-1001 ` vs `TST-R-1001` (trim, upper-case); repeat in the same claim; match across employees | `DUPLICATE_FLAG` per §4.3 | Routed | **Blocked by SPEC-Q-05** |
| TC-BE-090 | Combined | Edge | Meals 1,600.00 + hotel Pune 1 night 4,500.00 | two `LIMIT_BREACH` | `AUTO_REJECTED`; reason lists **both** lines | CL-07 |
| TC-BE-091 | Combined | Edge | Breach line + duplicate-flag line | `LIMIT_BREACH` + `DUPLICATE_FLAG` | `AUTO_REJECTED`, no `approval_steps` rows | §2.7 order |
| TC-BE-092 | Combined | Boundary | Duplicate-flag line, total 25,000.00 | `DUPLICATE_FLAG` | `PENDING_MANAGER` | ASM-004 |
| TC-BE-093 | Fields | Negative | On API-007/008: amount 0.00, -100.00, 10.005; missing category/date/vendor/receipt no.; expenseDate in the future; vendor 121 chars; receipt no. 61 chars; hotel without city/check-in/check-out; unknown JSON field | — | `400 VALIDATION_FAILED`, `details.fields` names each field | backbone §4.4, §8 |
| TC-BE-094 | Fields | Edge | Category other than `MEALS`/`HOTEL`/`LOCAL_CAB` | `400` under default | — | **Blocked by SPEC-Q-02** |
| TC-BE-095 | Fields | Edge | Line ≤ 500 without receipt number | `400` under default | — | Ref SPEC-Q-15 default |
| TC-BE-096 | Submit | Negative | Submit a claim with no lines | — | `422 CLAIM_HAS_NO_LINES`; state unchanged | T-02, §3.3 |

## 4. Approval routing and threshold matrix

JUnit (`TC-BE-1nn`) tests the chain engine; rows marked **INT** also run end to end as `TC-INT-1nn` with the seed personas (§8.1) and the claim fixtures (§8.2). Totals use only in-limit weekday lines unless stated. Step lists show `step type (status)`.

| ID | Scenario | Expected steps | State after submit → after actions | Ref / blocked | INT |
|---|---|---|---|---|---|
| TC-BE-101 | Clean, total 24,999.99 | none | `AUTO_APPROVED` → `APPROVED` or `SAMPLE_AUDIT_PENDING` | CL-02, CL-03 | Yes |
| TC-BE-102 | Clean, total 25,000.00 | none | as 101 | CL-03 | Yes |
| TC-BE-103 | Clean, total 25,000.01 | `MANAGER (PENDING)`, `FINANCE_AUDITOR (WAITING)` | `PENDING_MANAGER` → manager approves → `PENDING_AUDITOR` → any auditor approves → `APPROVED` | CL-03, T-08/T-09 | Yes |
| TC-BE-104 | Clean, total 99,999.99 | `MANAGER`, `FINANCE_AUDITOR` | as 103 | CL-03 | Yes |
| TC-BE-105 | Clean, total 1,00,000.00 | `MANAGER`, `FINANCE_AUDITOR` | as 103, no finance head | CL-03 | Yes |
| TC-BE-106 | Clean, total 1,00,000.01 | `MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD` | `PENDING_MANAGER` → `PENDING_AUDITOR` → `PENDING_FINANCE_HEAD` → `APPROVED` | CL-03 | Yes |
| TC-BE-107 | Weekend line, total 800.00 | `MANAGER` | `PENDING_MANAGER` → approve → `APPROVED` (no sample draw) | CL-02, T-09 | Yes |
| TC-BE-108 | Weekend line, total 30,000.00 | `MANAGER`, `FINANCE_AUDITOR` | as 103 | CL-02 | Yes |
| TC-BE-109 | Duplicate flag, total 800.00 | `MANAGER` | `PENDING_MANAGER`; flag in API-006 | ASM-004 | Yes |
| TC-BE-110 | No manager (`EMP-NM`), total 30,000.00 | `MANAGER (SKIPPED)` + `STEP_SKIPPED` audit, `FINANCE_AUDITOR` | `PENDING_AUDITOR` | CL-09 | Yes |
| TC-BE-111 | No manager, total 1,50,000.00 | `MANAGER (SKIPPED)`, `FINANCE_AUDITOR`, `FINANCE_HEAD` | `PENDING_AUDITOR` → `PENDING_FINANCE_HEAD` → `APPROVED` | CL-09 | Yes |
| TC-BE-112 | No manager, weekend line, total 3,000.00 | `MANAGER (SKIPPED)`, `FINANCE_AUDITOR` **added** | `PENDING_AUDITOR` → `APPROVED` | backbone §5.2 rule 3 (was SPEC-Q-01) | Yes |
| TC-BE-113 | Finance head claimant, total 1,50,000.00 | exactly one `FINANCE_AUDITOR` | `PENDING_AUDITOR` → auditor approves → `APPROVED`; never `PENDING_FINANCE_HEAD` | CL-16 | Yes |
| TC-BE-114 | Finance head claimant, weekend line, total 800.00 | exactly one `FINANCE_AUDITOR` | `PENDING_AUDITOR` | CL-16 | Yes |
| TC-BE-115 | Finance head claimant, clean, total 800.00 | one `FINANCE_AUDITOR` (proposed) | `PENDING_AUDITOR`, never auto-approved (proposed) | **Blocked by SPEC-Q-14** | — |
| TC-BE-116 | Finance auditor claimant (`FA-1`), total 1,50,000.00 | `MANAGER` (assigned `FH-1`), `FINANCE_AUDITOR (SKIPPED)`, `FINANCE_HEAD` | `PENDING_MANAGER` → `PENDING_FINANCE_HEAD` → `APPROVED` | ASM-009, §5.2 | Yes |
| TC-BE-117 | Finance auditor claimant, total 30,000.00 | `MANAGER`, `FINANCE_AUDITOR (SKIPPED)`, `FINANCE_HEAD` **added** | `PENDING_MANAGER` → `PENDING_FINANCE_HEAD` → `APPROVED` | ASM-009, §5.2 rule 3; see N-02 | Yes |
| TC-BE-118 | Claimant calls API-015/016/017 on own visible claim | — | Rejected, state unchanged; code per N-03 (`422 SELF_APPROVAL_FORBIDDEN` expected) | B-02 | Yes |
| TC-BE-119 | Visible but wrong actor: `MGR-1` (acted at manager step) calls API-015 at `PENDING_AUDITOR`; a finance auditor calls API-015 on a `PENDING_FINANCE_HEAD` claim they approved earlier | — | `403 NOT_CURRENT_APPROVER`; state unchanged | backbone §5.3 | Yes |
| TC-BE-120 | Send-back restart: 30,000.00 claim, manager approves, auditor sends back with comment; claimant resubmits unchanged | old steps `SUPERSEDED`; new `MANAGER (PENDING)`, `FINANCE_AUDITOR`; new submission number | `SENT_BACK` → `PENDING_MANAGER` | CL-12, T-11, T-02 | Yes |
| TC-BE-121 | Send-back; claimant lowers total to 24,000.00, clean | policy re-run; no steps | auto-approve path | CL-12, B-05 | Yes |
| TC-BE-122 | Send-back from `PENDING_MANAGER`; total raised to 1,20,000.00 | `MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD` | `PENDING_MANAGER` | CL-12, B-05 | Yes |
| TC-BE-123 | Send-back; claimant edits a line to meals 1,500.01 | policy re-run | `AUTO_REJECTED` | CL-12, CL-07 | Yes |
| TC-BE-124 | Send-back without / with empty comment | — | `422 COMMENT_REQUIRED` (proposed) | **Blocked by SPEC-Q-03** | — |
| TC-BE-125 | Reject with comment at `PENDING_MANAGER`, `PENDING_AUDITOR`, `PENDING_FINANCE_HEAD` | current step `REJECTED` | `REJECTED`; `CLAIM_REJECTED` with comment; then API-015 → `409 STATE_CONFLICT` | CL-04, T-10 | Yes |
| TC-BE-126 | Reject with missing or empty comment | — | `422 COMMENT_REQUIRED`; state unchanged | CL-04 | Yes |
| TC-BE-130 | Delegation active: `MGR-1` delegates to `FA-2` for 2026-11-27..2026-12-05 (IST); `FA-2` approves `EMP-A`'s 30,000.00 claim | unchanged | `PENDING_AUDITOR`; audit `actor_id` `FA-2`, `on_behalf_of_id` `MGR-1` | FR-008, CL-13 | Yes |
| TC-BE-131 | Window boundaries with injected clock: active at 2026-11-27 00:00 and 23:59 IST when start = end = 2026-11-27; inactive on 2026-11-26 23:59 and 2026-11-28 00:00 IST | — | Delegate may act only inside; outside, claim not visible → API-006/015 `404` (INT: expired window only) | backbone §5.4, ASM-002, ASM-007 | Partly |
| TC-BE-132 | Delegate holds only `EMPLOYEE` role | — | API-020 → `422 INVALID_DELEGATE`; API-038 does not list them | CL-13, B-03 | Yes |
| TC-BE-133 | Delegate = delegator | — | `422 INVALID_DELEGATE` | B-03 | Yes |
| TC-BE-134 | `FH-1` (manager of `FA-2`) delegates to `FA-2`; `FA-2`'s own weekend claim is at `FH-1`'s manager step; `FA-2` approves | — | `422 SELF_APPROVAL_FORBIDDEN` | B-02, CL-13 | Yes |
| TC-BE-135 | Pass-on: `MGR-1` → `FA-2` active; `FA-2` tries to cover `MGR-1`'s steps by delegating to `FH-1`; `FH-1` opens `MGR-1`'s pending claim | — | Pass-on → `422 INVALID_DELEGATE`; `FH-1` gets `404` | B-03, §5.4 | Yes |
| TC-BE-136 | Delegation never changes steps | identical steps with/without | — | §4.2 | — |
| TC-BE-137 | Second delegation by `MGR-1` overlapping 2026-12-01..2026-12-10 | — | `422 DELEGATION_OVERLAP` | §5.4 | Yes |
| TC-BE-138 | Delegator acts during own delegation window | — | `MGR-1` approve succeeds | ASM-S-04 (proposed) | Yes |
| TC-BE-139 | Pool steps need no delegation: any `FINANCE_AUDITOR` approves a `PENDING_AUDITOR` claim | — | Succeeds; API-014 of `FA-2` lists only delegated manager steps | §5.3, §7.2 | Yes |
| TC-BE-140 | Sample draw with injected source: value 0.05 at rate 0.10 | none | `SAMPLE_AUDIT_PENDING`; `SAMPLE_AUDIT_SELECTED` with drawn value in `details` | FR-011, CL-14, ASM-S-05 | — |
| TC-BE-141 | Injected value 0.50 at rate 0.10 | none | `APPROVED`; `SAMPLE_AUDIT_NOT_SELECTED` | T-07 | — |
| TC-BE-142 | Rate 1.00 (via API-035, INT): clean 800.00 claim | none | `SAMPLE_AUDIT_PENDING`; absent from API-025; included in API-026 → `422 CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` | CL-14, B-12 | Yes |
| TC-BE-143 | Auditor approves sampled claim | — | `APPROVED`; listed by API-025 | T-12 | Yes |
| TC-BE-144 | Auditor rejects sampled claim without / with comment | — | `422 COMMENT_REQUIRED` / `REJECTED` | T-13, CL-04 | Yes |
| TC-BE-145 | Sampled claim of `FA-1`; `FA-1` approves | — | Rejected (code per N-03); sampled claim not in `FA-1`'s API-022 | T-12 guard, §7.2 | Yes |
| TC-BE-146 | `sample_audit.rate` = 0 or 1.01 via API-035 | — | `400 VALIDATION_FAILED` | backbone §4.1 | Yes |
| TC-BE-147 | Batch and export: `APPROVED` claims → API-026 → API-028; then an already-batched claim in a second batch | — | `IN_PAYOUT_BATCH` with `CLAIM_ADDED_TO_BATCH` per claim → `PAID` with `CLAIM_PAID`; second batch → `422 CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` | B-09, T-14/15 | Yes |
| TC-BE-148 | Config change in flight: 30,000.00 claim in `PENDING_MANAGER`; API-035 sets `approval.auditor_threshold` = 50000.00 | steps unchanged; claim keeps its `config_version` | Manager approves → `PENDING_AUDITOR` | B-08 | Yes |
| TC-BE-149 | Bulk approve (`FA-1`, API-023): 3 `PENDING_AUDITOR` claims + 1 `APPROVED` claim + 1 own claim id | — | `200`; 3 `APPROVED` outcomes, others `FAILED` with `STATE_CONFLICT` / `SELF_APPROVAL_FORBIDDEN`; each claim in its own transaction | §7.2, FR-010 | Yes |
| TC-BE-150 | CSV writer: values starting `=`, `+`, `-`, `@`, tab, CR neutralised; text quoted; narration cut to 30 chars; amounts `27450.00`; file name `EXPENSEFLOW_PAYOUT_<batchNumber>_<YYYYMMDD>.csv` | — | Exact bytes match `csv/expected-payout-sample.csv` | §2.8, §13.4, backbone §8 | — |

## 5. API test coverage (pytest, `tests/backend/`)

Legend: text = required case; "—" = not applicable. 401 = no token and garbled/expired token → `UNAUTHENTICATED`. Every error body is checked for `{code, message, details, traceId}` and no stack trace; every response has `X-Trace-Id`. Lists check `{items, page, pageSize, total}`, `pageSize` > 100 → 400. Money fields are 2-decimal strings. IDs per §2.

| API | Happy path | 400 `VALIDATION_FAILED` | 401 | 403 | 404 `NOT_FOUND` | 409 `STATE_CONFLICT` | 413/415/422/405/429/other |
|---|---|---|---|---|---|---|---|
| API-001 `GET /health` | 200 app + database up, at `/api/v1/health` | — | — (public) | — | — | — | 405 on POST |
| API-002 `POST /auth/login` | 200 token; `LOGIN_SUCCEEDED` | missing fields | wrong password → 401; `LOGIN_FAILED` | — | — | — | 429 `RATE_LIMITED` (**Blocked by SPEC-Q-13**) |
| API-003 `GET /me` | 200 role + `isManager`/`isApprover`/`canDelegate`/`isAdmin` correct for each persona (§8.1) | — | yes | — | — | — | No password hash |
| API-004 `GET /claims` | 200 only own claims, for every role incl. `FINANCE_HEAD` | bad paging | yes | — | — | — | CL-15 |
| API-005 `POST /claims` | 201 `DRAFT`, `claimNumber` `CLM-YYYY-NNNNN`; `CLAIM_CREATED` | unknown field `status` (mass assignment) | yes | — | — | — | — |
| API-006 `GET /claims/{id}` | 200 claimant; current-step approver/delegate; any auditor for `PENDING_AUDITOR`/`SAMPLE_AUDIT_PENDING`; past actor; `allowedActions` correct | malformed id | yes | — | another employee's claim; `FH-1` on an unrelated `DRAFT`/`APPROVED` claim (role alone); unknown id; delegate after window | — | §7 visibility rule |
| API-007 add line | 201; `LINE_ADDED` | TC-BE-093 field rules | yes | — | other's claim | claim `PENDING_MANAGER` (B-04) | category (**Blocked by SPEC-Q-02**) |
| API-008 update line | 200; `LINE_UPDATED` | as API-007 | yes | — | other's claim; line of another claim | after submit / `APPROVED` | — |
| API-009 delete line | 204; `LINE_DELETED` | — | yes | — | other's claim | after submit | — |
| API-010 upload receipt | 201; SHA-256 stored; `RECEIPT_UPLOADED` | no file part | yes | — | other's claim | after submit | 413 `FILE_TOO_LARGE` (> 5 MB), 415 `UNSUPPORTED_FILE_TYPE` (`.txt` renamed `.pdf`, checked by content) — Ref SPEC-Q-06 default; 429 (**Blocked by SPEC-Q-13**) |
| API-011 get receipt | 200 bytes to anyone passing the visibility rule | — | yes | — | other's claim | — | No storage path in response |
| API-012 submit | 200 + resulting state per §3/§4 | — | yes | — | other's claim | resubmit when `PENDING_*`/`APPROVED`/`PAID` | 422 `RECEIPT_REQUIRED`, `CLAIM_HAS_NO_LINES`; same `Idempotency-Key` + body → same response, one transition; same key + different body → 422 `IDEMPOTENCY_KEY_REUSED` |
| API-013 claim audit | 200 ordered, from/to state, `on_behalf_of` | — | yes | — | other's claim | — | — |
| API-014 `GET /approvals` | 200 assigned + delegated `PENDING` manager steps; `PENDING_FINANCE_HEAD` for finance heads; never own claims; empty list for non-approver (`EMP-A`) | bad paging | yes | — (never 403) | — | — | — |
| API-015 approve | 200 next state | — | yes | 403 `NOT_CURRENT_APPROVER` (TC-BE-119) | not visible | already `APPROVED`/`REJECTED`; double approve | 422 `SELF_APPROVAL_FORBIDDEN`; idempotency as API-012 |
| API-016 reject | 200 `REJECTED` | — | yes | 403 `NOT_CURRENT_APPROVER` | not visible | from `REJECTED`, `PAID`, `DRAFT` | 422 `COMMENT_REQUIRED`, `SELF_APPROVAL_FORBIDDEN` |
| API-017 send-back | 200 `SENT_BACK`; `CLAIM_SENT_BACK`; comment shown to claimant in API-006 | — | yes | 403 `NOT_CURRENT_APPROVER` | not visible | from `SAMPLE_AUDIT_PENDING`, `APPROVED` (not in T-11) | comment rule (**Blocked by SPEC-Q-03**) |
| API-018 comment | 201; `COMMENT_ADDED` | empty comment | yes | visible but not current approver/past actor (e.g. claimant) → 403 | no relation to claim | — | — |
| API-019 list delegations | 200 own | — | yes | `EMP-A` (not `canDelegate`) → `FORBIDDEN` | — | — | — |
| API-020 create delegation | 201; `DELEGATION_CREATED` | end before start; missing dates | yes | `EMP-A` → `FORBIDDEN` | unknown delegate id | — | 422 `INVALID_DELEGATE`, `DELEGATION_OVERLAP` |
| API-021 cancel delegation | 204; `DELEGATION_CANCELLED` | — | yes | not the delegator | unknown id | — | — |
| API-022 review queue | 200 `PENDING_AUDITOR` + `SAMPLE_AUDIT_PENDING`, never own; filters `departmentId`, `category`, `minAmount`, `maxAmount`, `fromDate`, `toDate`, `flag`, `queue` (Ref SPEC-Q-09 default) | bad filter value | yes | `EMP-A`, `MGR-1`, `FH-1` | — | — | — |
| API-023 bulk approve | 200 per-claim `results` (TC-BE-149) | empty list; 101 ids | yes | `MGR-1`, `FH-1` (delegate or not) | — | — | Per-claim `FAILED` codes; idempotency |
| API-024 list batches | 200 | — | yes | `FA-1`, `EMP-A` | — | — | — |
| API-025 eligible claims | 200 only `APPROVED`, no batch; excludes `SAMPLE_AUDIT_PENDING` | — | yes | as API-024 | — | — | — |
| API-026 create batch | 201 `PB-YYYY-NNNN`; `PAYOUT_BATCH_CREATED`, `CLAIM_ADDED_TO_BATCH` each | empty list | yes | as API-024 | unknown claim id | — | 422 `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` (pending, sampled, paid, batched) |
| API-027 get batch | 200 rows + totals | — | yes | as API-024 | unknown id | — | — |
| API-028 export | 200 control summary (rows, total, SHA-256); claims `PAID`; `PAYOUT_BATCH_EXPORTED`, `CLAIM_PAID` | — | yes | as API-024 | unknown id | export twice | All-or-nothing |
| API-029 file | 200 CSV per §2.8; byte-identical re-download (same SHA-256) | — | yes | as API-024 | unknown id | before export | — |
| API-030 spend | 200 by category / department / month of `expenseDate`, states `APPROVED`/`IN_PAYOUT_BATCH`/`PAID` (Ref SPEC-Q-11 default) | bad date | yes | `EMP-A`, `MGR-1` | — | — | Aggregates only |
| API-031 violations | 200 counts by rule from latest submission; seed gives 160 breach, 90 duplicate, 300 weekend | bad filter | yes | `EMP-A` | — | — | — |
| API-032 approval time | 200 calendar time submit → final approval | bad date | yes | `EMP-A` | — | — | Ref SPEC-Q-08 default |
| API-033 audit events | 200 full trail | bad filter | yes | `FA-1`, `EMP-A` | — | — | PUT/DELETE → 405 `METHOD_NOT_ALLOWED` |
| API-034 policy rules | 200 all 13 keys of §4.1 | — | yes | `FA-1`, `EMP-A` | — | — | — |
| API-035 update rule | 200 new `config_version`; `POLICY_RULE_CHANGED` | wrong type/range (negative limit, rate 0, finance-head threshold ≤ auditor threshold) | yes | `FA-1`, `EMP-A` | unknown `ruleKey` | — | In-flight claim not re-routed (TC-BE-148) |
| API-036 departments | 200, 8 departments | — | yes | — | — | — | — |
| API-037 client config | 200 `receiptMandatoryAbove` "500.00", `tier1Cities`, `categories`; values follow API-035 changes | — | yes | — | — | — | No other config exposed |
| API-038 eligible delegates | 200 only `FINANCE_AUDITOR`/`FINANCE_HEAD` users, excluding caller | — | yes | `EMP-A` → `FORBIDDEN` | — | — | — |

## 6. Written end-to-end cases (Playwright, `tests/frontend/e2e/`)

Common preconditions: stack started with one command and seeded; personas from §8.1. Because the sample draw cannot be forced off over HTTP (N-01), cases that need a predictable auto-approval accept `APPROVED` **or** `SAMPLE_AUDIT_PENDING` and check the matching audit entry; TC-E2E-009 sets rate 1.00 for its own claim.

| ID | Title | Preconditions | Steps | Expected | Requirements |
|---|---|---|---|---|---|
| TC-E2E-001 | Mobile claim capture and draft | `EMP-A` logged in; viewport 390×844 | 1) New claim 2) Add meals 1,200.00 (Tue 2026-11-24), local cab 1,800.00, hotel Pune 2026-11-23→24 3,500.00 3) Upload PDF/JPG/PNG receipts 4) Save draft 5) Reload | No horizontal scroll; labels/errors linked to fields; city and dates shown only for hotel; claim `DRAFT` with 3 lines and receipts after reload | FR-001, FR-002, NFR-001 |
| TC-E2E-002 | Clean claim auto-approved; isolation | Draft from 001 | 1) Submit 2) Open claim 3) Log in as `EMP-B`, open `EMP-A`'s claim URL 4) `FH-1` opens same URL | Status `APPROVED` or `SAMPLE_AUDIT_PENDING`, audit `CLAIM_AUTO_APPROVED` + matching `SAMPLE_AUDIT_*`; `EMP-B` and `FH-1` get a not-found page | FR-003, FR-004, CL-02, FR-019, NFR-005 |
| TC-E2E-003 | Limit breach auto-rejected | `EMP-A` | 1) Claim: meals 1,600.00, hotel Mumbai 1 night 6,500.00 2) Submit | `AUTO_REJECTED`; reason lists both lines with rule, limit, actual; no approve action anywhere | FR-004, POL-001, POL-002, CL-07 |
| TC-E2E-004 | Missing receipt, then duplicate | `EMP-A` has an earlier submitted line with `receipt-meal-001.pdf` | 1) Line 800.00 without receipt → Submit 2) Attach `taxi-final-renamed.pdf` → Submit | 1: field error, stays `DRAFT`; 2: `PENDING_MANAGER`, POL-007 flag and matching claim number shown | POL-005, POL-007, ASM-003, ASM-004, B-07 |
| TC-E2E-005 | Two-level approval with receipt viewer | `EMP-A` submits 30,000.00 claim | 1) `MGR-1` opens approvals, views claim 2) Comments, approves 3) `FA-1` approves from review queue | Receipt side by side with lines; `PENDING_MANAGER` → `PENDING_AUDITOR` → `APPROVED`; comment in history | FR-005, FR-006, NFR-002, CL-03 |
| TC-E2E-006 | Three-level approval and audit trail | `EMP-A` submits 1,00,000.01 claim | 1) `MGR-1` approves 2) `FA-1` approves 3) `FH-1` approves 4) Open audit trail | Steps in order; `APPROVED`; trail lists `CLAIM_SUBMITTED`, `CLAIM_ROUTED`, `STEP_ACTIVATED`, `STEP_APPROVED` ×3, `CLAIM_APPROVED` with actors and times | FR-005, FR-018, CL-03 |
| TC-E2E-007 | Send back, restart; reject with comment | `EMP-A` 30,000.00 claim approved by `MGR-1` | 1) `FA-1` sends back with comment 2) `EMP-A` sees comment, edits a line, resubmits 3) `MGR-1` tries Reject with empty comment, then with comment | 2: `PENDING_MANAGER` again, old approval shown as superseded; 3: blocked with message, then `REJECTED` | FR-007, FR-020, CL-04, CL-12 |
| TC-E2E-008 | Delegation while on leave | `MGR-1` delegates to `FA-2` (today..+7 days, picked from API-038 list); `EMP-A` submits weekend 800.00 claim | 1) `FA-2` opens approvals 2) Approves 3) `MGR-1` tries a second overlapping delegation | Claim in `FA-2`'s list; `APPROVED`; audit shows `FA-2` on behalf of `MGR-1`; overlap rejected with a clear message; employees not offered as delegates | FR-008, CL-13, POL-008 |
| TC-E2E-009 | Review queue, bulk approve, sample audit | ≥ 3 `PENDING_AUDITOR` claims; rate 1.00 for one clean claim, then restored | 1) `FA-1` filters queue by department 2) Selects 3 rows, bulk approves 3) Opens the `SAMPLE_AUDIT_PENDING` claim, approves | Dense table filters correctly; per-row results shown; sampled claim absent from payout eligibility until approved | FR-009, FR-010, FR-011, CL-14, NFR-003 |
| TC-E2E-010 | Payout export and dashboard | ≥ 2 `APPROVED` claims (seed has 220) | 1) `FH-1` creates batch from eligible list 2) Tries download before export 3) Exports 4) Downloads twice 5) Opens dashboard | 2: blocked (409); file `EXPENSEFLOW_PAYOUT_<batchNumber>_<YYYYMMDD>.csv`, 13 columns in §2.8 order; control summary matches; claims `PAID`; downloads identical; dashboard shows spend by category/department/month, violations, average approval time | FR-012–FR-017, CL-06, CL-11 |

## 7. Security checklist (NFR-009)

Results go to `architecture/security-review.md`, each finding marked real or false alarm with a reason (§13.9).

| ID | Item | Source | How verified |
|---|---|---|---|
| TC-INT-801 | Claim splitting | B-01 | **Blocked by Q-06** — recorded as open risk |
| TC-INT-802 | Self-approval directly, as delegate, via bulk, as sample auditor | B-02 | TC-BE-118, 134, 145, 149 |
| TC-INT-803 | Delegation to self, to `EMPLOYEE`, pass-on | B-03 | TC-BE-132, 133, 135 |
| TC-INT-804 | Edit after submit/approve | B-04 | API-007/008/009/010 on `PENDING_*`, `APPROVED` → 409 |
| TC-INT-805 | Lower amount on send-back | B-05 | TC-BE-120..123 |
| TC-INT-806 | Direct API state jumps | B-06 | API-015/016/017/028/029 from every non-allowed state → 409; wrong actor → 403 `NOT_CURRENT_APPROVER` |
| TC-INT-807 | Renamed receipt reuse | B-07 | TC-BE-060, TC-E2E-004 |
| TC-INT-808 | Config change to push a claim through; non-admin edit | B-08, CL-10 | TC-BE-148; API-035 as `FA-1` → 403; `POLICY_RULE_CHANGED` present; `policy_rules` insert-only |
| TC-INT-809 | Paid/unapproved/sampled claim into a batch; one claim two batches | B-09 | TC-BE-142, 147 |
| TC-INT-810 | IDOR | B-10, FR-019 | Every claim-scoped API (006–018) with `EMP-B` token on `EMP-A`'s claim → 404; `FH-1` by role alone → 404 |
| TC-INT-811 | Audit tampering | B-11, NFR-004 | API-033 PUT/DELETE → 405; as `expenseflow_app` in the Postgres container: `UPDATE`/`DELETE` on `audit_events` denied; as `expenseflow_owner`: trigger rejects `UPDATE`/`DELETE`/`TRUNCATE`. Must run on PostgreSQL, not H2 (backbone §13) |
| TC-INT-812 | Avoiding sample audit | B-12 | TC-BE-140..146; drawn value in audit `details`; client cannot send a sample field (400) |
| TC-INT-813 | SQL injection | §13.3 | Filter params of API-022, 030, 033 with `' OR 1=1 --` → 400 or empty, never 500; review: no string-built SQL |
| TC-INT-814 | XSS | §13.3 | Vendor `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>` rendered as text in claim, queue, audit views (Playwright) |
| TC-INT-815 | CSRF / token storage | §13.3, §10.10 | Bearer header auth (no cookie); storage matches approved design |
| TC-INT-816 | Mass assignment | §13.3 | API-005/007/008/020 with `status`, `total`, `claimantId`, `sampleAuditSelected` → 400 |
| TC-INT-817 | CSV formula injection | §13.4 | TC-BE-150 |
| TC-INT-818 | Receipt upload safety | §13.5 | Content sniffing (`.txt`/`.html` renamed → 415); generated storage name; file name `../../x.pdf` harmless; API-011 404 for others |
| TC-INT-819 | Secrets | §13.6 | `git ls-files` has no `.env`; `.env.example` placeholders only; `Select-String` over `frontend/dist` finds no secrets |
| TC-INT-820 | Error leakage | §11.4 | Forced 400/404/405/500: `traceId` present, no stack trace or SQL |
| TC-INT-821 | Rate limiting | §13.8 | Login/upload bursts → 429 (**Blocked by SPEC-Q-13**) |
| TC-INT-822 | Dependency check | §13.10, NFR-007 | OWASP Dependency-Check and `npm audit` in CI; findings triaged |
| TC-INT-823 | Least privilege | §13.1 | `expenseflow_app` has no DDL; containers run as non-root |
| TC-INT-824 | Idempotency abuse | §10.5 | Key replay with different body → 422 `IDEMPOTENCY_KEY_REUSED`; replay never double-applies a transition |

## 8. Test data (`tests/test-data/`, synthetic only — DATA-004)

### 8.1 Seed relation (`database/SPEC.md` §7)

| Persona key | Demo login | Seed facts used by tests |
|---|---|---|
| `EMP-A` | `employee@expenseflow.test` | Consulting staff, reports to `MGR-1`; `isApprover` false |
| `MGR-1` | `manager@expenseflow.test` | Consulting team lead; `isManager`, `canDelegate`; role `EMPLOYEE` |
| `FA-1` | `auditor@expenseflow.test` | `FINANCE_AUDITOR`, reports to `FH-1` |
| `FA-2` | `auditor2@expenseflow.test` | `FINANCE_AUDITOR`, reports to `FH-1`; delegate target |
| `FH-1` | `financehead@expenseflow.test` | Only `FINANCE_HEAD`; no manager; `isAdmin`; manager of all auditors |
| `EMP-NM` | `nomanager@expenseflow.test` | Operations head, no manager (CL-09) |
| `EMP-B` | `nomanager@expenseflow.test` (reused) | Second claimant for isolation; not in `EMP-A`'s chain (see N-06) |

- No seeded `ADMIN` user (ASM-S-01): `ADMIN`-role behaviour (e.g. never approves chain steps) is JUnit/MockMvc only.
- `tests/test-data/personas.json` maps keys to e-mails and roles only; password from `$env:SEED_DEMO_PASSWORD`.
- Test lines use vendor prefix `TST ` and receipt-number prefix `TST-R-<runId>-`; test receipt files have unique bytes, so no test line matches a seeded line (POL-006/007/009) unless a case intends it. Test expense dates must not be in the future and avoid seed-style collisions.
- Seed checks against the `database/SPEC.md` manifest (backbone wins on any conflict): TC-INT-201 150 users, 8 departments, roles 1 `FINANCE_HEAD` / 4 `FINANCE_AUDITOR` / 145 `EMPLOYEE` / 0 `ADMIN` (DATA-001); TC-INT-202 hierarchy is a tree, only `FH-1` and the Operations and IT Support heads have no manager (DATA-002); TC-INT-203 3,000 claims, 9,000 lines, status counts per manifest, 160 breach lines (40 per POL-001..004), 90 flagged duplicate lines (30 per POL-006/007/009), 300 weekend lines, 550 `policy_results` (DATA-003); TC-INT-204 e-mails on `@expenseflow.test`, no real data (DATA-004, inspection); TC-INT-205 every user has a unique account number `^[0-9]{9,18}$` and IFSC `^[A-Z]{4}0[A-Z0-9]{6}$` (DATA-005); TC-INT-206 second seed run exits "already seeded"; a re-seeded volume gives the same rows except password hashes.

### 8.2 Files

| Path | Content | Used by |
|---|---|---|
| `personas.json` | Persona key → e-mail, role, manager key | All pytest/E2E |
| `receipts/receipt-meal-001.pdf` | Small valid synthetic PDF (< 100 KB), unique text | TC-BE-060/061, E2E |
| `receipts/taxi-final-renamed.pdf` | Byte-identical copy of `receipt-meal-001.pdf` | POL-007, B-07 |
| `receipts/receipt-meal-001-different.pdf` | Different bytes | TC-BE-061 |
| `receipts/receipt-hotel-001.jpg`, `receipt-cab-001.png` | Valid images | E2E-001, API-010 |
| `receipts/receipt-oversized.pdf` | Generated at test time to 5 MB + 1 byte (SPEC-Q-06 default); not committed | API-010 413 |
| `receipts/fake-pdf.pdf` | Plain text with `.pdf` extension | API-010 415 |
| `receipts/receipt.txt`, `receipt.html` | Disallowed types | API-010 415, TC-INT-818 |
| `claims/*.json` | One fixture per §3 row (lines + receipt refs) | §3 mirrors |
| `claims/total-24999_99.json` | Hotel Mumbai 2026-11-02→06 (4 nights) 24,000.00 + meals 2026-11-03 999.99 | TC-BE-101 |
| `claims/total-25000_00.json` / `total-25000_01.json` | Same hotel + meals 1,000.00 / 1,000.01 | TC-BE-102/103 |
| `claims/total-99999_99.json` | Hotel Mumbai 2026-11-02→18 (16 nights) 96,000.00 + meals 1,500.00 + cab 2,000.00 + cab 499.99 | TC-BE-104 |
| `claims/total-100000_00.json` / `total-100000_01.json` | As above with last cab 500.00 / 500.01 (receipt attached) | TC-BE-105/106 |
| `csv/payout-header.csv`, `csv/expected-payout-sample.csv` | 13-column header (§2.8); expected bytes for TC-BE-150 | TC-E2E-010, API-029, TC-BE-150 |

Check-in dates above are Mondays, so hotel `expenseDate` (= check-in, ASM-S-03) is a weekday.

## 9. Performance (Locust, `tests/load/`)

**No targets are set.** CLAUDE.md §2.5: any latency or throughput target must be agreed and recorded in §6 first. Until then runs are **report-only** and cannot pass or fail. Page-latency scope is SPEC-Q-12.

| ID | Scenario (user mix) | Calls | Measured |
|---|---|---|---|
| TC-INT-901 | Employee browsing | API-002, 003, 004, 006 | p50/p95/p99 response time, requests/s, error rate per endpoint |
| TC-INT-902 | Employee capture + submit | API-005, 007, 010, 012 | As above; upload time; submit pipeline time |
| TC-INT-903 | Approver queue | API-014, 006, 011, 015 | As above |
| TC-INT-904 | Auditor queue + bulk approve | API-022 (filters), 023 | As above; batch size vs time |
| TC-INT-905 | Dashboard | API-030, 031, 032 | As above on the 3,000-claim seed |
| TC-INT-906 | Payout | API-025, 026, 028 | Export time vs batch size |

Also read during each run: `http_server_requests_seconds`, `expenseflow_approval_queue_size{step}`, `expenseflow_oldest_pending_approval_seconds{step}` (NFR-008; series present with 0 when empty). Store Locust CSVs in `tests/load/results/` with date, user count and commit hash; record in `PROGRESS.md`.

## 10. Validation and exit criteria

| §19 item | Evidence | `validation-matrix.xlsx` row(s) |
|---|---|---|
| FR-001..020, POL-001..009 validated; CL-01..CL-18 honoured | §3–§6 cases Pass | One row per FR/POL/CL; `Test Case ID(s)` lists cases |
| Frontend–backend end to end | TC-E2E-001..010 | FR rows, method `Test` |
| Every API vs contract (API-001..038) | §5 + contract test in CI | Rows by FR |
| Error handling (400/401/403/404/405/409/413/415/422) | §5 columns | NFR-005, FR-019 |
| Security incl. B-01..B-12 | §7, `security-review.md` | NFR-009 |
| Audit trail append-only | TC-INT-811 | FR-018, NFR-004 |
| Seed loaded | TC-INT-201..206 | DATA-001..005 |
| One-command seeded demo; CI green | Phase 7 run log | NFR-006, NFR-007 (`Demonstration`) |
| Metrics, dashboard, alert, runbook | Phase 8 inspection | NFR-008 (`Inspection`) |
| Mobile, side-by-side, dense tables | TC-E2E-001, 005, 009 + `TC-FE-` | NFR-001..003 |

**Exit:** every Must-have row `Validated`; no open Critical/High `BUG-###` on the demo path; demo dry run twice without intervention. A row whose only cases are Blocked stays `Partially Validated` and names the blocker.

**Recording rules:** write the **actual** result only after running (§14.3); fill `Actual Result`, `Status`, `Executed By`, `Executed On`; log every run in `PROGRESS.md` with the command, date and passed/failed/blocked counts; never delete or weaken a failing test — raise a `BUG-###`.

## 11. Ordered task list

| # | Phase | Task | Done when |
|---|---|---|---|
| 1 | 2/3 | Review this spec; get answers to SPEC-Q-02/03/05/13/14 and N-01..N-06 | Blocked list agreed; spec v0.3 |
| 2 | 3 | Create `tests/test-data/` files (§8.2) and `personas.json` | Files committed; no real data |
| 3 | 4a | Write JUnit `PolicyEngineTest` for TC-BE-001..096 (non-blocked) **before** the engine | `.\mvnw.cmd test` runs, policy cases **fail** (red); logged in `PROGRESS.md` |
| 4 | 4a | Implement policy engine until green | All policy JUnit cases pass; no limit literal in engine code (grep) |
| 5 | 4b | JUnit chain/delegation/sample/CSV tests TC-BE-101..150 first (injected clock and random source), then engines | Red then green, logged |
| 6 | 4c | Move `HealthControllerTests` to `/api/v1/health` (backbone §8) as part of the API-001 change | Test asserts the backbone path |
| 7 | 4c | pytest API suite (§5) per endpoint as built | Every API-001..038 row covered; green on fresh stack |
| 8 | 4c | pytest integration: TC-INT-001..096, TC-INT-1nn (INT rows), TC-INT-201..206 | Green on fresh seeded PostgreSQL stack |
| 9 | 5 | Vitest component tests (`TC-FE-`) | `npx vitest run` green |
| 10 | 6 | Playwright TC-E2E-001..010 | All 10 Pass on the demo machine |
| 11 | 6 | Security checklist §7 and dependency checks | `security-review.md` updated; real findings fixed or logged |
| 12 | 6 | Locust runs §9 (report only) | CSVs stored; numbers in `PROGRESS.md` |
| 13 | 6 | Fill `test-cases.xlsx` and `validation-matrix.xlsx` (delete example rows) | Summary sheets show real counts; §10 exit met |

## 12. Open questions referenced

Blocking cases: **SPEC-Q-02** (categories), **SPEC-Q-03** (send-back comment), **SPEC-Q-05** (duplicate scope — proposed §4.3), **SPEC-Q-14** (finance head's clean claim), SPEC-Q-13 (rate limits), Q-06 (splitting), Q-13 (holidays). Tested on proposed defaults (re-check when answered): SPEC-Q-04, 06, 07, 08, 09, 10, 11, 12, 15; ASM-S-01..05; ASM-001, 003, 004, 005, 006, 007, 008, 009, 010.

## 13. Issues in architecture/SPEC.md

### 13.1 Issues raised on v0.1 — status in v0.2

| ID | Issue (short) | Status |
|---|---|---|
| I-01 | Finance auditor's own claim 25,000.01–1,00,000 | **Resolved** — §5.2 rule 3 adds the next level (TC-BE-117) |
| I-02 | API-006 open to finance head/admin by role | **Resolved** — claim visibility rule §7 |
| I-03 | Pool vs individual steps undefined | **Resolved** — §5.1, §5.3 |
| I-04 | "Approvers" not a role | **Resolved** — capabilities via API-003 |
| I-05 | Finance head's clean claim ≤ 25,000 | **Open as SPEC-Q-14** — TC-BE-115 Blocked |
| I-06 | Hotel nights, rounding, expense date | **Resolved** — §4.2, §4.4, ASM-S-03 |
| I-07 | Duplicate matching within claim, normalisation | **Resolved as proposal** (§4.3, ASM-S-02); still under SPEC-Q-05 |
| I-08 | 400 vs 422; claim with no lines | **Resolved** — §4.4, `CLAIM_HAS_NO_LINES` |
| I-09 | Code for visible-but-wrong actor | **Resolved** — `403 NOT_CURRENT_APPROVER` (see N-03 for the claimant case) |
| I-10 | Idempotency replay | **Resolved** — §8, `IDEMPOTENCY_KEY_REUSED` |
| I-11 | Bulk approve semantics | **Resolved** — §7.2, `FINANCE_AUDITOR` only |
| I-12 | Delegation dates, overlap, delegator acting | **Resolved** — §5.4, `DELEGATION_OVERLAP`, ASM-S-04 |
| I-13 | Sample-audit meaning and test control | **Resolved for JUnit** — per-claim draw, injectable source (ASM-S-05); HTTP control see N-01 |
| I-14 | Batch audit action; API-029 before export | **Resolved** — `CLAIM_ADDED_TO_BATCH`; 409 |
| I-15 | `/api/health` vs `/api/v1/health`; H2 | **Resolved in backbone** (§8 path, §13 H2 note); existing test to be moved (task 6) |
| I-16 | CLAUDE.md §16.3 403 vs 404; §19 omits CL-18 | **Noted for owner** (backbone §13); tests use 404 |
| I-17 | Rate limits | **Open as SPEC-Q-13** — 429 cases Blocked |
| I-18 | No 405 | **Resolved** — `METHOD_NOT_ALLOWED` |
| I-19 | No database spec / personas | **Resolved** — `database/SPEC.md` §7 demo logins (gap: N-06) |

### 13.2 Remaining issues found in v0.2

| ID | Issue | Affects |
|---|---|---|
| N-01 | `sample_audit.rate` must be > 0 and the draw uses `SecureRandom`; the random source is injectable only in unit tests. HTTP and E2E tests can force selection (rate 1.00) but never non-selection, so any auto-approved claim may land in `SAMPLE_AUDIT_PENDING`. Needs a test-profile source, or acceptance that tests tolerate both outcomes | §3, §6, demo |
| N-02 | Finance auditors report to the finance head (`database/SPEC.md` §7.1). A finance auditor's own claim > 25,000 then has a `MANAGER` step assigned to the finance head and an added `FINANCE_HEAD` step that only the same person can approve — one person approves two levels. Acceptable? | TC-BE-116, 117 |
| N-03 | Claimant calling API-015/016/017 on their own (visible) claim: `422 SELF_APPROVAL_FORBIDDEN` or `403 NOT_CURRENT_APPROVER`? §5.3 fits both. Same for a finance auditor approving their own `SAMPLE_AUDIT_PENDING` claim | TC-BE-118, 145 |
| N-04 | For hotel breaches, which "configured limit" and "actual value" are stored — per night or × nights? | TC-BE-012..024 |
| N-05 | Sample draw boundary (selected when value < rate, or ≤?) not stated | TC-BE-140/141 |
| N-06 | Seed has one plain demo employee; isolation tests (B-10) need a second loginable employee outside `EMP-A`'s chain (tests reuse `nomanager@`, which is also the CL-09 persona). Clock control for delegation boundaries over HTTP is not available (TC-BE-131 boundary in JUnit only) | §8.1, TC-INT-810 |
| N-07 | §7.2: can API-023 bulk-approve `SAMPLE_AUDIT_PENDING` claims (T-12), given the review queue lists them? | API-023, TC-E2E-009 |
