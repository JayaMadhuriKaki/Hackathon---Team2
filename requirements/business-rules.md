# Business Rules

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals |
| Version | 1.1 — **approved baseline** (project owner, 2026-11-27) |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Companion files | requirement-analysis.md · functional-requirements.md · non-functional-requirements.md · business-rules.md · user-stories.md · open-questions.md |

Business rules are the conditions the system applies. All values are seeded configuration (BR-030), not code. BR-001 to BR-009 are the policy rules POL-001 to POL-009 in CLAUDE.md, in the same order. Notes point to open questions (OQ) or unconfirmed assumptions (ASM) wherever the source is incomplete — **no missing rule has been filled in**.

| ID | Area | Rule | Type | Source | Notes | User stories |
|---|---|---|---|---|---|---|
| BR-001 | Policy limit | A day's meal spend may not exceed 1,500 INR. | Limit | requirements.docx | Exactly 1,500 passes. Aggregation scope (per claim or across claims) open: OQ-011 | US-010 |
| BR-002 | Policy limit | Hotel spend may not exceed 6,000 INR per night in tier-1 cities: Kolkata, Mumbai, Delhi, Chennai, Bangalore. | Limit | requirements.docx; CL-08 | Per night = line amount ÷ nights (ASM-005) | US-011 |
| BR-003 | Policy limit | Hotel spend may not exceed 4,000 INR per night in all other cities. | Limit | requirements.docx | — | US-011 |
| BR-004 | Policy limit | Local cab spend may not exceed 2,000 INR per trip. | Limit | requirements.docx | One line = one trip (assumed, OQ-027) | US-012 |
| BR-005 | Receipt | A receipt is mandatory for a line above 500 INR. | Validation | requirements.docx | Exactly 500 does not need one. Consequence of a missing receipt open: OQ-028 | US-006, US-013 |
| BR-006 | Duplicate | A line with the same vendor, amount and date as an earlier line is flagged as a duplicate. | Flag | requirements.docx | Scope (same employee or anyone) open: OQ-029 | US-015 |
| BR-007 | Duplicate | A line with the same receipt file hash as an earlier line is flagged as a duplicate. | Flag | requirements.docx | Consequence of the flag open: OQ-030 | US-005, US-014 |
| BR-008 | Weekend | Weekend spend is flagged for manager attention. | Flag | requirements.docx | Weekend = Saturday/Sunday by expense date (ASM-001) | US-016 |
| BR-009 | Duplicate | The combination of expense date and receipt number must be unique; a repeat is a duplicate. | Flag | CL-05 | Scope and consequence as BR-006/BR-007 | US-043 |
| BR-010 | Outcome | If any line breaks a limit (BR-001 to BR-004), the whole claim is auto-rejected, with a reason for each breaching line. | Decision | CL-07 | — | US-010, US-011, US-012 |
| BR-011 | Outcome | A claim can be auto-approved only if its total is 25,000 INR or less and it has no weekend line. | Decision | CL-02 | Duplicates also prevent auto-approval per ASM-004 (unconfirmed) | US-017 |
| BR-012 | Routing | A claim with any weekend line is always routed to the manager. | Decision | CL-02 | — | US-016 |
| BR-013 | Routing | Thresholds use the claim total: above 25,000 INR adds the finance auditor; above 1,00,000 INR adds the finance head (strictly greater than). | Decision | requirements.docx; CL-03 | 25,000.00 → no auditor; 25,000.01 → auditor | US-018, US-021, US-022 |
| BR-014 | Routing | The chain order is manager, then finance auditor, then finance head. | Decision | requirements.docx | — | US-021, US-022 |
| BR-015 | Routing | If a step has no approver (e.g. no manager), the claim moves to the next level. | Decision | CL-09 | — | US-024 |
| BR-016 | Routing | A finance head's own claims are approved by a finance auditor, whatever the amount. | Decision | CL-16 | — | US-024 |
| BR-017 | Approval | Nobody may approve their own claim, directly or as a delegate. | Constraint | CL-16 (owner premise for Q-26) | Handling when the claimant is a step's approver: skip a level (ASM-009, unconfirmed) | US-023, US-024, US-027 |
| BR-018 | Approval | Rejecting a claim requires a comment. | Validation | CL-04 | — | US-018 |
| BR-019 | Approval | On resubmission after send-back, policy is re-run, the chain is recalculated from the new total and restarts at the manager; earlier approvals no longer count. | Decision | CL-12 | — | US-008 |
| BR-020 | Delegation | A delegate must be a finance auditor or a finance head. | Constraint | CL-13 | — | US-023 |
| BR-021 | Delegation | Delegation applies only while the approver is on leave. | Constraint | requirements.docx | Leave period captured as start/end dates (ASM-007, unconfirmed) | US-023 |
| BR-022 | Sample audit | A random 10% of auto-approved claims are flagged for sample audit. | Decision | requirements.docx | Exact 10% or 10% probability per claim open: OQ-035 | US-028 |
| BR-023 | Sample audit | A claim flagged for sample audit cannot be paid until a finance auditor approves it; the auditor may reject it with a comment. | Constraint | CL-14; CL-04 | — | US-028 |
| BR-024 | Payout | Only approved claims that are not held for sample audit and not already in a batch can be added to a payout batch; a claim is in at most one batch. | Constraint | CL-14; CL-11 | — | US-029 |
| BR-025 | Payout | Claims move to Paid when their batch is exported; there is no separate payment confirmation. | Decision | CL-06 | — | US-030 |
| BR-026 | Payout | Payout batches are created and exported by the finance head or admin. | Constraint | CL-11 | — | US-029 |
| BR-027 | Policy config | Only the finance head or admin can change policy configuration; every change is audited. | Constraint | CL-10 | — | US-041 |
| BR-028 | Visibility | Each user sees their own claims; approvers additionally see claims assigned to them and claims they acted on. | Constraint | requirements.docx; CL-15 | Exact approver scope per ASM-008 (unconfirmed) | US-002, US-044 |
| BR-029 | Audit | Every action is recorded in the audit trail and can never be changed or deleted. | Constraint | requirements.docx | — | US-033 |
| BR-030 | Configuration | All limits, thresholds, the tier-1 city list and the sample rate are configuration values. | Constraint | requirements.docx; CL-08 | — | US-011, US-017 |
| BR-031 | Payout | The payout CSV follows the format in CLAUDE.md §2.8: one row per claim, 13 columns. | Format | CL-11 (format decided by Claude at the owner's request) | Team story US-030 proposes a different 9-column format: OQ-036 | US-030 |

## Decision conditions — claim outcome on submission

Evaluated in this order (CL-07, CL-02, CLAUDE.md §2.7):

| # | Condition | Outcome | Rules |
|---|---|---|---|
| 1 | A line above 500 INR has no receipt | Handling open — submission blocked per ASM-003 | BR-005, OQ-028 |
| 2 | Any line breaks a meal, hotel or cab limit | **Auto-reject** whole claim, reason per line | BR-001–004, BR-010 |
| 3 | Total > 25,000 INR, or any weekend line | **Route** into the approval chain | BR-011, BR-012, BR-013 |
| 4 | Any duplicate flag | Route with flag (ASM-004) — or auto-reject? | BR-006, BR-007, BR-009, OQ-030 |
| 5 | None of the above | **Auto-approve**; 10% sampled for audit | BR-011, BR-022 |

## Decision conditions — approval chain

| Condition (claim total) | Steps | Rules |
|---|---|---|
| ≤ 25,000 INR (routed, e.g. weekend) | Manager | BR-012, BR-014 |
| > 25,000 and ≤ 1,00,000 INR | Manager → Finance auditor | BR-013 |
| > 1,00,000 INR | Manager → Finance auditor → Finance head | BR-013 |
| Claimant has no manager | Skip to next level | BR-015 |
| Claimant is the finance head | Finance auditor only | BR-016 |
| Claimant would approve own step | Skip to next level (ASM-009) | BR-017 |
| Sent back and resubmitted | Re-run policy; restart at manager | BR-019 |
