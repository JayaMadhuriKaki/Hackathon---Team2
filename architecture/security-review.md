# Security and Approval-Bypass Review

| Field | Value |
|---|---|
| Product | ExpenseFlow |
| Version | 0.2 (findings re-checked against `main` at cb8672d, backend and frontend merged; runtime checks automated, not yet run) |
| Last updated | 2026-11-27 |
| Covers | CLAUDE.md §5 (approval-bypass review), §13 (security guardrails), NFR-009 (basic web security checklist) |
| Re-run | Phase 2 (design review), Phase 6 (validation, against the running app) |

## 1. How to use this document

- **§2** is the mandatory approval-bypass list from CLAUDE.md §5 (B-01 to B-12), reviewed "as a skeptical finance auditor".
- **§3** lists further bypass paths found during this review (B-13 onward). They are **proposals**: none is a requirement until the team or owner accepts it.
- **§4** is the basic web security checklist (NFR-009).
- **§5** is the findings log. Every finding is marked **Real** or **False alarm** with a reason (CLAUDE.md §13.9).
- Test-case IDs refer to `deliverables/test-cases.xlsx`.

**Status values:** `Not testable yet` (code does not exist) · `Not started` · `Pass` · `Fail` · `Accepted risk` · `N/A`.

Current state: the backend and frontend are still the starter skeleton, so every check that needs the app is **Not testable yet**. Findings in §5 come from reading the existing configuration.

---

## 2. Approval-bypass review (CLAUDE.md §5)

| ID | Bypass attempt | Required control | Where it must be enforced | Verified by | Status |
|---|---|---|---|---|---|
| B-01 | Splitting one expense into several lines or claims to stay under a limit or threshold | Meal limit summed per employee per day across claims (ASM-013); hotel limit summed per night (ASM-016); duplicate checks. **Residual risk:** splitting one large expense into several claims under ₹25,000 is not detected (Q-06 open) | Policy engine | TC-BE-005, TC-BE-007, TC-BE-026 | Partly automated (`test_meals_summed_per_day_across_claims`); split detection itself open (Q-06) |
| B-02 | Approving your own claim (directly, as delegate, or as admin) | Backend rejects any approve/reject/send-back where actor = claimant, at every step, including bulk approve and the sample-audit review | Approval service (not the UI) | TC-E2E-006 (delegate), TC-E2E-008 (finance head's claim). **Gaps:** add API tests for bulk approve containing own claim, and auditor approving own sampled claim | Automated, not yet run |
| B-03 | Delegating to yourself, to your own claimant, to an unauthorised role, or creating delegation chains | Delegate must be a finance auditor or finance head (CL-13), not the delegator, not the claimant; a delegate cannot pass on delegated work; dates checked at action time on the server | Delegation service | TC-E2E-006 (role, self, expiry). **Gaps:** add tests for delegation chain and delegate = claimant | Automated, not yet run |
| B-04 | Editing a claim after it has been submitted or approved | Lines locked outside `DRAFT` and `SENT_BACK`; edit API returns 409 | Claim service | TC-BE-092, TC-E2E-004 | Automated, not yet run |
| B-05 | Changing the amount during send-back to fall below a threshold | Policy re-run and chain recalculated on every resubmission; resubmitted claim never auto-approved (ASM-021, owner confirmation pending) | Claim + policy service | TC-BE-089, TC-BE-090, TC-BE-091, TC-E2E-004 | Automated, not yet run |
| B-06 | Calling an API directly to skip a step or change state | All transitions checked against the state machine on the server; status/outcome fields never accepted from the client; actor must be the approver of the **current** step | State machine in service layer | TC-BE-093. **Gap:** add a test where the auditor approves a claim still at the manager step (expect 403/409) | Automated, not yet run |
| B-07 | Re-using a receipt with a renamed file | Duplicate check on SHA-256 of file content (POL-007) | Receipt upload + policy engine | TC-BE-049, TC-E2E-007, TC-INT-001 | Automated, not yet run |
| B-08 | Changing policy config to approve a pending claim | Config edit = finance head/admin only (CL-10), audited; in-flight claims keep the chain stored at submit | Config service; claim stores its chain | TC-BE-097, TC-BE-098, TC-E2E-010 | Automated, not yet run |
| B-09 | Adding an already-paid or unapproved claim to a payout batch | Only `APPROVED` claims not in a batch; one claim, one batch (unique DB constraint) | Payout service + DB constraint | TC-BE-104, TC-E2E-009 | Automated, not yet run |
| B-10 | Reading another employee's claim by changing an ID in the URL | Object-level check on every read: claims, line items, **receipt files**, comments, audit history; 404 when not visible | Every controller/service read path | TC-E2E-002, TC-BE-044. **Gaps:** receipt download, audit-history and list endpoints | Automated, not yet run |
| B-11 | Tampering with or deleting audit entries | No update/delete code path; app DB user has INSERT/SELECT only on the audit table | Code + DB grants | TC-BE-111, TC-E2E-010 (incl. direct DB attempt). See finding F-01 | Automated, not yet run |
| B-12 | Avoiding the 10% sample audit | Selection made on the server with a seeded, logged draw; sampled claims blocked from payout until reviewed | Policy/sample service + payout service | TC-BE-104, TC-BE-108, TC-BE-109, TC-INT-003 | Automated, not yet run |

### 2.1 Automated checks (pytest, `tests/`) — added 2026-11-27

All of these run against the Docker Compose stack (`pytest tests -q`). They have been written and collect cleanly, but **have not run yet** because Docker was not running on the QA laptop. The test-case gaps listed in the table above are now covered as shown.

| ID | pytest test(s) |
|---|---|
| B-02 | `test_routing_and_approvals.py::test_claimant_cannot_approve_own_claim`; `test_delegation.py::test_delegation_never_allows_self_approval`; `test_review_queue_and_sample.py::test_auditor_cannot_clear_own_sampled_claim`, `::test_queue_never_shows_auditors_own_claims` |
| B-03 | `test_delegation.py::test_invalid_delegates_are_refused` (plain employee, self), `::test_employee_without_reports_cannot_delegate`, `::test_only_delegator_can_cancel`, `::test_cancelled_delegation_removes_access` |
| B-04 | `test_claims_capture.py::test_lines_locked_after_submit` (add, edit, delete, upload, resubmit → 409) |
| B-05 | `test_routing_and_approvals.py::test_send_back_restarts_chain_from_manager`, `::test_send_back_then_lower_amount_reruns_policy`, `::test_send_back_then_breach_is_auto_rejected` |
| B-06 | `test_routing_and_approvals.py::test_wrong_approver_cannot_act`, `::test_double_approve_is_409`; `test_review_queue_and_sample.py::test_bulk_approve_reports_per_claim_results` (claim still at the manager step fails); `test_claims_capture.py::test_mass_assignment_unknown_field_is_400` |
| B-07 | `test_policy_rules.py::test_pol007_renamed_receipt_with_same_bytes_is_flagged` |
| B-08 | `test_admin_dashboard_audit.py::test_policy_rules_are_admin_only`, `::test_config_change_is_audited_and_does_not_reroute_in_flight_claims`; DB: `test_app_role_cannot_rewrite_insert_only_tables` |
| B-09 | `test_payout.py::test_unapproved_claim_cannot_be_batched`, `::test_batch_export_marks_paid_and_csv_matches_format` (second batch, export twice) |
| B-10 | `test_claims_capture.py::test_other_users_get_404_on_someone_elses_draft` (claim, **receipt file**, **audit history**; employee, manager, finance head), `::test_other_employee_cannot_change_or_submit`, `::test_claim_list_shows_only_own_claims` |
| B-11 | `test_admin_dashboard_audit.py::test_audit_trail_has_no_update_or_delete_path`; `tests/integration/test_audit_append_only_db.py` (app role denied, owner blocked by trigger) |
| B-12 | `test_review_queue_and_sample.py::test_sampled_claim_is_held_from_payout_until_audited`, `::test_sample_draw_is_recorded_in_audit` |
| B-13, B-14 | `test_audit_append_only_db.py::test_app_role_cannot_change_bank_details_or_hierarchy` |
| §13.4 CSV | `test_payout.py::test_batch_export_marks_paid_and_csv_matches_format` checks no cell starts with `= + - @ TAB CR`. The demo users have no such names, so the neutralising code itself is only proven by JUnit/H2 (`ApiTestSupport` seeds `=Eve Employee`) |

---

## 3. Further bypass paths found in this review (proposed — need team decision)

These are not in CLAUDE.md. They are listed so the team can accept, reject or defer each one. Accepted items should be added to CLAUDE.md §5 by the owner.

| ID | Bypass attempt | Why it matters | Proposed control | Decision |
|---|---|---|---|---|
| B-13 | **Redirecting a payout:** changing an employee's bank account or IFSC just before a batch is exported | Money goes to the wrong account although every approval was correct | Employees cannot edit bank details; only admin can, audited; the export reads details at export time and the audit entry records them | Partly mitigated: the app role has SELECT only on `users` (V005), so bank details cannot change through the app; checked by `test_app_role_cannot_change_bank_details_or_hierarchy`. Admin path still undecided (Q-20) |
| B-14 | **Changing the reporting line:** admin points a claimant at a friendly manager, or removes the manager so CL-09 skips the step | Avoids the real manager without breaking any rule | Hierarchy changes admin-only and audited; chain stored at submit so later changes do not re-route in-flight claims | Partly mitigated as B-13 (no write path in the app) |
| B-15 | **One person approving two steps of the same claim** (e.g. auditor acting as manager's delegate, then approving the auditor step) | Defeats the purpose of a multi-level chain | A user may act on at most one step of a claim; the next step skips them as in ASM-009 | Open |
| B-16 | **Finance head raises a limit, submits own claim, lowers it again** (CL-10 allows the edit) | Conflict of interest; every step looks legitimate | Audit entry is already required; show config changes on the dashboard. Optional: config changes cannot be made by someone with a claim in progress | Open |
| B-17 | **Race conditions / replay:** double-click approve, approve and send-back at the same time, exporting a batch twice | Claim approved twice or paid twice | State check and update in one transaction with optimistic locking (version column); idempotency key on submit, approve, bulk approve, export (CLAUDE.md §10.5) | Open — partly covered by TC-E2E-001 and TC-E2E-008 double-click steps |
| B-18 | **Editing the CSV after export** before it is uploaded to the bank | Amount or account changed outside the system | SHA-256 control summary (§2.8) recorded in the audit trail so the file can be checked later | Covered by §2.8; record the hash in audit — Open |
| B-19 | **Bulk-approving through the API** claims the UI would not allow (flagged claims, own claims, claims at another step) | UI checks alone can be bypassed | Bulk approve applies the same server checks as single approve, claim by claim; whole request rejected or per-claim result returned | Open (ASM-023) |
| B-20 | **Entering a weekday date for weekend spend** or a fake receipt image | Weekend flag and receipt rule depend on what the employee types/uploads | Not preventable by rules; relies on the side-by-side receipt viewer and the 10% sample audit | Accepted risk (proposed) |

---

## 4. Basic web security checklist (NFR-009)

| ID | Area | Check | How to verify | Verified by / tool | Status |
|---|---|---|---|---|---|
| SEC-01 | Authentication | Every non-public endpoint returns 401 without a valid token | Call each operation in the contract with no token / expired token | pytest API suite | Partly automated (`test_protected_endpoint_needs_valid_token` on `/me`); full per-endpoint matrix not yet written |
| SEC-02 | Authentication | Passwords stored with BCrypt; no plain-text or reversible storage | Inspect `users` table and code | Code review | Not run |
| SEC-03 | Authentication | JWT signature verified, `alg: none` rejected, expiry enforced, signing secret from env var | Send tampered, unsigned and expired tokens | pytest | Not run |
| SEC-04 | Authentication | Login and upload endpoints rate-limited (429) | Burst requests | pytest / Locust | Not run |
| SEC-05 | Authorization | Role check on every endpoint (403 for wrong role) | Matrix test: each role × each operation (`x-roles` in contract) | pytest | Automated for dashboard, audit, payout, policy rules, review queue, delegation; not yet run |
| SEC-06 | Authorization | Object-level checks (IDOR) on claims, lines, receipts, comments, audit history | Use user B's token on user A's IDs → 404 | pytest; B-10 | Automated, not yet run |
| SEC-07 | Authorization | No mass assignment: client cannot set `status`, `claimantId`, `outcome`, `approvedBy`, `batchId` | Send extra fields | pytest; TC-BE-093 | Automated, not yet run |
| SEC-08 | Input validation | Bean Validation on every DTO; unknown fields rejected; amounts > 0, 2 decimals | Invalid payloads | TC-BE-082 to TC-BE-088 | Automated, not yet run |
| SEC-09 | Injection | No SQL built by string concatenation; payloads like `' OR 1=1--` in filters and search are harmless | Code search for native queries with `+`; send payloads to queue filters | Code review + pytest | Not run |
| SEC-10 | XSS | Vendor names, comments and city with `<script>`/`<img onerror>` are displayed as text | Store payload, view in every screen; search code for `dangerouslySetInnerHTML` | Playwright + code review | Not run |
| SEC-11 | CSRF | If JWT is sent in the `Authorization` header and no auth cookie exists, CSRF does not apply; if cookies are used, CSRF tokens required | Inspect auth design (Q-22 open) | Design review | Not started |
| SEC-12 | Path traversal | Receipts fetched by generated ID only; `../` in any parameter has no effect | Request `../../etc/passwd` style IDs | pytest | Not run |
| SEC-13 | File upload | Maximum size enforced (413) | Upload over the limit (limit pending Q-21) | pytest | Automated, not yet run |
| SEC-14 | File upload | Type allow-list checked by file content, not extension (415) | Upload `.exe` renamed `.pdf`; HTML file renamed `.jpg` | pytest | Automated, not yet run |
| SEC-15 | File upload | Files stored outside the web root with generated names; SHA-256 stored | Inspect volume and DB | TC-INT-001 | Not run |
| SEC-16 | File download | Receipts served with correct `Content-Type`, `X-Content-Type-Options: nosniff`, and `Content-Disposition` | Inspect response headers | pytest | Not run |
| SEC-17 | CSV export | Values starting with `=`, `+`, `-`, `@`, tab, CR neutralised (§13.4) | Export a batch containing EMP-T05 | TC-E2E-009 | Partly automated (no demo user has a formula-like name; see §2.1) |
| SEC-18 | Error handling | No stack traces or internal messages in responses; standard `{code, message, details, traceId}` shape | Trigger 400/404/500 | pytest | Automated (every error checked by `assert_error`), not yet run |
| SEC-19 | Secrets | No secrets committed; `.env` git-ignored; `.env.example` has placeholders only | `git log -p` search, gitleaks (if approved) | Manual + CI | Not started (see F-02) |
| SEC-20 | Secrets | No secrets or internal URLs in the frontend bundle | Search `frontend/dist` after build | Manual | Not run |
| SEC-21 | Headers | nginx sends `X-Content-Type-Options`, `X-Frame-Options`/CSP `frame-ancestors`, `Referrer-Policy`, a basic CSP | `curl -I http://localhost:3000` | Manual | Not started (see F-07) |
| SEC-22 | CORS | Only the frontend origin allowed; no `*` with credentials | Preflight from another origin | pytest | Not started |
| SEC-23 | Database | App DB user is not a superuser; no UPDATE/DELETE on the audit table (NFR-004) | `\du` and table grants in psql | Manual; TC-E2E-010 step 11 | Not started (see F-01) |
| SEC-24 | Monitoring | Actuator exposes only health/info/prometheus; metrics not reachable by end users | Call `/actuator/*` through the frontend and directly | Manual | Not started (see F-05) |
| SEC-25 | Logging | No passwords, tokens or receipt contents in logs; trace ID on every line | Review logs after a full E2E run | Manual | Not run |
| SEC-26 | Dependencies | CI runs OWASP Dependency-Check (Maven) and `npm audit`; no known critical vulnerabilities (NFR-007) | CI log | CI | Not started (see F-04) |
| SEC-27 | Containers | Images pinned to exact versions; app containers run as non-root | Inspect Dockerfiles and compose | Manual | Not started (see F-03) |
| SEC-28 | Session | Logout or token expiry ends access; tokens not stored where injected scripts can read them unless the auth design accepts it (CLAUDE.md §10.10) | Inspect storage after login; reuse token after logout | Playwright | Not run |

---

## 5. Findings log

Findings first logged on the starter template, re-checked on 2026-11-27 against `main` (cb8672d). Severity: Critical / High / Medium / Low.

| ID | Finding | Location | Severity | Real or false alarm | Reason | Proposed fix | Status |
|---|---|---|---|---|---|---|---|
| F-01 | The backend connects to Postgres as the `postgres` superuser, so the app could update or delete audit rows (breaks NFR-004 / B-11) | `docker-compose.yml`, `.env.example`, `application.properties` | High | Real | Superuser ignores table grants | Separate app user created by a Flyway/init script with INSERT/SELECT only on the audit table; superuser used only for migrations | **Fixed in code** — app runs as `expenseflow_app` (V005: SELECT/INSERT only on `audit_events`), Flyway as owner, triggers block UPDATE/DELETE/TRUNCATE (V004). Verification automated in `tests/integration/test_audit_append_only_db.py`, not yet run |
| F-02 | Default credentials `postgres/postgres` and Grafana `admin/admin` used as fallbacks | `docker-compose.yml`, `.env.example` | Low | Real, accepted for local demo | Values are placeholders for a local, synthetic-data stack and are not secrets; risk only if reused outside the laptop | Keep for demo; README note "change before any shared deployment" | Open — also `SEED_DEMO_PASSWORD` and `DB_APP_PASSWORD` defaults in `docker-compose.yml` |
| F-03 | Prometheus and Grafana images use `:latest` | `docker-compose.yml` | Medium | Real | Breaks "use exact versions" event rule (§8.2) and makes builds non-repeatable | Pin exact versions | Open — `prom/prometheus:latest`, `grafana/grafana:latest` |
| F-04 | CI has no dependency vulnerability check, no pytest and no frontend tests | `.github/workflows/ci.yml` | Medium | Real | NFR-007 requires a dependency check on every push | Add OWASP Dependency-Check, `npm audit`, pytest and Vitest jobs (Phase 7) | Open — CI still runs only `mvnw verify`, lint, build and `docker compose build` |
| F-05 | `/actuator/prometheus` is published on host port 8080 without authentication | `application.properties`, `docker-compose.yml` | Low | Real, low impact | Metrics reveal internals but no personal data; the stack is local | Do not publish port 8080 outside the compose network for the demo, or restrict actuator to the internal network | Open |
| F-06 | `spring.jpa.hibernate.ddl-auto=update` lets the app change the schema at runtime | `application.properties` | Medium | Real | Requires DDL rights for the app user (conflicts with F-01 fix) and CLAUDE.md requires Flyway | Switch to Flyway migrations and `ddl-auto=validate` | **Fixed** — `ddl-auto=validate`, Flyway V001–V005 |
| F-07 | nginx sends no security headers | `frontend/nginx.conf` | Low | Real | Missing clickjacking and MIME-sniffing protection | Add headers listed in SEC-21 | Open — only the receipt/CSV download sets `X-Content-Type-Options` |
| F-08 | nginx default request body limit (1 MB) will reject larger receipt uploads with its own 413 page | `frontend/nginx.conf` | Low | Real (functional) | Body limit not set; upload limit not yet decided (Q-21) | Set `client_max_body_size` to match the agreed upload limit | **Fixed** — nginx `client_max_body_size 6m`, backend 5 MB per file (413 tested in `test_claims_capture.py`) |

Not findings (checked, OK):
- Backend container runs as a non-root `app` user (`backend/Dockerfile`).
- `.env` is git-ignored; no real secrets found in the repository.
- Spring Security with JWT is now in place; SEC-01 to SEC-05 are covered by `tests/backend/test_auth_and_errors.py` (not yet run).

## 6. Open questions raised by this review

| # | Question | Related |
|---|---|---|
| SQ-1 | Who may change employee bank details and the reporting hierarchy, and are such changes audited? | B-13, B-14, Q-20 |
| SQ-2 | May one person act on two steps of the same claim (e.g. as delegate and in their own role)? | B-15 |
| SQ-3 | What is the receipt upload size limit and allowed file types? | SEC-13, SEC-14, F-08, Q-21 |
| SQ-4 | How do users sign in (affects CSRF and token storage)? | SEC-11, SEC-28, Q-22 |
