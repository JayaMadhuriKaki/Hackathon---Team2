# Test Coverage Report — ExpenseFlow

Date: 2026-11-27 · Status values are actual: nothing below the frontend row has been executed by QA.

## By requirement

| Requirement | Written cases (xlsx) | Automated test location | Executed | Result |
|---|---|---|---|---|
| FR-001..003 capture, draft, submit | Yes | `tests/backend/test_claims_capture.py`; Vitest screens; E2E stack TC-E2E-001 | Vitest only | Pass (frontend) / Not run (API) |
| FR-004, POL-001..009 policy engine | Yes | JUnit `policy/*`; `tests/backend/test_policy_rules.py` | No | Not run |
| FR-005, CL-02/03/09/16 routing + thresholds | Yes | JUnit `ApprovalChainEngineTest`; `test_routing_and_approvals.py` | No | Not run |
| FR-006/007/020 comment, send-back, reject | Yes | `test_routing_and_approvals.py`; UI-03 | UI-03 only | Pass (UI mocked) |
| FR-008 delegation | Yes | JUnit `DelegationRulesTest`; `test_delegation.py` | No | Not run |
| FR-009..011 review queue, bulk, sample | Yes | `test_review_queue_and_sample.py` | No | Not run |
| FR-012..014 payout + CSV | Yes | `test_payout.py` | No | Not run |
| FR-015..017 dashboard | Yes | `test_admin_dashboard_audit.py` | No | Not run |
| FR-018 / NFR-004 audit append-only | Yes | `test_admin_dashboard_audit.py`; `tests/integration/test_audit_append_only_db.py` | No | Not run |
| FR-019 / NFR-005 isolation | Yes | `test_claims_capture.py` (404 for claim, receipt, audit) | No | Not run |
| NFR-001..003 mobile, side-by-side, tables | Yes | Playwright UI-01..06 | Yes | **Pass** |
| NFR-007 CI | — | — | — | Gap (DEF-001) |
| NFR-009 security | Checklist | `architecture/security-review.md` | Static | Partial |

## By level

| Level | Written | Executed | Passed |
|---|---|---|---|
| Frontend unit (Vitest) | 63 | 63 | 63 |
| UI E2E mocked (Playwright) | 6 | 6 | 6 |
| Backend unit (JUnit) | 131 (team) | 0 by QA | – |
| API / business rules / negative / boundary / authz (pytest) | 169 | 0 | – |
| DB consistency (pytest) | 11 | 0 | – |
| Full-stack E2E (Playwright) | 11 | 0 | – |
| Workbook cases | 131 | 0 | – |

Requirement coverage by written tests: 100 % of FR-001..020 and POL-001..009. Coverage by passing tests against the running system: 0 %.
