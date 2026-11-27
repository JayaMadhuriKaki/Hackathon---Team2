# Test Coverage Report — ExpenseFlow

Date: 2026-11-27. Results are actual runs only. Full per-requirement detail is in `deliverables/validation-matrix.xlsx` (84 rows) and `deliverables/requirement-traceability.xlsx` (column "Test Case ID(s)").

## By requirement group

| Requirements | Written tests | Executed and passed | Status |
|---|---|---|---|
| FR-001–003, 021, 022 capture, draft, submit, line fields | Workbook + `test_claims_capture.py` + Vitest | Vitest, UI-04 | Partially validated |
| FR-004, 023, BR-001–010 policy engine | Workbook + JUnit `policy/*` + `test_policy_rules.py` | TC-FE-061 only | Not validated (backend not run) |
| FR-005, BR-011–016 routing, thresholds | Workbook + JUnit chain + `test_routing_and_approvals.py` | – | Not validated |
| FR-006, 007, 020, 025, BR-018, 019 comment, send-back, reject, resubmit | `test_routing_and_approvals.py`, UI-03 | UI-03, TC-FE-071 | Partially validated (FR-020, BR-018) |
| FR-008, BR-017, 020, 021 delegation | JUnit `DelegationRulesTest`, `test_delegation.py` | TC-FE-018, 110 | Partially validated (FR-008) |
| FR-009–011, 024, BR-022, 023 review queue, bulk, sample audit | `test_review_queue_and_sample.py` | TC-FE-017, 090, 091 | Partially validated (FR-009, 010) |
| FR-012–014, BR-024–026, 031, NFR-019 payout and CSV | `test_payout.py` | TC-FE-100 | Partially validated (FR-012–014) |
| FR-015–017 dashboard | `test_admin_dashboard_audit.py` | TC-FE-141 | Partially validated (FR-017) |
| FR-018, NFR-004, BR-029 audit append-only | `test_admin_dashboard_audit.py`, `integration/test_audit_append_only_db.py` | TC-FE-120 | Partially validated (UI only) |
| FR-019, 031, NFR-005, BR-028 visibility | `test_claims_capture.py`, `test_routing_and_approvals.py` | TC-FE-060 | Partially validated |
| FR-026, NFR-011, BR-027, 030 policy config | `test_admin_dashboard_audit.py` | – | Not validated |
| FR-027–030 seed data | Workbook only | – | **Blocked (DEF-006)** |
| FR-032 roles | `test_auth_and_errors.py` | TC-FE-040..042 | Partially validated |
| NFR-001–003, 020 mobile, side-by-side, tables, accessibility | Playwright UI-01..06 | 6 / 6 | Partially validated (mocked API) |
| NFR-021 money | Vitest TC-FE-001..004 | 4 / 4 | Partially validated |
| NFR-007 CI + dependency check | – | npm audit 0 vulnerabilities | Not validated (DEF-001) |
| NFR-006, 008, 010 deployment, metrics, runbook | – | – | Not validated (later phases) |
| NFR-009, 012–014, 017 security and rules | Inspection | Static review | Not validated by test |

## By level

| Level | Written | Executed | Passed | Failed |
|---|---|---|---|---|
| Frontend unit / component (Vitest) | 63 | 63 | 63 | 0 |
| UI E2E, mocked API (Playwright) | 6 | 6 | 6 | 0 |
| Backend unit (JUnit, team-written) | 131 | 0 by QA (CI) | – | – |
| API: business rules, negative, boundary, authN/authZ (pytest) | 169 | 0 | – | – |
| Database consistency (pytest) | 11 | 0 | – | – |
| Full-stack E2E (Playwright) | 11 | 0 | – | – |
| Workbook test cases | 131 | 0 (3 blocked) | 0 | 0 |

## Totals

- Requirements with written tests: **71 / 84 (85 %)**
- Requirements validated against the running system: **0 / 84**
- Requirements partially validated (frontend evidence): **29 / 84**
