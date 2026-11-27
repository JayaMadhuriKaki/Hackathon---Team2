# Open Questions

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals |
| Version | 1.1 — **approved baseline** (project owner, 2026-11-27) |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Companion files | requirement-analysis.md · functional-requirements.md · non-functional-requirements.md · business-rules.md · user-stories.md · open-questions.md |

Missing, ambiguous or conflicting information. **Critical** questions affect money, approval routing, access control, audit or payout, and block the related build work until answered. Each has a *current handling* so the team knows what will happen if nothing changes — it is not a decision.

## Critical — need an answer before building the affected rule

| ID | Area | Question | Origin | Current handling |
|---|---|---|---|---|
| OQ-028 | Policy | When a line above 500 INR has no receipt: is submission blocked (ASM-003) or is the claim auto-rejected (team US-013)? | New — conflict | Blocked at submission (ASM-003); architecture/SPEC.md agrees |
| OQ-029 | Policy | Does the vendor + amount + date duplicate rule compare against the same employee's lines only (team US-015), or everyone's? | New — source silent | Spec proposal (SPEC-Q-05): vendor/amount/date vs the claimant's own lines; hash and date+receipt number vs everyone |
| OQ-030 | Policy | What happens to a claim with a duplicate flag: routed to an approver with the flag (source says 'flagged'; ASM-004) or auto-rejected (team US-014)? | New — conflict | Routed with flag (ASM-004); architecture/SPEC.md agrees |
| OQ-032 | Policy | What is the full list of expense categories? Do categories other than meals, hotel and local cab have any limit? | New | Spec proposal (SPEC-Q-02): only MEALS, HOTEL, LOCAL_CAB |
| OQ-046 | Approvals | Does a finance head's clean claim of 25,000 INR or less go to a finance auditor (CL-16), or can it be auto-approved (CL-02)? | architecture/SPEC.md SPEC-Q-14 | Spec proposal: always to an auditor, never auto-approved |
| OQ-047 | Approvals | Finance auditors report to the finance head, so an auditor's own claim above 1,00,000 INR would have the finance head on both the manager step and the finance-head step. May one person approve two levels? | architecture/SPEC.md SPEC-Q-16 | Spec proposal: yes, each step approved separately |

## Non-critical

| ID | Area | Question | Origin | Current handling |
|---|---|---|---|---|
| OQ-006 | Policy | Should claim-splitting (several lines or claims just under a limit or threshold) be detected? If so, how? | CLAUDE.md Q-06 | Not detected; each rule checked as written |
| OQ-011 | Policy | Is the 1,500 INR meal limit the total for the employee on that date across all their claims, or per claim? | CLAUDE.md Q-11; team US-010 proposes across claims | Team proposal: across claims |
| OQ-013 | Policy | Is weekend Saturday and Sunday by expense date? Are public holidays included? | CLAUDE.md Q-13 | ASM-001: Saturday/Sunday by expense date |
| OQ-020 | Access | What exactly can the admin do beyond policy configuration and payout batches? | CLAUDE.md Q-20 | Policy configuration and payout only |
| OQ-021 | Security | Which receipt file types and maximum size are allowed? More than one file per line? | CLAUDE.md Q-21; team US-005 proposes PDF/JPG/PNG ≤ 5 MB | Team proposal |
| OQ-022 | Security | How do users sign in? | CLAUDE.md Q-22; team US-001 proposes email + demo password + JWT | Team proposal |
| OQ-023 | Dashboard | How is average approval time measured (submit to final approval? calendar or business time?) | CLAUDE.md Q-23; team US-032 proposes submit to final approval | Team proposal |
| OQ-024 | Audit queue | Which filters does the review queue need? Can bulk approve include flagged claims? | CLAUDE.md Q-24; team US-026 proposes department, category, amount, date, flag type | Team proposal |
| OQ-025 | Claims | Is a receipt needed to save a draft? Can a submitted claim be withdrawn? | CLAUDE.md Q-25 | Draft without receipt; no withdrawal |
| OQ-027 | Policy | Is one local cab line always one trip? | New | One line = one trip |
| OQ-031 | Claims | Can an expense date be in the future? (Team US-003 rejects future dates; not documented.) | New | Spec proposal: future dates rejected (compared in IST) |
| OQ-033 | Claims | Are all amounts in INR only? | New | INR only |
| OQ-034 | Approvals | Is a comment mandatory when sending a claim back? (Team US-019 says yes; only rejection is confirmed.) | New | Team and spec proposal: mandatory |
| OQ-035 | Audit queue | Is the sample exactly 10% of auto-approved claims (and over what period), or a 10% chance for each claim (team US-028)? | New — ambiguity | Team and spec proposal (ASM-S-05): 10% chance per claim |
| OQ-040 | Approvals | Team US-024 also 'flags' the finance head's own claims. Is a flag required, and what does it do? | New — not documented | No flag |
| OQ-041 | Claims | Are line items locked once a claim is submitted (team US-006)? Only editable again after send-back? | New | Locked after submit |
| OQ-042 | Ops | What are the targets for page latency and the threshold for the oldest-pending-approval alert? (Source: 'team validates thresholds'.) | New | Team to set in the runbook |
| OQ-043 | Policy | Does policy configuration need an editing screen in the must-have scope, or is editing the seeded configuration enough (team marks the screen Stretch)? | New | Team proposal: screen is Stretch |
| OQ-044 | Scope | Team stories US-040 (notifications) and US-042 (receipt OCR) are not in the requirements. Approve as optional extras or drop? | New | Treated as Could; no work until approved |
| OQ-045 | Claims | Is a receipt number required for lines at or below 500 INR that have no receipt? | New | Spec proposal: required on every line |
| OQ-048 | Dashboard | Which claim states count as 'spend', and which date decides the month? | architecture/SPEC.md SPEC-Q-11 | architecture/SPEC.md §7.3 |
| OQ-049 | Ops | Is API latency enough for 'page latency', or must browser page loads be measured? | architecture/SPEC.md SPEC-Q-12 | API latency on the dashboard; page timings from Playwright/Locust |
| OQ-050 | Security | Rate limits and sign-in token lifetime? | architecture/SPEC.md SPEC-Q-13 | Login 5/min per IP; uploads 30/min per user; token 8 h |

## Resolved by approval of the analysis (2026-11-27)

| ID | Question | Resolution |
|---|---|---|
| OQ-036 | Which payout CSV format applies: the 13-column format decided in CL-11 or the team's 9-column format in US-030? | CL-11 13-column format applies (CL-20) |
| OQ-037 | Auto-approve ceiling: CL-02 says total ≤ 25,000 INR; team US-017 says ≤ 5,000 INR. Confirm CL-02 applies. | CL-02 applies: auto-approve up to 25,000 INR (CL-20) |
| OQ-038 | Limit breaches: CL-07 says auto-reject the claim; team US-010/011/012 say route with a flag. Confirm CL-07 applies. | CL-07 applies: limit breach auto-rejects (CL-20) |
| OQ-039 | Team US-023 lets any delegate be chosen; CL-13 restricts delegates to finance auditor or finance head. Confirm CL-13 applies. | CL-13 applies: delegate must be finance auditor or finance head (CL-20) |

## Mapping to architecture/SPEC.md questions

The team's spec keeps its own IDs. This file is the single list; answers are recorded here and in CLAUDE.md, then copied to the spec.

| SPEC ID | OQ ID | | SPEC ID | OQ ID |
|---|---|---|---|---|
| SPEC-Q-02 | OQ-032 | | SPEC-Q-09 | OQ-024 |
| SPEC-Q-03 | OQ-034 | | SPEC-Q-10 | OQ-025 |
| SPEC-Q-04 | OQ-011 | | SPEC-Q-11 | OQ-048 |
| SPEC-Q-05 | OQ-029 | | SPEC-Q-12 | OQ-049 |
| SPEC-Q-06 | OQ-021 | | SPEC-Q-13 | OQ-050 |
| SPEC-Q-07 | OQ-022 | | SPEC-Q-14 | OQ-046 |
| SPEC-Q-08 | OQ-023 | | SPEC-Q-15 | OQ-045 |
| | | | SPEC-Q-16 | OQ-047 |

## Assumptions awaiting confirmation

These are used in the analysis but are **not** confirmed requirements.

| ID | Assumption | Affects |
|---|---|---|
| ASM-001 | Weekend is determined by the expense line's date | BR-008 |
| ASM-002 | Times are shown in IST | NFR-021 |
| ASM-003 | A line above 500 INR without a receipt cannot be submitted | BR-005 |
| ASM-004 | A duplicate-flagged claim is routed to approval, never auto-approved | BR-006, BR-007, BR-009, BR-011 |
| ASM-005 | Hotel nights come from check-in and check-out dates; per night = amount ÷ nights | BR-002, BR-003, FR-022 |
| ASM-006 | 'Bangalore' also matches 'Bengaluru'; 'Delhi' also matches 'New Delhi' | BR-002 |
| ASM-007 | A delegation has start and end dates; one active delegate per approver | BR-021 |
| ASM-008 | Approvers see only claims assigned to them or that they acted on | FR-031, BR-028 |
| ASM-009 | If the claimant would approve a step of their own claim, that step is skipped to the next level | BR-017 |
| ASM-010 | Dashboard: finance auditors and finance head; full audit trail: finance head; others see history of claims they can open | FR-015–018 |

Proposed in `architecture/SPEC.md` §12 (not yet accepted): ASM-S-01 admin role exists but the finance head acts as admin; ASM-S-02 duplicate matching per SPEC §4.3; ASM-S-03 a hotel line's expense date is its check-in date; ASM-S-04 a delegator can still act on their own steps during a delegation; ASM-S-05 10% chance per claim; ASM-S-06 UUIDv7 keys.

## Conflicts between the team's user-stories.xlsx and confirmed decisions

| Team story | Team version | Confirmed / documented position | Question |
|---|---|---|---|
| US-10, US-11, US-12 | Limit breach routes with a flag | Breach auto-rejects the claim (CL-07) | OQ-038 — resolved |
| US-17 | Auto-approve only if total ≤ 5,000 INR | Auto-approve if total ≤ 25,000 INR (CL-02) | OQ-037 — resolved |
| US-13 | Missing receipt above 500 INR → auto-reject | Submission blocked (ASM-003, unconfirmed) | OQ-028 |
| US-14 | Same receipt hash → auto-reject | Source says "flagged"; routed (ASM-004, unconfirmed) | OQ-030 |
| US-15 | Duplicate check limited to the same employee | Source does not restrict | OQ-029 |
| US-23 | Any delegate | Finance auditor or finance head only (CL-13) | OQ-039 — resolved |
| US-24 | Finance head's claims also "flagged" | Not documented (CL-16 routes to auditor only) | OQ-040 |
| US-30 | 9-column CSV | 13-column format (CL-11) | OQ-036 — resolved |
| US-03 | Future dates rejected | Not documented | OQ-031 |
| US-40, US-42 | Notifications, OCR | Not in requirements | OQ-044 |
