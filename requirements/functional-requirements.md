# Functional Requirements

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals |
| Version | 1.1 — **approved baseline** (project owner, 2026-11-27) |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Companion files | requirement-analysis.md · functional-requirements.md · non-functional-requirements.md · business-rules.md · user-stories.md · open-questions.md |

Each requirement is atomic and traces to its source. IDs FR-001 to FR-020 are the same as in CLAUDE.md §2.1; FR-021 to FR-032 are added by this analysis. "Derived" means the requirement is needed to make a documented requirement work; the reason is given.

## Claims

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-001 | An employee can create a claim containing several line items; each line records category, amount (INR), expense date and a receipt upload. | requirements.docx — Must-have 1 | UC-001 | Must | US-003, US-005 |
| FR-002 | An employee can save a claim as a draft and return to edit it. | requirements.docx — Must-have 1 | UC-001 | Must | US-004 |
| FR-003 | An employee can submit a claim. | requirements.docx — Must-have 1 | UC-002 | Must | US-006 |
| FR-021 | Each line item records the vendor and the receipt number. | Derived: vendor is needed by the duplicate rule (requirements.docx); receipt number from CL-05 | UC-001 | Must | US-003, US-043 |
| FR-022 | Hotel lines record the city and the number of nights. | Derived: hotel limits are per night and by city tier (requirements.docx; CL-08); capture method ASM-005 | UC-001 | Must | US-003, US-011 |
| FR-025 | An employee can edit and resubmit a claim that was sent back. | requirements.docx — Must-have 3 (send back); CL-12 | UC-005 | Must | US-008 |

## Policy

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-004 | A policy engine checks every line of a submitted claim and returns one of three outcomes: auto-approve, route for approval, or auto-reject with a reason. | requirements.docx — Must-have 2 | UC-002 | Must | US-006, US-010 |
| FR-023 | For every evaluated line the system records and shows the rule applied, the configured limit, the actual value and the reason. | Derived: 'auto-reject with a reason' (requirements.docx); CL-07 | UC-002 | Must | US-006, US-014 |
| FR-026 | A finance head or admin can change policy configuration (limits, tier-1 cities, thresholds, sample rate) without a code change. | requirements.docx — policy rules as configuration; CL-10 | UC-015 | Must | US-017, US-041 |

## Approvals

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-005 | Claims routed for approval pass through an approval chain: manager, then finance auditor above 25,000 INR, then finance head above 1,00,000 INR. | requirements.docx — Must-have 3; CL-03 | UC-003 | Must | US-018, US-021, US-022 |
| FR-006 | Approvers can comment on a claim. | requirements.docx — Must-have 3 | UC-007 | Must | US-020 |
| FR-007 | Approvers can send a claim back to the employee for clarification. | requirements.docx — Must-have 3 | UC-004 | Must | US-008, US-019 |
| FR-008 | Approvers can delegate their approvals while on leave. | requirements.docx — Must-have 3 | UC-008 | Must | US-023 |
| FR-020 | An approver can reject a claim outright with a mandatory comment. | CL-04 | UC-006 | Must | US-018 |

## Audit queue

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-009 | A finance auditor works a review queue with filters. | requirements.docx — Must-have 4 | UC-009 | Must | US-026 |
| FR-010 | A finance auditor can bulk approve claims from the review queue. | requirements.docx — Must-have 4 | UC-009 | Must | US-027 |
| FR-011 | A random 10% of auto-approved claims are flagged for sample audit. | requirements.docx — Must-have 4 | UC-010 | Must | US-028 |
| FR-024 | Claims selected for sample audit are held from payout until a finance auditor approves or rejects them. | CL-14 | UC-010 | Must | US-028 |

## Payout

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-012 | Approved claims go into a payout batch. | requirements.docx — Must-have 5 | UC-011 | Must | US-029 |
| FR-013 | A payout batch exports as a CSV file in a mock bank-file format. | requirements.docx — Must-have 5; CL-11 | UC-011 | Must | US-030 |
| FR-014 | Claims in an exported payout batch move to Paid. | requirements.docx — Must-have 5; CL-06 | UC-011 | Must | US-030 |

## Dashboard

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-015 | A dashboard shows spend by category, by department and by month. | requirements.docx — Must-have 6 | UC-012 | Must | US-031 |
| FR-016 | The dashboard shows policy violations. | requirements.docx — Must-have 6 | UC-012 | Must | US-032 |
| FR-017 | The dashboard shows average approval time. | requirements.docx — Must-have 6 | UC-012 | Must | US-032 |

## Audit

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-018 | An append-only audit trail records every action. | requirements.docx — Must-have 7 | UC-013 | Must | US-006, US-007, US-020, US-033, US-044 |

## Access

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-019 | An employee can see only their own claims. | requirements.docx — Must-have 7 | UC-014 | Must | US-001, US-002, US-007 |
| FR-031 | Approvers can open the claims assigned to them (directly or as delegate) and the claims they have acted on. | CL-15; ASM-008 | UC-003 | Must | US-002, US-018 |
| FR-032 | The system identifies each user and their role (employee, line manager, finance auditor, finance head/admin). | Derived: personas and FR-019 require knowing who the user is; method open (OQ-022) | All | Must | US-001 |

## Data

| ID | Requirement | Source | Use case | Priority | User stories |
|---|---|---|---|---|---|
| FR-027 | The system is seeded with 150 synthetic employees in 8 departments. | requirements.docx — Synthetic data | — | Must | US-034 |
| FR-028 | The seed includes a reporting hierarchy linking each employee to a manager. | requirements.docx — Synthetic data | — | Must | US-034 |
| FR-029 | The seed includes 3,000 historical claims, including planted duplicates and limit breaches. | requirements.docx — Synthetic data | — | Must | US-034 |
| FR-030 | Each seeded employee has a synthetic bank account number and IFSC code for the payout file. | CL-11 (CLAUDE.md §2.8) | UC-011 | Must | US-030, US-034 |

**Totals:** 32 functional requirements — 32 Must.

Mandatory and optional fields, inputs and outputs are in `requirement-analysis.md` §7–§8. Business rules that these requirements apply are in `business-rules.md`.
