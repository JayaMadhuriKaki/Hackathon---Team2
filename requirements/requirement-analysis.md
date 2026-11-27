# Requirement Analysis — ExpenseFlow

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals (AI Manthan Hackathon, Track A, Team 2) |
| Version | 1.1 — **approved baseline** |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Status | Approved by the project owner on 2026-11-27 (CL-19). Changes need a new version and approval. |

## 1. Summary

| Item | Count |
|---|---|
| Functional requirements (FR) | 32 (FR-001–FR-020 unchanged from CLAUDE.md; FR-021–FR-032 new) |
| Non-functional requirements (NFR) | 21 |
| Business rules (BR) | 31 |
| User stories (US) | 46 (US-001–US-042 from the team workbook; US-043–US-046 new) |
| Acceptance criteria | 106 |
| Open questions | 29 open — **6 critical**; 4 resolved by approval |
| Assumptions awaiting confirmation | 10 |

**Key finding:** the team's earlier `deliverables/user-stories.xlsx` contradicted several confirmed decisions (auto-approve ceiling, limit-breach outcome, payout CSV, delegates). On approval these were resolved in favour of the confirmed decisions (CL-20) and the workbook was corrected. The team's `architecture/SPEC.md` (v0.3) already follows the confirmed decisions; its open questions are merged into `open-questions.md`.

## 2. Sources analysed

| Document | Location | Role in this analysis |
|---|---|---|
| requirements.docx | requirements/ | **Source of truth** — problem statement, personas, must-have scope, policy rules, synthetic data, phase deliverables. Identical in Drive and GitHub. |
| use-cases.docx (v1.0) | requirements/ (Drive; pending in GitHub PR) | Use cases UC-001–UC-015. Written from requirements.docx and CL decisions, so it confirms structure but adds no new requirements. GitHub `main` still holds the blank template. |
| AI_Manthan_Hackathon___Prerequisites_Guide.docx | requirements/ | Event rules and approved tool versions (NFR-012–NFR-014). |
| requirement-analysis.md (template) | requirements/ | Replaced by this document. |
| CLAUDE.md §2.6, §6 | repository root | Owner-confirmed clarifications CL-01–CL-18, open questions and assumptions. |
| user-stories.xlsx | deliverables/ | Team's 42 stories — IDs reused; conflicts recorded and corrected. |
| architecture/SPEC.md and layer SPECs | repository | Team's draft specs (v0.3) — checked for consistency; open questions merged (SPEC-Q → OQ). |

## 3. Actors and responsibilities

| Actor | Type | Responsibilities | Must never |
|---|---|---|---|
| Employee | Primary | Create, save, submit and resubmit claims; upload receipts; respond to send-backs; track own claims | See other employees' claims (FR-019); approve own claims |
| Line manager | Primary | First approval step: approve, reject with comment, send back, comment, delegate while on leave | Approve own claims; see claims not assigned to them (CL-15) |
| Finance auditor | Primary | Second step above 25,000 INR; review queue with filters and bulk approve; sample audits; approve the finance head's claims; can be a delegate | Approve own claims |
| Finance head (also admin) | Primary | Third step above 1,00,000 INR; policy configuration; payout batches and export; dashboard; can be a delegate | Approve own claims |
| ExpenseFlow system | System | Policy evaluation, routing, sample selection, audit trail, CSV generation, metrics | Act without an audit entry |
| Support engineer / team | Delivery role | Metrics, alert, runbook, CI, demo environment (phase deliverables) | — (not a product persona; see OQ in open-questions.md) |

## 4. User journeys and workflows

**J1 — Employee claims expenses** (UC-001, UC-002, UC-014): create claim → add lines (category, amount, date, vendor, receipt number; hotel: city and nights) → upload receipts → save draft → submit → see outcome (auto-approved / auto-rejected with reasons / routed) → track status.

**J2 — Approval chain** (UC-003): claim reaches manager → review lines beside receipts → approve → (if total > 25,000) finance auditor approves → (if total > 1,00,000) finance head approves → Approved.

**J3 — Send-back and resubmission** (UC-004, UC-005): approver sends back with comment → employee edits → resubmits → policy re-runs → chain restarts at manager.

**J4 — Rejection** (UC-006): approver rejects with mandatory comment → claim Rejected (final).

**J5 — Delegation** (UC-008): approver sets a finance auditor or finance head as delegate for the leave period → delegate acts on the approver's steps.

**J6 — Auditor queue** (UC-009, UC-010): auditor filters queue → reviews → bulk approves; reviews sampled auto-approved claims → approve (eligible for payout) or reject.

**J7 — Payout** (UC-011): finance head creates batch from approved, non-held claims → exports CSV → claims Paid.

**J8 — Oversight** (UC-012, UC-013, UC-015): dashboard (spend, violations, approval time); audit trail; policy configuration.

**J9 — Operations** (phase deliverables): one-command seeded start → metrics → Grafana dashboard → alert on oldest pending approval → runbook.

Claim workflow (states implied by the sources): Draft → Submitted → Auto-Approved / Auto-Rejected / Pending (Manager → Auditor → Finance Head) → Approved → In Payout Batch → Paid; plus Sent Back, Rejected and Sample Audit Pending. Final state names are an architecture decision (team uses CLARIFICATION_REQUESTED and IN_PAYOUT).

## 5. Business rules and decision conditions

See `business-rules.md` for all 31 rules (BR-001–BR-031) and the two decision tables (claim outcome; approval chain).

## 6. Mandatory and optional fields

| Entity | Field | Mandatory? | Source |
|---|---|---|---|
| Claim | Claimant | Yes (system) | FR-001 |
| Claim | Line items | At least one to submit | FR-001 |
| Claim | Total | Calculated | BR-013 |
| Line item | Category | Yes | FR-001 (full list open: OQ-032) |
| Line item | Amount (INR) | Yes, > 0 | FR-001; currency OQ-033 |
| Line item | Expense date | Yes | FR-001; future dates OQ-031 |
| Line item | Vendor | Yes | FR-021 (needed by BR-006) |
| Line item | Receipt number | Yes — for lines without a receipt open (OQ-045) | FR-021, CL-05 |
| Line item | Receipt file | Mandatory above 500 INR; optional at or below | BR-005 |
| Hotel line | City | Yes | FR-022, CL-08 |
| Hotel line | Nights (or check-in/check-out dates) | Yes | FR-022, ASM-005 |
| Approval action | Comment — reject | **Mandatory** | BR-018 |
| Approval action | Comment — send back | Open (OQ-034) | — |
| Approval action | Comment — approve / general comment | Optional | FR-006 |
| Delegation | Delegate | Yes (finance auditor or finance head) | BR-020 |
| Delegation | Leave start and end dates | Yes (ASM-007) | BR-021 |
| Payout batch | Claims | At least one | FR-012 |
| Employee (seed) | Department, manager, role, bank account, IFSC | Yes | FR-027–FR-030 |
| Policy configuration | Limits, tier-1 cities, thresholds, sample rate | Yes | BR-030 |

## 7. Input and output data

| Direction | Data | From / to | Requirements |
|---|---|---|---|
| Input | Claim and line details | Employee | FR-001, FR-021, FR-022 |
| Input | Receipt files (hashed) | Employee | FR-001, BR-007 |
| Input | Approval decisions, comments, send-backs, rejections | Approvers | FR-005–FR-007, FR-020 |
| Input | Delegations | Approvers | FR-008 |
| Input | Policy configuration changes | Finance head/admin | FR-026 |
| Input | Seed data (employees, departments, hierarchy, claims, bank details) | Seed process | FR-027–FR-030 |
| Output | Policy outcome and per-line reasons | Employee, approvers | FR-004, FR-023 |
| Output | Approval queues and review queue | Approvers, auditor | FR-009, FR-031 |
| Output | Payout CSV (bank file) and control summary | Finance head → bank (mock) | FR-013, BR-031 |
| Output | Dashboard aggregates | Finance auditor, finance head | FR-015–FR-017 |
| Output | Audit trail | Finance head; claim history to participants | FR-018 |
| Output | Metrics and alerts | Prometheus / Grafana | NFR-008 |

## 8. Exception scenarios

| # | Scenario | Expected handling | Requirements |
|---|---|---|---|
| E-01 | Missing or invalid field (amount ≤ 0, missing date) | Field error; input kept; nothing saved | FR-001, NFR-016 |
| E-02 | Receipt of disallowed type or size | Upload refused with message | NFR-018, OQ-021 |
| E-03 | Line above 500 INR with no receipt at submission | Open: blocked (ASM-003) or auto-reject | BR-005, OQ-028 |
| E-04 | Limit breach | Claim auto-rejected with reasons | BR-010 |
| E-05 | Duplicate detected | Flag; consequence open | BR-006/007/009, OQ-030 |
| E-06 | Approver acts on a claim already actioned | Refused as out of date | FR-005 |
| E-07 | Approver is the claimant | Cannot approve; step skipped (ASM-009) | BR-017 |
| E-08 | Claimant has no manager / is finance head | Next level / finance auditor | BR-015, BR-016 |
| E-09 | Reject without comment | Refused | BR-018 |
| E-10 | Invalid delegate or overlapping delegation | Refused | BR-020, ASM-007 |
| E-11 | User requests another user's claim | Not found; no data revealed | FR-019, NFR-005 |
| E-12 | Held, unapproved or batched claim added to payout | Refused | BR-024 |
| E-13 | Export fails | No state change | FR-013, FR-014 |
| E-14 | Non-admin edits policy configuration or creates a batch | Access refused | BR-026, BR-027 |
| E-15 | Bulk approve includes ineligible claims | Those skipped and listed; others approved | FR-010 |
| E-16 | Attempt to change or delete an audit entry | Refused by application and database | NFR-004 |

## 9. Dependencies and external integrations

| Dependency | Type | Notes |
|---|---|---|
| Bank (mock) | External — file only | No live integration; CSV export per BR-031 |
| Receipt file storage | Internal infrastructure | Local Docker volume (AWS not allowed, NFR-013) |
| PostgreSQL 18 | Infrastructure | Docker container (NFR-014) |
| Prometheus and Grafana | Infrastructure | Docker containers (NFR-008) |
| CI (GitHub Actions or local script) | Tooling | Tests and dependency check (NFR-007) |
| Synthetic seed data | Data | Hierarchy, departments, roles and bank details needed by routing and payout (FR-027–FR-030) |
| Policy configuration | Data | Needed before any claim can be evaluated (BR-030) |
| Org hierarchy | Data | Approval routing depends on it (BR-014, BR-015) |
| Sign-in method | Decision | Needed for all role-based features (OQ-022) |
| Event rules | Constraint | Synthetic data, free tools, code location (NFR-012, NFR-013) |

## 10. Security and compliance requirements

- **Access control:** own-claim visibility and approver scope (FR-019, FR-031, BR-028, NFR-005); role-restricted actions (BR-026, BR-027); no self-approval (BR-017); delegate restrictions (BR-020).
- **Integrity:** append-only audit trail (FR-018, BR-029, NFR-004); claims locked after submit (OQ-041); sampled claims held from payout (BR-023).
- **Input handling:** server-side validation and authorisation of all input (NFR-016); upload limits and content checks (NFR-018); CSV formula-injection protection (NFR-019).
- **Secrets:** environment variables only; nothing committed (NFR-017).
- **Compliance (event rules):** synthetic data only; free/open-source tools; no Jira or company AWS; code stays local or in company Git (NFR-012, NFR-013).
- **Verification:** security checklist run with findings triaged (NFR-009); approval-bypass review in CLAUDE.md §5.

## 11. Ambiguities and conflicts — summary

29 open questions are listed in `open-questions.md`. The **6 critical** ones block the rules they affect:

| ID | Question |
|---|---|
| OQ-028 | When a line above 500 INR has no receipt: is submission blocked (ASM-003) or is the claim auto-rejected (team US-013)? |
| OQ-029 | Does the vendor + amount + date duplicate rule compare against the same employee's lines only (team US-015), or everyone's? |
| OQ-030 | What happens to a claim with a duplicate flag: routed to an approver with the flag (source says 'flagged'; ASM-004) or auto-rejected (team US-014)? |
| OQ-032 | What is the full list of expense categories? Do categories other than meals, hotel and local cab have any limit? |
| OQ-046 | Does a finance head's clean claim of 25,000 INR or less go to a finance auditor (CL-16), or can it be auto-approved (CL-02)? |
| OQ-047 | Finance auditors report to the finance head, so an auditor's own claim above 1,00,000 INR would have the finance head on both the manager step and the finance-head step. May one person approve two levels? |

Ten conflicts were found between the team's `user-stories.xlsx` and documented or confirmed positions (table in `open-questions.md`). Four were resolved on approval (CL-20); the rest remain open questions.

## 12. Traceability matrix — Requirement → User story → Acceptance criteria

### Functional requirements

| Requirement | Summary | User stories | Acceptance criteria |
|---|---|---|---|
| FR-001 | An employee can create a claim containing several line items; each line records category, … | US-003, US-005 | US-003-AC1, US-003-AC2, US-003-AC3, US-005-AC1, US-005-AC2, US-005-AC3 |
| FR-002 | An employee can save a claim as a draft and return to edit it. | US-004 | US-004-AC1, US-004-AC2, US-004-AC3 |
| FR-003 | An employee can submit a claim. | US-006 | US-006-AC1, US-006-AC2, US-006-AC3 |
| FR-004 | A policy engine checks every line of a submitted claim and returns one of three outcomes: … | US-006, US-010 | US-006-AC1, US-006-AC2, US-006-AC3, US-010-AC1, US-010-AC2, US-010-AC3 |
| FR-005 | Claims routed for approval pass through an approval chain: manager, then finance auditor a… | US-018, US-021, US-022 | US-018-AC1, US-018-AC2, US-018-AC3, US-021-AC1, US-021-AC2, US-022-AC1, US-022-AC2 |
| FR-006 | Approvers can comment on a claim. | US-020 | US-020-AC1 |
| FR-007 | Approvers can send a claim back to the employee for clarification. | US-008, US-019 | US-008-AC1, US-008-AC2, US-008-AC3, US-019-AC1, US-019-AC2 |
| FR-008 | Approvers can delegate their approvals while on leave. | US-023 | US-023-AC1, US-023-AC2, US-023-AC3, US-023-AC4 |
| FR-009 | A finance auditor works a review queue with filters. | US-026 | US-026-AC1, US-026-AC2 |
| FR-010 | A finance auditor can bulk approve claims from the review queue. | US-027 | US-027-AC1, US-027-AC2 |
| FR-011 | A random 10% of auto-approved claims are flagged for sample audit. | US-028 | US-028-AC1, US-028-AC2, US-028-AC3 |
| FR-012 | Approved claims go into a payout batch. | US-029 | US-029-AC1, US-029-AC2, US-029-AC3 |
| FR-013 | A payout batch exports as a CSV file in a mock bank-file format. | US-030 | US-030-AC1, US-030-AC2, US-030-AC3 |
| FR-014 | Claims in an exported payout batch move to Paid. | US-030 | US-030-AC1, US-030-AC2, US-030-AC3 |
| FR-015 | A dashboard shows spend by category, by department and by month. | US-031 | US-031-AC1, US-031-AC2 |
| FR-016 | The dashboard shows policy violations. | US-032 | US-032-AC1, US-032-AC2 |
| FR-017 | The dashboard shows average approval time. | US-032 | US-032-AC1, US-032-AC2 |
| FR-018 | An append-only audit trail records every action. | US-006, US-007, US-020, US-033, US-044 | US-006-AC1, US-006-AC2, US-006-AC3, US-007-AC1, US-007-AC2, US-020-AC1, US-033-AC1, US-033-AC2, US-044-AC1, US-044-AC2 |
| FR-019 | An employee can see only their own claims. | US-001, US-002, US-007 | US-001-AC1, US-001-AC2, US-001-AC3, US-002-AC1, US-002-AC2, US-002-AC3, US-007-AC1, US-007-AC2 |
| FR-020 | An approver can reject a claim outright with a mandatory comment. | US-018 | US-018-AC1, US-018-AC2, US-018-AC3 |
| FR-021 | Each line item records the vendor and the receipt number. | US-003, US-043 | US-003-AC1, US-003-AC2, US-003-AC3, US-043-AC1, US-043-AC2 |
| FR-022 | Hotel lines record the city and the number of nights. | US-003, US-011 | US-003-AC1, US-003-AC2, US-003-AC3, US-011-AC1, US-011-AC2, US-011-AC3 |
| FR-023 | For every evaluated line the system records and shows the rule applied, the configured lim… | US-006, US-014 | US-006-AC1, US-006-AC2, US-006-AC3, US-014-AC1, US-014-AC2 |
| FR-024 | Claims selected for sample audit are held from payout until a finance auditor approves or … | US-028 | US-028-AC1, US-028-AC2, US-028-AC3 |
| FR-025 | An employee can edit and resubmit a claim that was sent back. | US-008 | US-008-AC1, US-008-AC2, US-008-AC3 |
| FR-026 | A finance head or admin can change policy configuration (limits, tier-1 cities, thresholds… | US-017, US-041 | US-017-AC1, US-017-AC2, US-017-AC3, US-041-AC1, US-041-AC2 |
| FR-027 | The system is seeded with 150 synthetic employees in 8 departments. | US-034 | US-034-AC1, US-034-AC2, US-034-AC3 |
| FR-028 | The seed includes a reporting hierarchy linking each employee to a manager. | US-034 | US-034-AC1, US-034-AC2, US-034-AC3 |
| FR-029 | The seed includes 3,000 historical claims, including planted duplicates and limit breaches… | US-034 | US-034-AC1, US-034-AC2, US-034-AC3 |
| FR-030 | Each seeded employee has a synthetic bank account number and IFSC code for the payout file… | US-030, US-034 | US-030-AC1, US-030-AC2, US-030-AC3, US-034-AC1, US-034-AC2, US-034-AC3 |
| FR-031 | Approvers can open the claims assigned to them (directly or as delegate) and the claims th… | US-002, US-018 | US-002-AC1, US-002-AC2, US-002-AC3, US-018-AC1, US-018-AC2, US-018-AC3 |
| FR-032 | The system identifies each user and their role (employee, line manager, finance auditor, f… | US-001 | US-001-AC1, US-001-AC2, US-001-AC3 |

### Non-functional requirements

| Requirement | Summary | User stories | Acceptance criteria |
|---|---|---|---|
| NFR-001 | Claim capture is mobile-friendly. | US-009 | US-009-AC1, US-009-AC2 |
| NFR-002 | Approvers see each receipt side by side with the claim lines. | US-025 | US-025-AC1, US-025-AC2 |
| NFR-003 | Data tables are dense but scannable and support bulk actions. | US-026 | US-026-AC1, US-026-AC2 |
| NFR-004 | The audit trail is append-only: no update or delete is possible through the application or… | US-033 | US-033-AC1, US-033-AC2 |
| NFR-005 | Data isolation: no user can read another user's claim through the UI or the API unless an … | US-002 | US-002-AC1, US-002-AC2, US-002-AC3 |
| NFR-006 | The whole system runs with Docker Compose, and a seeded demo environment starts with one c… | US-039 | US-039-AC1 |
| NFR-007 | CI runs the automated tests and a dependency vulnerability check. | US-038 | US-038-AC1 |
| NFR-008 | Metrics exist for approval queue size, approval age and page latency, with a dashboard and… | US-035, US-036 | US-035-AC1, US-036-AC1, US-036-AC2 |
| NFR-009 | A basic web security checklist is run against the code, and each finding is fixed or recor… | US-046 | US-046-AC1, US-046-AC2 |
| NFR-010 | A runbook covers start-up, health checks, the alert and recovery. | US-037 | US-037-AC1 |
| NFR-011 | Policy limits are seeded as configuration, not hard-coded. | US-017 | US-017-AC1, US-017-AC2, US-017-AC3 |
| NFR-012 | Only synthetic or public data is used; no client or confidential data on laptops or in pro… | US-034 | US-034-AC1, US-034-AC2, US-034-AC3 |
| NFR-013 | Only open-source or free tools are used; Jira and company AWS are not used; code stays on … | — (constraint: verified by review) | — |
| NFR-014 | The stack and versions are: React 19 + Vite, Spring Boot 4.1.x on JDK 21, PostgreSQL 18 in… | — (constraint: verified by review) | — |
| NFR-015 | Tests include a unit test for every policy rule, tests for approval thresholds and delegat… | US-038, US-045 | US-038-AC1, US-045-AC1, US-045-AC2, US-045-AC3 |
| NFR-016 | Every external input is validated and authorised on the server; frontend validation is nev… | US-001, US-046 | US-001-AC1, US-001-AC2, US-001-AC3, US-046-AC1, US-046-AC2 |
| NFR-017 | Secrets are never committed; configuration comes from environment variables with a placeho… | US-046 | US-046-AC1, US-046-AC2 |
| NFR-018 | Receipt uploads are limited by size and allowed file type, checked by content, stored outs… | US-005 | US-005-AC1, US-005-AC2, US-005-AC3 |
| NFR-019 | Values in the payout CSV are protected against spreadsheet formula injection. | US-030 | US-030-AC1, US-030-AC2, US-030-AC3 |
| NFR-020 | Forms and tables are keyboard-accessible, labelled and meet basic contrast requirements. | US-003, US-027 | US-003-AC1, US-003-AC2, US-003-AC3, US-027-AC1, US-027-AC2 |
| NFR-021 | Money is stored and calculated without floating-point error, and shown in INR with Indian … | US-003, US-030 | US-003-AC1, US-003-AC2, US-003-AC3, US-030-AC1, US-030-AC2, US-030-AC3 |

### Business rules

| Requirement | Summary | User stories | Acceptance criteria |
|---|---|---|---|
| BR-001 | A day's meal spend may not exceed 1,500 INR. | US-010 | US-010-AC1, US-010-AC2, US-010-AC3 |
| BR-002 | Hotel spend may not exceed 6,000 INR per night in tier-1 cities: Kolkata, Mumbai, Delhi, C… | US-011 | US-011-AC1, US-011-AC2, US-011-AC3 |
| BR-003 | Hotel spend may not exceed 4,000 INR per night in all other cities. | US-011 | US-011-AC1, US-011-AC2, US-011-AC3 |
| BR-004 | Local cab spend may not exceed 2,000 INR per trip. | US-012 | US-012-AC1, US-012-AC2, US-012-AC3 |
| BR-005 | A receipt is mandatory for a line above 500 INR. | US-006, US-013 | US-006-AC1, US-006-AC2, US-006-AC3, US-013-AC1, US-013-AC2, US-013-AC3 |
| BR-006 | A line with the same vendor, amount and date as an earlier line is flagged as a duplicate. | US-015 | US-015-AC1, US-015-AC2 |
| BR-007 | A line with the same receipt file hash as an earlier line is flagged as a duplicate. | US-005, US-014 | US-005-AC1, US-005-AC2, US-005-AC3, US-014-AC1, US-014-AC2 |
| BR-008 | Weekend spend is flagged for manager attention. | US-016 | US-016-AC1, US-016-AC2 |
| BR-009 | The combination of expense date and receipt number must be unique; a repeat is a duplicate… | US-043 | US-043-AC1, US-043-AC2 |
| BR-010 | If any line breaks a limit (BR-001 to BR-004), the whole claim is auto-rejected, with a re… | US-010, US-011, US-012 | US-010-AC1, US-010-AC2, US-010-AC3, US-011-AC1, US-011-AC2, US-011-AC3, US-012-AC1, US-012-AC2, US-012-AC3 |
| BR-011 | A claim can be auto-approved only if its total is 25,000 INR or less and it has no weekend… | US-017 | US-017-AC1, US-017-AC2, US-017-AC3 |
| BR-012 | A claim with any weekend line is always routed to the manager. | US-016 | US-016-AC1, US-016-AC2 |
| BR-013 | Thresholds use the claim total: above 25,000 INR adds the finance auditor; above 1,00,000 … | US-018, US-021, US-022 | US-018-AC1, US-018-AC2, US-018-AC3, US-021-AC1, US-021-AC2, US-022-AC1, US-022-AC2 |
| BR-014 | The chain order is manager, then finance auditor, then finance head. | US-021, US-022 | US-021-AC1, US-021-AC2, US-022-AC1, US-022-AC2 |
| BR-015 | If a step has no approver (e.g. no manager), the claim moves to the next level. | US-024 | US-024-AC1, US-024-AC2, US-024-AC3 |
| BR-016 | A finance head's own claims are approved by a finance auditor, whatever the amount. | US-024 | US-024-AC1, US-024-AC2, US-024-AC3 |
| BR-017 | Nobody may approve their own claim, directly or as a delegate. | US-023, US-024, US-027 | US-023-AC1, US-023-AC2, US-023-AC3, US-023-AC4, US-024-AC1, US-024-AC2, US-024-AC3, US-027-AC1, US-027-AC2 |
| BR-018 | Rejecting a claim requires a comment. | US-018 | US-018-AC1, US-018-AC2, US-018-AC3 |
| BR-019 | On resubmission after send-back, policy is re-run, the chain is recalculated from the new … | US-008 | US-008-AC1, US-008-AC2, US-008-AC3 |
| BR-020 | A delegate must be a finance auditor or a finance head. | US-023 | US-023-AC1, US-023-AC2, US-023-AC3, US-023-AC4 |
| BR-021 | Delegation applies only while the approver is on leave. | US-023 | US-023-AC1, US-023-AC2, US-023-AC3, US-023-AC4 |
| BR-022 | A random 10% of auto-approved claims are flagged for sample audit. | US-028 | US-028-AC1, US-028-AC2, US-028-AC3 |
| BR-023 | A claim flagged for sample audit cannot be paid until a finance auditor approves it; the a… | US-028 | US-028-AC1, US-028-AC2, US-028-AC3 |
| BR-024 | Only approved claims that are not held for sample audit and not already in a batch can be … | US-029 | US-029-AC1, US-029-AC2, US-029-AC3 |
| BR-025 | Claims move to Paid when their batch is exported; there is no separate payment confirmatio… | US-030 | US-030-AC1, US-030-AC2, US-030-AC3 |
| BR-026 | Payout batches are created and exported by the finance head or admin. | US-029 | US-029-AC1, US-029-AC2, US-029-AC3 |
| BR-027 | Only the finance head or admin can change policy configuration; every change is audited. | US-041 | US-041-AC1, US-041-AC2 |
| BR-028 | Each user sees their own claims; approvers additionally see claims assigned to them and cl… | US-002, US-044 | US-002-AC1, US-002-AC2, US-002-AC3, US-044-AC1, US-044-AC2 |
| BR-029 | Every action is recorded in the audit trail and can never be changed or deleted. | US-033 | US-033-AC1, US-033-AC2 |
| BR-030 | All limits, thresholds, the tier-1 city list and the sample rate are configuration values. | US-011, US-017 | US-011-AC1, US-011-AC2, US-011-AC3, US-017-AC1, US-017-AC2, US-017-AC3 |
| BR-031 | The payout CSV follows the format in CLAUDE.md §2.8: one row per claim, 13 columns. | US-030 | US-030-AC1, US-030-AC2, US-030-AC3 |

**Coverage:** every FR and BR has at least one user story and acceptance criterion. NFR-013 and NFR-014 are constraints verified by review. User stories US-040 and US-042 trace to no requirement (OQ-044).

The workbooks `deliverables/requirement-traceability.xlsx` and `deliverables/user-stories.xlsx` match these files (updated on approval).

## 13. Next steps

1. Project owner answers the 6 critical questions and confirms or corrects the assumptions.
2. Team reviews `architecture/SPEC.md` against this baseline, then the solution-architecture skill produces ADR-000, ADR-001, diagrams and the security review.
