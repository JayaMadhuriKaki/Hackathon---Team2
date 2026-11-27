# CLAUDE.md — ExpenseFlow Engineering Guide

> Claude Code reads this file at the start of every session.
> These are the **mandatory engineering standards** for this repository. They override any general habit or default.
> Role: act as a **senior full-stack software architect and engineering lead**: plan first, verify everything, never guess.

---

## 0. Session Start Checklist (do this every session, before any change)

1. Read this file completely.
2. Read `PROGRESS.md` (progress tracker) to see the current phase, open items and blockers. If it does not exist yet, create it in Phase 0 (see §15).
3. Run `git status` and `git log --oneline -10`. If there are uncommitted user changes, **do not touch them**. Ask how to proceed.
4. Re-read the requirement sections relevant to today's task in `requirements/requirements.docx` and `requirements/requirement-analysis.md`.
5. Check §6 (Open Questions). If today's task depends on an unanswered business-critical question, **stop and ask**.
6. State the plan for the task (see §14.2) and wait for approval before writing code.

---

## 1. Project Snapshot

| Item | Value |
|---|---|
| Product | **ExpenseFlow** — Expense Claims and Policy-Driven Approvals |
| Track | Track A |
| Source of truth | `requirements/requirements.docx` (provided) |
| Supporting docs | `requirements/use-cases.docx`, `requirements/requirement-analysis.md` |
| Currency | INR (Indian digit grouping used in the source: 1,00,000 = 100,000) |
| Tech stack | **Approved** — React 19 (Vite) · Spring Boot 4.1.x on JDK 21 · PostgreSQL 18 in Docker · JUnit 5 unit tests + pytest API tests · Docker Compose · Prometheus + Grafana. See §8. |
| Event rules | `requirements/AI_Manthan_Hackathon___Prerequisites_Guide.docx` — see §8.2 |
| Team / deadline / demo slot | `<fill in>` |

### 1.1 Problem statement (summary of the source document)
A mid-size services company reimburses expenses through email threads and Excel. Policy breaches slip through, duplicate receipts get paid and managers take days to respond. ExpenseFlow replaces this with a policy engine, a multi-level approval chain, an auditor review queue and a payout export.

### 1.2 Personas (as documented)
| Persona | Notes from source |
|---|---|
| Employee | Creates and submits claims. Can only see **their own** claims. |
| Line manager | First approver in the chain. |
| Finance auditor | Second approver above 25,000 INR; works the review queue; handles sample audits. |
| Finance head | Third approver above 1,00,000 INR; **also the admin**. |

---

## 2. Documented Business Requirements (the only rules you may implement)

Everything in this section comes directly from `requirements.docx`. Requirement IDs are assigned here for traceability; keep them stable. If a rule is not in this section or in a later approved clarification (§6), **it does not exist**.

### 2.1 Functional requirements — Must-have scope

| ID | Requirement (from source) |
|---|---|
| FR-001 | An employee creates a claim containing several line items. Each line has **category, amount, date and receipt upload**. |
| FR-002 | An employee can **save a claim as a draft**. |
| FR-003 | An employee can **submit** a claim. |
| FR-004 | A policy engine checks **every line** and returns exactly one of three outcomes: **auto-approve**, **route for approval**, or **auto-reject with a reason**. |
| FR-005 | Approval chain order: **manager**, then **finance auditor** when above 25,000 INR, then **finance head** when above 1,00,000 INR. |
| FR-006 | Approvers can **comment** on a claim. |
| FR-007 | Approvers can **send a claim back for clarification**. |
| FR-008 | Approvers can **delegate** their approvals while **on leave**. |
| FR-009 | A finance auditor works a **review queue with filters**. |
| FR-010 | The finance auditor can **bulk approve** from the review queue. |
| FR-011 | A **random 10% of auto-approved claims** are flagged for **sample audit**. |
| FR-012 | Approved claims go into a **payout batch**. |
| FR-013 | A payout batch **exports as CSV in a mock bank-file format**. |
| FR-014 | After payout, claims move to **Paid**. |
| FR-015 | A dashboard shows **spend by category, department and month**. |
| FR-016 | The dashboard shows **policy violations**. |
| FR-017 | The dashboard shows **average approval time**. |
| FR-018 | An **append-only audit trail** records **every action**. |
| FR-019 | An employee can **only see their own claims**. |
| FR-020 | An approver can **reject a claim outright**; a **comment is mandatory** when rejecting. *(CL-04)* |

### 2.2 Policy rules — seeded as configuration, never hard-coded

| ID | Rule (from source) | Config key (proposed name) |
|---|---|---|
| POL-001 | Meals: up to **1,500 INR per day** | `meals.max_per_day` |
| POL-002 | Hotel: up to **6,000 INR per night in tier-1 cities** (Kolkata, Mumbai, Delhi, Chennai, Bangalore — CL-08) | `hotel.max_per_night.tier1`, `hotel.tier1_cities` |
| POL-003 | Hotel: up to **4,000 INR per night in all other cities** | `hotel.max_per_night.other` |
| POL-004 | Local cab: up to **2,000 INR per trip** | `local_cab.max_per_trip` |
| POL-005 | A **receipt is mandatory above 500 INR** | `receipt.mandatory_above` |
| POL-006 | **Duplicate:** a line with the **same vendor, amount and date** as an earlier line is flagged | `duplicate.match_fields` |
| POL-007 | **Duplicate:** a line with the **same receipt file hash** as an earlier line is flagged | `duplicate.match_receipt_hash` |
| POL-008 | **Weekend spend** is flagged for **manager attention**, and the claim is **always routed to the manager** (never auto-approved) *(CL-02)* | `weekend.flag_for_manager` |
| POL-009 | **Duplicate:** the combination of **expense date + receipt number** must be unique; a repeat is treated as a duplicate *(CL-05)* | `duplicate.match_date_receipt_no` |

**Line-item fields** (documented): category, amount, date, receipt upload (FR-001), vendor (needed by POL-006) and **receipt number** (CL-05). Hotel lines also need **city** and **number of nights** (CL-08; capture method per ASM-005).

**Rules for the policy engine**
- All limits, thresholds and flags live in configuration (seed file + database table). Code reads them; code never contains the numbers.
- Approval thresholds (25,000 and 1,00,000 INR) and the 10% sample rate are also configuration.
- The policy engine is written **test-first**: the unit tests for every POL rule exist and fail before the engine is implemented.
- Each policy result records which rule fired, the configured limit, the actual value and a human-readable reason.

### 2.3 Synthetic data (seed)
| ID | Requirement |
|---|---|
| DATA-001 | **150 employees** in **8 departments** |
| DATA-002 | A **reporting hierarchy** (each employee maps to a manager) |
| DATA-003 | **3,000 historical claims**, including **planted duplicates** and **limit breaches** |
| DATA-004 | All data is synthetic. No real personal data. |
| DATA-005 | Each employee has a synthetic bank account number and IFSC code for the payout file (CL-11). |

The seed must be deterministic (fixed random seed) so demos and tests are repeatable.

### 2.4 Phase deliverables (from source)

| Phase | Required outputs | How Claude is used (from source) |
|---|---|---|
| Architecture | Context and container diagram; claim state machine; policy-as-configuration design; ADR on how approval chains are modelled | Review the design **as a skeptical finance auditor** and list ways to bypass approvals |
| UI and UX | Mobile-friendly claim capture; side-by-side receipt viewer for approvers; dense but scannable data tables with bulk actions | Propose layouts and states for tables; team tests the approver flow with a stopwatch |
| Development | Working app with the full chain, policy engine and payout export | Plan-first prompts; business rules in this file; **tests written before the policy engine** |
| Testing | Unit tests for every policy rule; tests for approval thresholds and delegation; **10 written end-to-end cases**; defect log; basic security checklist run | Generate rule-by-rule cases; review code against common web vulnerabilities; team fixes real findings and rejects false alarms |
| Deployment | Docker Compose; CI with tests and a dependency check; seeded demo environment from **one command** | Claude writes CI; team confirms no secrets are committed |
| Support (SRE) | Metrics for queue size, approval age and page latency; dashboard; alert; runbook | Draft the runbook and an alert on **oldest pending approval**; team validates thresholds |

### 2.5 Non-functional requirements (derived only from documented items)
| ID | Requirement | Source |
|---|---|---|
| NFR-001 | Claim capture UI is mobile-friendly | UI and UX phase |
| NFR-002 | Approver screens show the receipt side by side with the claim | UI and UX phase |
| NFR-003 | Data tables are dense, scannable and support bulk actions | UI and UX phase |
| NFR-004 | Audit trail is append-only: no update or delete path exists, in code or in DB permissions | FR-018 |
| NFR-005 | Data isolation: an employee can never read another employee's claim, by UI or by API | FR-019 |
| NFR-006 | Whole stack runs with Docker Compose; demo environment is seeded with one command | Deployment phase |
| NFR-007 | CI runs tests and a dependency vulnerability check on every push | Deployment phase |
| NFR-008 | Metrics exist for queue size, approval age and page latency, with a dashboard and an alert on oldest pending approval | Support phase |
| NFR-009 | A basic web security checklist is run and its results recorded | Testing phase |

Any further NFR target (for example a specific latency number) must be agreed and recorded in §6 before it is used.

### 2.6 Confirmed clarifications (answered 2026-11-27 by the project owner)

These answers are now binding requirements. They take precedence over any earlier reading of the source document.

| ID | Answers | Decision |
|---|---|---|
| CL-01 | Q-01 (partial) | The policy engine's decision criteria are exactly the documented limits: meals ≤ 1,500 INR per day, hotel ≤ 6,000 INR per night in tier-1 cities and ≤ 4,000 INR elsewhere, local cab ≤ 2,000 INR per trip, receipt mandatory above 500 INR. No other limit may be added. Breach handling is decided in CL-07. |
| CL-02 | Q-03, Q-04 | **Auto-approve is only possible when the claim total is 25,000 INR or less** and no weekend flag is raised. A claim whose total is above 25,000 INR **always goes through the approval chain**, even if every line passes policy. A claim with any **weekend** line is **always routed to the manager**. |
| CL-03 | Q-05 | Approval thresholds are measured on the **claim total**. Following the source wording "above", the checks are strictly greater than: total > 25,000 adds the finance auditor; total > 1,00,000 adds the finance head. A total of exactly 25,000 does not require the auditor. |
| CL-04 | Q-07 | An approver at any step can **reject a claim outright**. A **comment is mandatory** on rejection. Rejection ends the claim (state `REJECTED`). |
| CL-05 | Q-12/Q-14 (partial) | Each line item has a **receipt number**. The combination **expense date + receipt number must be unique**; a repeat is a duplicate (POL-009). Hotel city, number of nights and the tier-1 city list are still open (Q-12). |
| CL-06 | Q-15 | There is **no separate payment-confirmation step**. An approved claim is placed in a payout batch and moves to **Paid** when that batch is exported. |
| CL-07 | Q-01, Q-02 | If **any line breaks a limit** (POL-001 to POL-004), the **whole claim is auto-rejected**. The rejection reason lists every breaching line with the rule, the limit and the actual amount. |
| CL-08 | Q-12 | **Tier-1 cities: Kolkata, Mumbai, Delhi, Chennai, Bangalore.** Stored as configuration. City matching is case-insensitive and also accepts the official names Bengaluru and New Delhi (ASM-006). All other cities use the 4,000 INR limit. |
| CL-09 | Q-10 | If a step has **no approver** (for example the claimant has no manager), the claim **moves to the next level** of the chain. |
| CL-10 | Q-17 | Only users with the **finance head or admin** role can edit policy configuration. Every change is recorded in the audit trail. |
| CL-11 | Q-16, Q-15 | **Payout CSV format and batch ownership** — the owner asked Claude to decide from the requirements. Decision in §2.8. |
| CL-12 | Q-08 | After **send-back** and resubmission, the claim **restarts**: the policy engine runs again on the edited lines, the chain is recalculated from the new total, and approval begins again at the first step (manager). Earlier approvals on that claim no longer count. |
| CL-13 | Q-09 | A **delegate must be a finance auditor or a finance head**. Delegation never lets anyone approve their own claim (B-02/B-03). |
| CL-14 | Q-18 | Claims selected for the **10% sample audit are held from payout** until a finance auditor reviews them. The auditor can approve (claim becomes eligible for payout) or reject with a mandatory comment (CL-04). |
| CL-15 | Q-19 | **Every user, in every role, sees their own claims.** Beyond that, approvers see only what they need to act: claims currently assigned to them (or to someone they are delegate for) and claims they have already acted on (ASM-008). |
| CL-16 | Q-26 | The **finance head's own claims are approved by a finance auditor**, whatever the amount. They never go to a finance head step. |
| CL-17 | Tech stack | Stack confirmed by the project owner: **React 19** frontend, **Spring Boot** backend, **PostgreSQL 18 in Docker**, **pytest for backend and API tests**, **Docker Compose** delivery, **Prometheus + Grafana** monitoring. Versions follow the AI Manthan Prerequisites Guide (§8). Supersedes the earlier FastAPI choice (ASM-011, withdrawn). |
| CL-18 | Testing | **JUnit 5** (via `spring-boot-starter-test`) is used for Java unit tests — policy engine, approval-chain engine and other business logic — alongside **pytest** for backend API and integration tests. |

### 2.7 Routing summary (derived from CL-01 to CL-03)

| Condition (claim level) | Route |
|---|---|
| All lines pass policy, no weekend line, total ≤ 25,000 | Auto-approve → payout |
| Any weekend line, total ≤ 25,000 | Manager |
| Total > 25,000 and ≤ 1,00,000 | Manager → Finance auditor |
| Total > 1,00,000 | Manager → Finance auditor → Finance head |
| Any line breaches a limit (POL-001 to POL-004) | **Auto-reject** the whole claim, with reasons (CL-07) |
| A line above 500 INR has no receipt (POL-005) | Cannot be submitted — blocked by validation (ASM-003) |
| Any line is flagged as a duplicate (POL-006, POL-007, POL-009) | Route to approval chain with the flag shown; never auto-approved (ASM-004) |
| The step has no approver (e.g. no manager) | Skip to the next level (CL-09) |
| Claimant is the finance head | Finance auditor only, any amount (CL-16) |
| Claimant would be the approver of a step | Skip that step to the next level (ASM-009) |
| Auto-approved and selected for 10% sample audit | Held from payout until an auditor reviews (CL-14) |
| Sent back and resubmitted | Restart: re-run policy, recalculate chain, begin at manager (CL-12) |

Evaluation order: (1) limit breaches → auto-reject; (2) otherwise, if total > 25,000, any weekend line or any duplicate flag → approval chain; (3) otherwise → auto-approve. An auto-rejected claim is never routed.

### 2.8 Payout batch and bank-file export (CL-11)

The source only says approved claims go into a payout batch that "exports as a CSV in a mock bank-file format" and that claims then move to Paid. As instructed by the project owner, the format below is Claude's decision, chosen to resemble a typical Indian bulk-payment (NEFT) upload file. It can be changed by an approved ADR.

**Batch rules**
- Payout batches are created and exported by users with the **finance head or admin** role (aligned with CL-10).
- Only claims in state `APPROVED` and not already in a batch can be added. A claim belongs to at most one batch.
- Creating the batch and exporting it are separate actions. On export, in one transaction: the CSV is generated, the batch is locked, every claim in it moves to `PAID`, and audit entries are written.
- An exported batch can be downloaded again (same file, same totals) but never edited.

**File format**

| Property | Value |
|---|---|
| File name | `EXPENSEFLOW_PAYOUT_<batchNumber>_<YYYYMMDD>.csv` |
| Encoding / delimiter | UTF-8, comma, every text field quoted, header row included |
| Rows | One row per claim |
| Amounts | Rupees with exactly 2 decimals, no thousand separators (e.g. `27450.00`) |
| Dates | `YYYY-MM-DD` |
| Safety | Values starting with `=`, `+`, `-`, `@`, tab or carriage return are neutralised (§13.4) |

| # | Column | Example | Notes |
|---|---|---|---|
| 1 | `batch_number` | `PB-2026-0007` | |
| 2 | `payment_reference` | `PB-2026-0007-0001` | Unique per row |
| 3 | `value_date` | `2026-11-30` | Export date |
| 4 | `payment_mode` | `NEFT` | Fixed value in the mock |
| 5 | `employee_id` | `EMP0142` | |
| 6 | `beneficiary_name` | `Ananya Rao` | |
| 7 | `beneficiary_account_number` | `001234567890` | Synthetic |
| 8 | `beneficiary_ifsc` | `HDFC0001234` | Synthetic, valid format |
| 9 | `amount` | `27450.00` | Approved claim total |
| 10 | `currency` | `INR` | |
| 11 | `claim_id` | `CLM-2026-01534` | |
| 12 | `department` | `Consulting` | |
| 13 | `narration` | `Expense reimbursement CLM-2026-01534` | Max 30 characters, trimmed |

The export screen and API also return a **control summary**: number of rows, total amount and the SHA-256 hash of the file. This requires seeded **synthetic bank details** for each employee (account number, IFSC) — an addition to DATA-001.

---

## 3. Requirement Handling Rules (mandatory)

1. **Never assume undocumented requirements.** If behaviour is not in §2 or in an answered §6 question, do not build it.
2. **Read all supplied documents** (`requirements.docx`, `use-cases.docx`, anything added to `requirements/`) before analysis or design.
3. **Identify missing, conflicting and ambiguous requirements.** Add each to §6 and to `requirements/requirement-analysis.md`.
4. **Ask for clarification** when a business-critical requirement is unclear. Business-critical means it affects money, approval routing, access control, audit or payout.
5. For non-critical gaps (for example a label or a colour), you may propose a sensible default, but log it as an assumption (`ASM-###`) and flag it in the task summary.
6. **Maintain traceability** for every requirement: requirement → UI screen/component → API operation → backend service/rule → DB table → test case(s) → validation result. Record it in `deliverables/requirement-traceability.xlsx`.
7. When a clarification is answered, record the answer in §6 (mark it Answered, with date and who answered), then update the analysis doc and the traceability matrix in the same task.

---

## 4. Domain Model Guidance

### 4.1 Claim state machine — DRAFT, needs confirmation in the Architecture phase
The source asks for a state machine as an architecture deliverable but does not list the states. The states below are only those the source implies. **Do not add states, transitions or side effects beyond this without approval.**

```mermaid
stateDiagram-v2
    [*] --> Draft : employee creates
    Draft --> Submitted : employee submits (FR-003)
    Submitted --> AutoApproved : passes policy, total <= 25,000, no weekend line (CL-02)
    Submitted --> AutoRejected : any line breaks a limit, with reasons (CL-07)
    Submitted --> PendingManager : total > 25,000, weekend line or duplicate flag (CL-02, ASM-004)
    Submitted --> PendingAuditor : no manager, skip level (CL-09)
    PendingManager --> PendingAuditor : approved and amount > 25,000 (FR-005)
    PendingAuditor --> PendingFinanceHead : approved and amount > 1,00,000 (FR-005)
    PendingManager --> SentBack : send back (FR-007)
    PendingAuditor --> SentBack : send back (FR-007)
    PendingFinanceHead --> SentBack : send back (FR-007)
    PendingManager --> Rejected : reject + mandatory comment (CL-04)
    PendingAuditor --> Rejected : reject + mandatory comment (CL-04)
    PendingFinanceHead --> Rejected : reject + mandatory comment (CL-04)
    SentBack --> Submitted : employee resubmits; chain restarts (CL-12)
    PendingManager --> Approved : approved, total <= 25,000
    PendingAuditor --> Approved : approved, total <= 1,00,000
    PendingFinanceHead --> Approved : approved
    AutoApproved --> Approved : not selected for sample audit (CL-02)
    AutoApproved --> SampleAuditPending : selected in 10% sample (FR-011, CL-14)
    SampleAuditPending --> Approved : auditor approves (CL-14)
    SampleAuditPending --> Rejected : auditor rejects + comment (CL-14)
    Approved --> InPayoutBatch : added to batch (FR-012)
    InPayoutBatch --> Paid : batch CSV exported (CL-06)
    AutoRejected --> [*]
    Rejected --> [*]
    Paid --> [*]
```

Every transition must:
- be enforced by the backend (never just hidden in the UI);
- check that the actor is allowed to perform it;
- write an audit-trail entry (actor, action, from-state, to-state, timestamp, comment/reason).

### 4.2 Approval chain modelling
- Model the chain as **data driven by configuration** (ordered steps, each with a role and a threshold), not as nested `if` statements. Record the choice in an ADR (`architecture/adr/ADR-001-approval-chain.md`).
- The required steps are calculated when the claim is submitted and stored with the claim, so a later config change does not silently re-route claims already in flight. **Confirm this behaviour (Q-17).**
- Delegation changes **who** may act on a step. It never changes **which** steps are required.

### 4.3 Money and dates
- Store money as an integer number of paise or as a fixed-precision decimal. **Never use floating point for money.**
- Display INR with Indian digit grouping (₹1,00,000.00).
- Store timestamps in UTC; display in the user's local time (assume IST unless told otherwise — log as an assumption).
- "Weekend" uses the expense line's date, not the submission date (assumption ASM-001 — confirm with Q-13).

---

## 5. Approval-Bypass Review (skeptical finance auditor)

The source requires the design to be reviewed for ways to bypass approvals. Every design and every code review must check at least these attack paths, and the results go in `architecture/security-review.md`:

| # | Bypass attempt | Required control |
|---|---|---|
| B-01 | Splitting one expense into several lines or claims to stay under a limit or threshold | Flag and surface; exact handling needs a decision (Q-06) |
| B-02 | Approving your own claim (directly, as delegate, or as admin) | Backend blocks self-approval at every step |
| B-03 | Delegating to yourself, to your own claimant, to an unauthorised role, or creating delegation chains | Delegate must be a finance auditor or finance head (CL-13), not the delegator, not the claimant; no delegation of a delegation |
| B-04 | Editing a claim after it has been submitted or approved | Lines are locked after submit; edits only in Draft/SentBack |
| B-05 | Changing the amount during "send back" to fall below a threshold | Re-run policy and re-calculate the chain on every resubmission |
| B-06 | Calling an API directly to skip a step or change state | State transitions validated server-side against the state machine |
| B-07 | Re-using a receipt with a renamed file | Duplicate check uses the file content hash (POL-007) |
| B-08 | Changing policy config to approve a pending claim | Config changes are admin-only, audited, and do not re-route in-flight claims |
| B-09 | Adding an already-paid or unapproved claim to a payout batch | Batch accepts only claims in Approved state; one claim, one batch |
| B-10 | Reading another employee's claims by changing an ID in the URL | Object-level authorization on every read (FR-019) |
| B-11 | Tampering with or deleting audit entries | Append-only table; no update/delete in code; DB permissions deny it |
| B-12 | Avoiding the 10% sample audit | Random selection made server-side with a recorded seed/log; sampled claims cannot enter a payout batch until reviewed (CL-14) |

---

## 6. Open Questions — Missing, Conflicting and Ambiguous Requirements

These were found while reading `requirements.docx`. **Items marked Critical block the related work until answered.** Record answers here and in `requirements/requirement-analysis.md`.

| ID | Area | Question | Type | Critical? | Status |
|---|---|---|---|---|---|
| Q-01 | Policy outcomes | Limits confirmed (CL-01). **Still open:** when a line breaches a limit, has no receipt above 500 INR, or is a duplicate, is the result auto-reject or route for approval? | Missing | **Yes** | Answered (CL-07) |
| Q-02 | Policy outcomes | The engine checks each line, but approval is per claim. How is the claim's outcome derived when lines have different outcomes? Can a single line be rejected while the rest proceed? | Ambiguous | **Yes** | Answered (CL-07) |
| Q-03 | Auto-approve vs chain | Does an auto-approved claim skip the whole approval chain, including the manager and the amount thresholds? Can a claim above 25,000 INR be auto-approved? | Conflicting | **Yes** | Answered (CL-02) |
| Q-04 | Weekend flag | POL-008 flags weekend spend "for manager attention". If the claim would otherwise be auto-approved, does the weekend flag force routing to the manager? | Conflicting | **Yes** | Answered (CL-02) |
| Q-05 | Thresholds | Is "above 25,000" / "above 1,00,000" strictly greater than? Is it measured on the claim total, on approved lines only, or per line? | Ambiguous | **Yes** | Answered (CL-03) |
| Q-06 | Splitting | Should claim-splitting (several claims or lines just under a limit or threshold) be detected? If yes, what is the rule? | Missing | No | Open |
| Q-07 | Approver actions | The source lists comment, send back and delegate. Can an approver also **reject** a claim outright? | Missing | **Yes** | Answered (CL-04) |
| Q-08 | Send back | After send-back and resubmission, does the chain restart from the manager or resume at the step that sent it back? | Missing | **Yes** | Answered (CL-12) |
| Q-09 | Delegation | Who can be a delegate (same role only? any manager?), who sets it up (the approver or admin?), does it need start/end dates, and can a delegate approve a claim from someone they report to? | Missing | **Yes** | Answered (CL-13); dates per ASM-007 |
| Q-10 | Hierarchy gaps | Who approves a claim from someone with no manager, or from the finance head? Who is the "manager" step for a finance auditor's own claim? | Missing | **Yes** | Answered (CL-09) |
| Q-11 | Meals limit | Is 1,500 INR per day the total of all meal lines on the same date for the same employee, across all their claims? | Ambiguous | No | Open |
| Q-12 | Hotel nights / city tier | How are the number of nights and the city captured on a hotel line? Which cities are tier-1? (No list is provided.) *Not covered by CL-05.* | Missing | **Yes** | Answered (CL-08); nights per ASM-005 |
| Q-13 | Weekend | Is weekend Saturday and Sunday, based on the expense date? Are public holidays included? | Ambiguous | No | Open |
| Q-14 | Line fields | The only documented line fields are category, amount, date and receipt. Duplicate checks need **vendor**, hotel needs **city/nights**. Confirm the full field list and the complete category list. | Missing | **Yes** | Answered (CL-05, CL-08) |
| Q-15 | Paid status | Do claims move to Paid when the CSV is exported, or when someone confirms the bank payment? Who creates a payout batch? | Ambiguous | **Yes** | Answered (CL-06, CL-11) |
| Q-16 | CSV format | What columns, order, delimiter and file name does the "mock bank-file format" use? | Missing | **Yes** | Decided (CL-11, §2.8) |
| Q-17 | Config changes | Who can edit policy configuration (finance head as admin?), and do changes apply to claims already submitted? | Missing | **Yes** | Answered (CL-10) |
| Q-18 | Sample audit | 10% of auto-approved claims over what period? What happens to a sampled claim (blocks payout until reviewed, or reviewed after payment)? What if the audit finds a problem? | Missing | **Yes** | Answered (CL-14) |
| Q-19 | Visibility | Employees see only their own claims. What exactly can managers, auditors and the finance head see (direct reports only? whole department? everything)? Who can see the dashboard and audit trail? | Missing | **Yes** | Answered (CL-15); details per ASM-008, ASM-010 |
| Q-20 | Admin role | What can the finance head do as admin (manage users, roles, config, delegations)? | Missing | No | Open |
| Q-21 | Receipts | Allowed file types and maximum size for receipt uploads? Multiple files per line? | Missing | No | Open |
| Q-22 | Authentication | How do users sign in (local accounts with seeded passwords, SSO mock, role switcher for demo)? | Missing | No | Open |
| Q-23 | Approval time | How is "average approval time" measured (submit to final approval? per step? business hours or calendar time?) | Ambiguous | No | Open |
| Q-24 | Review queue | Which filters does the auditor queue need, and can bulk approve include claims with policy flags? | Ambiguous | No | Open |
| Q-25 | Draft rules | Is a receipt required to save a draft, or only to submit? Can a submitted claim be withdrawn? | Ambiguous | No | Open |
| Q-26 | Self-approval | Under CL-09 a claim skips a missing approver. If the claimant **is** the approver for a step (for example the finance head's own claim above 1,00,000 INR), the step cannot be performed. Who approves it? | Missing | **Yes** | Answered (CL-16) |

### 6.1 Assumption log
| ID | Assumption | Related | Confirmed? |
|---|---|---|---|
| ASM-001 | Weekend is determined by the expense line's date | POL-008, Q-13 | No |
| ASM-002 | Timestamps shown in IST | §4.3 | No |
| ASM-003 | A line above 500 INR without a receipt cannot be submitted (validation error), rather than auto-rejected | POL-005 | No |
| ASM-004 | A duplicate is **flagged** (as the source says), not rejected: the claim goes to the approval chain with the flag visible and cannot be auto-approved | POL-006/007/009 | No |
| ASM-005 | Hotel lines capture check-in and check-out dates; nights = check-out − check-in; per-night amount = line amount ÷ nights | POL-002/003 | No |
| ASM-006 | "Bangalore" also matches "Bengaluru", and "Delhi" also matches "New Delhi" | CL-08 | No |
| ASM-007 | A delegation has a start date and an end date (the leave period) and only one active delegate per approver at a time | CL-13 | No |
| ASM-008 | Approvers can open claims assigned to them (directly or as delegate) and claims they have acted on, so they can review receipts and history; nothing else beyond their own claims | CL-15 | No |
| ASM-009 | If the claimant would approve a step of their own claim (e.g. a finance auditor's claim reaching the auditor step), that step is skipped to the next level, extending CL-09; a finance auditor's own claim above 25,000 INR therefore goes to the finance head | CL-09, B-02 | No |
| ASM-010 | The dashboard shows only aggregate figures and is visible to finance auditors and the finance head/admin; the full audit trail is visible to the finance head/admin, and everyone else sees the audit history of claims they can open | CL-15, FR-015–018 | No |
| ASM-011 | ~~Backend is FastAPI~~ — **withdrawn**; replaced by the owner's Spring Boot decision (CL-17) | CL-17 | Withdrawn |
| ASM-012 | pytest tests the backend **through its HTTP API** (black-box); Java classes such as the policy engine are unit-tested with **JUnit 5**, because pytest cannot load Java code | CL-17, CL-18, §16 | **Yes — confirmed as CL-18** |

---

## 7. Repository Rules

1. **Inspect before changing.** Before any change, look at the relevant folders and files, the current stack, dependencies and conventions.
2. **Reuse** existing components, services, utilities and patterns. Search the codebase before creating something new.
3. **Never overwrite a file without understanding its purpose.** If unsure, read it and ask.
4. **No unnecessary frameworks or dependencies.** Each new dependency needs a one-line reason in the task plan and must be actively maintained and free of known critical vulnerabilities.
5. Current repository state (as of this file's creation): `frontend/`, `backend/` and `database/` contain only README placeholders. `architecture/api-contracts.yaml` contains a **generic placeholder** (`/items` endpoints) from the project template — it is **not** an ExpenseFlow contract and must be replaced in the API phase, not extended.

### 7.1 Repository map
```
CLAUDE.md                         ← this file
PROGRESS.md                       ← progress tracker (create in Phase 0)
.claude/settings.json             ← Claude Code permissions
.claude/skills/                   ← phase playbooks
.claude/commands/                 ← slash commands per phase
requirements/                     ← requirements.docx (source of truth), use-cases.docx, requirement-analysis.md
architecture/                     ← solution-design.md, architecture-diagram.md, database-design.md,
                                    api-contracts.yaml, adr/, security-review.md
frontend/  backend/  database/    ← source code, migrations, seeds
tests/frontend|backend|integration|test-data
deliverables/                     ← Excel workbooks + demo-script.md
demo/screenshots|demo-video
```

**Planned additions** (create only when that phase starts):
- `docker-compose.yml` and `.env.example` at repo root (Deployment)
- `.github/workflows/` or the CI system's equivalent (Deployment)
- `ops/` — runbook, Prometheus config and alert rules, Grafana dashboards (Support/SRE)
- `tests/load/` — Locust scripts (performance testing artifact)
- `architecture/adr/` — architecture decision records

---

## 8. Technology Stack (approved — CL-17)

Source: **AI Manthan Hackathon – Prerequisites Guide**, §3 (compatible versions) and §4 (Track A stack). The guide says: *use exactly these versions and do not substitute newer ones during the hackathon.* The stack is now fixed. **Changing any item below needs explicit approval.**

### 8.1 Stack

| Layer | Choice and version | Source | Notes |
|---|---|---|---|
| OS (laptops) | Windows 10/11; macOS/Linux use the same versions | Guide intro | Scripts must work in PowerShell; prefer Docker for anything OS-specific |
| Frontend | **React 19**, built with **Vite 7 or later** | Owner (CL-17); Vite from guide §4 | Node.js **26.13.1** with npm. Pick JavaScript or TypeScript once and keep it |
| Backend | **Spring Boot 4.1.x on JDK 21 (Temurin)** | Owner (CL-17); version from guide §3/§4 | Use the **Maven wrapper** (`mvnw` / `mvnw.cmd`) committed in the repo, so no build tool is installed on the laptop. `JAVA_HOME` must point to JDK 21 |
| Request validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | Part of Spring Boot | Every DTO validated; central `@RestControllerAdvice` error handler |
| Database | **PostgreSQL 18.x** (`postgres:18` Docker image) | Owner (CL-17); guide §3 | Never install PostgreSQL on the laptop |
| Data access | Spring Data JPA (Hibernate) + PostgreSQL JDBC driver | Proposed (standard for Spring Boot) | Repository methods or parameterized JPQL only — no string-built SQL |
| Migrations | Flyway | Proposed (Spring Boot auto-configures it) | Versioned scripts in `database/migrations/` (copied into the backend classpath at build) |
| Auth | Spring Security with JWT bearer tokens; BCrypt password hashing for seeded synthetic users | Proposed | Login method still open (Q-22) |
| Receipt storage | Local Docker volume, files named by generated ID, SHA-256 stored | Proposed | Company AWS is not allowed (§8.2) |
| Synthetic data | Python **Faker** script in `database/seeds/` (same Python 3.13 env as pytest) | Guide §3 | Fixed random seed; writes to PostgreSQL |
| Backend and API tests | **pytest** on Python 3.13 with `httpx`/`requests`, calling the running Spring Boot API | Owner (CL-17) | Covers API integration, approval-chain and policy behaviour end to end through HTTP. Tests in `tests/backend/` and `tests/integration/` |
| Java unit tests | **JUnit 5** (`spring-boot-starter-test`) | **Owner (CL-18)** | Needed for test-first unit tests of the policy and approval-chain engines (§16.1); guide §4 allows JUnit |
| Manual API checks | Postman (VS Code extension) or curl | Guide §4 | |
| Frontend component tests | Vitest + React Testing Library | Proposed | Needed for §16.4 |
| UI / E2E tests | Playwright 1.60 or later | Guide §4 (kept — owner did not remove it) | Needed for the 10 written E2E cases. If browser download is blocked, use channel `msedge` or `chrome` |
| Load testing | Locust (pip, Python 3.13 env) | Guide §4 (kept) | Performance-testing artifact |
| Delivery | **Docker Compose** | Owner (CL-17) | One `docker compose up` starts PostgreSQL, backend, frontend, Prometheus and Grafana and seeds demo data (NFR-006) |
| CI | GitHub Actions, or a local script running lint and tests | Required by `requirements.docx` (Deployment phase); guide §4 | Runs `mvnw verify`, pytest, frontend tests; dependency check with OWASP Dependency-Check (Maven) and `npm audit` (NFR-007) |
| Monitoring | **Prometheus + Grafana** (Docker images) | Owner (CL-17) | Spring Boot Actuator + Micrometer Prometheus registry exposes `/actuator/prometheus` |
| Linting / formatting | Checkstyle or Spotless (Java); ESLint + Prettier (frontend) | Proposed | |
| UI design | Wireframes drafted with Claude; Figma only if approved | Guide §4 | |
| Tracking | Shared Excel / Google Sheets workbooks (`deliverables/*.xlsx`) | Guide §2 | Replaces Jira |

**Items marked "Proposed"** are not named in the guide but are needed to meet this file's standards. Each must be confirmed in ADR-000 before it is added; do not add any other dependency without a stated reason (§7.4).

**Not used:** FastAPI or a Node backend, Python 3.15, JDK 27, SQLite, an installed Maven/Gradle (use the wrapper), pip-installed Superset/Airflow, k6 on the host, the PostgreSQL desktop installer, Jira, company AWS.

**Python's role:** Python 3.13 is used only for pytest API tests, the Faker seed script and Locust — never for application code.

### 8.2 Event rules (mandatory, from the guide)
1. **Synthetic or public data only.** No client or confidential data on the laptop or in prompts.
2. **Open-source or free tools only.** Jira and company AWS resources are not used.
3. **Code stays on the laptop or in the company Git repository.**
4. **Start only from the starter templates.** No pre-built solution code. If a starter template is provided, build on it; otherwise scaffold with the official generators (e.g. `npm create vite@latest`) and write the rest during the event.
5. **Use exactly the versions in §8.1.** Do not upgrade during the hackathon.
6. Installers need admin rights; pip/npm packages, Docker images and VS Code extensions do not. Never ask a participant to install system software — use Docker or packages instead.

### 8.3 Environment check
Before Phase 0, confirm (from guide §6): `git --version`; `java -version` shows 21 and `JAVA_HOME` points to JDK 21; `py -3.13 --version` and a working `.venv` with `pip install pytest httpx faker`; `node -v` = v26.13.1 and a Vite React build; Playwright Chromium install (or Edge/Chrome fallback); `docker run --rm hello-world`; PostgreSQL 18 in Docker. Record results in `PROGRESS.md`.

---

## 9. Architecture Standards

1. **Modular architecture with separation of concerns.** Frontend, backend, database and integrations are logically separate. No business logic in UI components or HTTP route handlers.
2. Backend layers (Spring Boot packages): **`controller` → `service` (business rules) → `repository` (Spring Data JPA) → database**, plus `dto`, `domain`, `config`, `security`, `exception`. The **policy engine** and the **approval-chain engine** are separate, pure, independently testable modules.
3. **Contract first.** Define or update `architecture/api-contracts.yaml` before implementing or changing any endpoint or any frontend call.
4. **Consistent naming:**

| Item | Convention | Example |
|---|---|---|
| API paths | plural, kebab-case nouns under `/api/v1` | `/api/v1/claims/{claimId}/line-items` |
| JSON fields | camelCase | `claimId`, `submittedAt` |
| DB tables / columns | snake_case, plural tables | `claim_line_items.receipt_hash` |
| Enum values | UPPER_SNAKE | `PENDING_MANAGER` |
| Policy rule IDs | `POL-###` | `POL-006` |
| Frontend components | PascalCase | `ClaimLineItemForm` |
| Requirement IDs | `FR-###`, `NFR-###`, `POL-###`, `DATA-###` | |
| Other IDs | `API-###`, `TC-<FE/BE/INT/E2E>-###`, `BUG-###`, `ADR-###`, `Q-##`, `ASM-###` | |

5. Required architecture outputs (from source): context and container diagram, claim state machine, policy-as-configuration design, ADR on approval-chain modelling, plus the approval-bypass review (§5).

---

## 10. Frontend Standards

1. Build **reusable UI components** (form fields, data table, status badge, money display, receipt viewer, confirm dialog, empty/error states). No copy-pasted variants.
2. **Responsive design**; claim capture must work on a phone (NFR-001).
3. **Client-side validation** that mirrors the API schema (required fields, types, lengths, amounts > 0). Client validation is for user experience only — the backend is the security boundary.
4. Every data-driven screen handles **loading, empty, success and error** states.
5. **Prevent duplicate submissions:** disable the action button while a request is in flight; use idempotency for submit, approve, bulk approve and payout actions.
6. **Accessible forms:** labels linked to inputs, keyboard navigation, visible focus, sufficient contrast, errors announced and linked to the field.
7. **Meaningful error messages** that tell the user what went wrong and how to fix it. Never show stack traces or raw server errors.
8. Screens required by the source: claim capture (mobile-friendly), side-by-side receipt viewer for approvers (NFR-002), dense data tables with bulk actions (NFR-003), auditor review queue with filters (FR-009/010), payout batch and export (FR-012/013), dashboard (FR-015–017), audit trail view.
9. The UI only shows actions the user is allowed to perform, but **the backend still enforces every permission**.
10. Never store tokens or sensitive data in places readable by injected scripts unless the approved auth design says so.

---

## 11. Backend Standards

1. **Server-side validation for every external input**: body, path, query, headers and files. Reject unknown fields where practical.
2. **Business rules are enforced on the backend** — policy rules, thresholds, state transitions, self-approval blocks, delegation rules and visibility rules.
3. **Appropriate HTTP status codes:** 200, 201, 204, 400 (malformed), 401 (not authenticated), 403 (not allowed), 404 (not found or not visible), 409 (state conflict, e.g. approving an already-approved claim), 413 (file too large), 415 (bad file type), 422 (business-rule failure), 429 (rate limit), 500.
4. **Consistent exception handling:** one central error handler (`@RestControllerAdvice`); every error uses the standard shape `{ "code", "message", "details", "traceId" }`. No stack traces in responses.
5. **Authentication and authorization** on every non-public endpoint, including **object-level checks** (FR-019, B-10).
6. **Never expose secrets or sensitive data** in responses, logs, errors or the frontend bundle.
7. **Parameterized queries or safe ORM operations only.** No SQL built by string concatenation.
8. Multi-step changes (state change + audit entry, adding claims to a payout batch) run in **one database transaction**.
9. The **audit trail** is written by the same service that performs the action, inside the same transaction, and is append-only (NFR-004).
10. Structured logging with a request/trace ID. Never log receipt contents, passwords or tokens.
11. Expose metrics needed for SRE: approval queue size, oldest pending approval age, page/API latency (NFR-008).

---

## 12. API Design Standards

1. Maintain the contract in **OpenAPI 3** at `architecture/api-contracts.yaml`. The contract is updated **before** implementation.
2. For every operation, define: `operationId`, summary, tags, `x-api-id` (`API-###`), `x-requirements` (FR/POL IDs), request schema, response schemas, all error responses, and examples.
3. Schemas include **validation rules, mandatory fields and data types** (formats, min/max, enums, max lengths, file size/type for uploads).
4. Document **authentication**, the **roles allowed** for each operation (`x-roles`), and **rate limits** where applicable (`x-rate-limit`).
5. Keep frontend and backend in sync with the contract: shared types are generated from or validated against it, and an automated contract test runs in CI.
6. Do **not invent** endpoints, fields or behaviours that do not trace to §2 or an answered §6 question.
7. Every operation appears in `deliverables/api-inventory.xlsx`, and the row count matches the contract.
8. Lint the contract (for example with Redocly or Spectral) and fix all errors before implementation.

---

## 13. Security and Guardrails

1. **Least privilege** everywhere: user roles, database users (the app user cannot update/delete audit rows), CI tokens, container users.
2. **Validate and sanitize** all external input; encode output.
3. Protect against **injection** (SQL, command, path traversal), **XSS**, **CSRF** (if cookie-based auth), **IDOR / unauthorized access**, mass assignment and open redirects.
4. **CSV export safety:** neutralise values starting with `=`, `+`, `-`, `@`, tab or carriage return to prevent spreadsheet formula injection in the payout file.
5. **Receipt uploads:** enforce a maximum size and an allow-list of file types (pending Q-21); check the real file content type, not just the extension; store with generated names outside the web root; compute and store the SHA-256 hash (POL-007); serve receipts only to authorised users.
6. **Never commit credentials, API keys or secrets.** Use environment variables. Provide `.env.example` with placeholder values only. `.env` is git-ignored.
7. **Never rely on frontend validation for security.**
8. Rate-limit authentication and upload endpoints.
9. Run the basic security checklist (NFR-009) and record results in `architecture/security-review.md`. Mark each finding real or false alarm with a reason.
10. CI includes a dependency vulnerability check (NFR-007).

---

## 14. Development Process

### 14.1 Phases (small, independently verifiable)

| # | Phase | Command | Exit criteria |
|---|---|---|---|
| 0 | Setup | — | Environment check (§8.3) passed; `PROGRESS.md` created; repo inspected; Git initialised |
| 1 | Requirement analysis | `/analyse-requirements` | `requirement-analysis.md` complete; §6 questions sent; traceability workbook started |
| 2 | Architecture | `/design-architecture` | Diagrams, state machine, policy-config design, ADR-000 (records the §8 stack and confirms the "Proposed" items) and ADR-001 (approval chain), bypass review |
| 3 | API design | `/generate-apis` | Contract linted; API inventory complete |
| 4a | Policy engine (test-first) | `/build-backend` | Failing tests for every POL rule written first, then engine; all policy tests pass |
| 4b | Approval chain + delegation | `/build-backend` | Threshold and delegation tests pass |
| 4c | Remaining backend (claims, queue, payout, dashboard, audit) | `/build-backend` | API tests pass for every operation |
| 5 | Frontend | `/build-frontend` | Required screens done; component tests pass; integrated with backend |
| 6 | Validation | `/run-validation` | All suites run; 10 written E2E cases; defect log; security checklist; validation matrix |
| 7 | Deployment | — | Docker Compose; CI with tests + dependency check; one-command seeded demo |
| 8 | Support (SRE) | — | Metrics, dashboard, oldest-pending-approval alert, runbook |
| 9 | Demo | `/prepare-demo` | Demo script, screenshots, backup video, acceptance checklist (§17) |

### 14.2 Plan before code (every task)
Before writing code, post a short plan containing:
- the requirement IDs addressed;
- files to create or change (and why each existing file is touched);
- any contract, schema or migration changes;
- new dependencies (with reason) — or "none";
- tests to be written;
- open questions that affect the task.

Then **wait for approval**. Small follow-up fixes inside an approved plan do not need a new plan.

### 14.3 Things you must never do
- Invent APIs, database fields, policy rules or business rules.
- Change the technology stack without approval.
- Claim tests passed without running them. Always show the command and the result summary.
- Remove, skip or weaken an existing test to make a build pass. Fix the code, or raise the issue.
- Force-push, rewrite shared history, run `git reset --hard`, or discard the user's uncommitted changes.
- Commit `.env` files, secrets, real personal data or large binaries.

### 14.4 Git checkpoints
- Commit at the end of each verified step with a message like `feat(policy): POL-005 receipt mandatory above limit [FR-004]`.
- Commit types: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `ci`.
- Before risky changes, make a checkpoint commit first.
- Work on feature branches if the team uses them; never force-push.

---

## 15. Progress Tracker

Maintain `PROGRESS.md` at the repository root. Update it at the end of every task.

Required content:
- current phase and status per phase (Not started / In progress / Done / Blocked);
- completed items with requirement IDs and commit hashes;
- tests run: command, date, passed/failed counts;
- open blockers, including unanswered critical questions from §6;
- next planned step.

---

## 16. Testing Standards

1. **Unit tests for business logic** — every policy rule (POL-001 to POL-009) has its own JUnit 5 tests (CL-18), written **before** the policy engine. The same rules are also covered by pytest API tests.
2. **Approval tests** — every threshold boundary on the claim total (24,999.99 / 25,000.00 / 25,000.01 and 99,999.99 / 1,00,000.00 / 1,00,000.01 INR, per CL-03), auto-approve only at ≤ 25,000 with no weekend line (CL-02), weekend routing to manager, rejection with and without a comment (CL-04), send-back restart (CL-12), finance head's claim routed to auditor (CL-16), sample-audit hold blocking payout (CL-14), visibility of other users' claims returning 404 (CL-15), every chain path, send-back, and delegation (active, expired, invalid delegate, self-approval attempt).
3. **API integration tests (pytest, against the running backend)** — every operation: happy path, validation errors, 401, 403 (including another employee's claim), 404, 409 state conflicts.
4. **Frontend component tests** — forms, validation messages, loading/empty/error states, duplicate-submit prevention, bulk actions.
5. **End-to-end** — at least **10 written E2E cases** covering the demo story (required by source).
6. Every feature has **positive, negative, boundary and edge-case** scenarios. Examples: amount 0, negative, very large; receipt at exactly 500 INR; same receipt with different file name; weekend dates; claims with no manager.
7. **Never remove or weaken existing tests** to make a build pass.
8. **Run tests after every significant implementation phase** and record results in `PROGRESS.md`. Report actual results only.
9. Test data lives in `tests/test-data/` and is synthetic.
10. Record defects as `BUG-###` in the Defects sheet of `deliverables/test-cases.xlsx`.

---

## 17. Documentation and Deliverables

Keep documentation synchronized with the actual implementation. A task is not done until its docs are updated.

| Deliverable | Location | Updated when |
|---|---|---|
| Requirement analysis | `requirements/requirement-analysis.md` | Phase 1, and whenever a §6 answer arrives |
| Solution design, diagrams, DB design | `architecture/*.md` | Phase 2 and any design change |
| ADRs | `architecture/adr/` | Every significant decision |
| Security & bypass review | `architecture/security-review.md` | Phase 2 and Phase 6 |
| API contract | `architecture/api-contracts.yaml` | Before any endpoint change |
| **Requirement mapping / traceability** | `deliverables/requirement-traceability.xlsx` | Every phase |
| **API inventory** | `deliverables/api-inventory.xlsx` | API phase and every endpoint change |
| **Test cases + defect log** | `deliverables/test-cases.xlsx` | Every test run |
| **Validation matrix** | `deliverables/validation-matrix.xlsx` | Phase 6 and final acceptance |
| Demo script | `deliverables/demo-script.md` | Phase 9 |
| Runbook, alerts | `ops/` | Phase 8 |
| Setup instructions | `README.md` files | Whenever setup changes |

---

## 18. Definition of Done (per task)

- [ ] Plan was approved before coding
- [ ] Traces to documented requirement IDs; no invented behaviour
- [ ] API contract updated first (if endpoints changed) and still lints cleanly
- [ ] Server-side validation, authorization and audit entries in place
- [ ] Tests written (positive, negative, boundary, edge) and **actually run and passing**
- [ ] No existing test removed or weakened
- [ ] No secrets, no console errors, no TODOs on the demo path
- [ ] Docs, traceability and relevant Excel workbook updated
- [ ] `PROGRESS.md` updated and a Git checkpoint committed

---

## 19. Final Acceptance Checklist

- [ ] All agreed acceptance criteria satisfied, with evidence in `validation-matrix.xlsx`
- [ ] Every Must-have item FR-001 to FR-020 and POL-001 to POL-009 validated, and every confirmed clarification CL-01 to CL-17 honoured
- [ ] Frontend and backend integration works end to end
- [ ] Every documented API verified against the contract
- [ ] Error handling verified (validation, 401, 403, 404, 409, upload limits)
- [ ] Security controls verified, including the approval-bypass list in §5
- [ ] Audit trail confirmed append-only
- [ ] Synthetic data loaded: 150 employees, 8 departments, hierarchy, 3,000 claims with planted duplicates and breaches
- [ ] Docker Compose brings up a seeded demo with one command; CI is green
- [ ] Metrics, dashboard, alert and runbook in place
- [ ] Working demo, demo script, screenshots and backup video ready

---

## 20. Decision Log

| Date | Decision | Reason | Approved by |
|---|---|---|---|
| 2026-11-27 | Added JUnit 5 for Java unit tests; pytest stays for backend API and integration tests | pytest cannot unit-test Java code; policy engine must be built test-first | Project owner |
| 2026-11-27 | Stack confirmed: React 19 (Vite), **Spring Boot 4.1.x / JDK 21**, PostgreSQL 18 in Docker, pytest for backend and API tests, Docker Compose, Prometheus + Grafana. FastAPI withdrawn | Owner's decision; versions per AI Manthan Prerequisites Guide | Project owner |
| 2026-11-27 | Recorded clarifications CL-12 to CL-16 (send-back restarts chain, delegates limited to finance auditor/head, sampled claims held from payout, own-claim visibility, finance head's claims approved by auditor) | Answers to Q-08, Q-09, Q-18, Q-19, Q-26 | Project owner |
| 2026-11-27 | Recorded clarifications CL-07 to CL-11 (limit breach auto-rejects claim, tier-1 city list, skip missing approver, policy config by finance head/admin, payout CSV format decided by Claude) | Answers to Q-01, Q-02, Q-10, Q-12, Q-16, Q-17 | Project owner |
| 2026-11-27 | Recorded clarifications CL-01 to CL-06 (policy limits, auto-approve ceiling, weekend routing, claim-total thresholds, outright rejection, date + receipt-number uniqueness, Paid on export) | Answers from project owner to Q-01–Q-07, Q-12, Q-14, Q-15 | Project owner |
