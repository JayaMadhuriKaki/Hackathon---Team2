# User Stories

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals |
| Version | 1.1 — **approved baseline** (project owner, 2026-11-27) |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Companion files | requirement-analysis.md · functional-requirements.md · non-functional-requirements.md · business-rules.md · user-stories.md · open-questions.md |

Story IDs keep the team's numbering from `deliverables/user-stories.xlsx` (US-01 → US-001) so existing references stay valid. US-043 to US-046 are new. Where a story here differs from the team's spreadsheet, the **Note** says why; confirmed decisions (CL) take precedence, and conflicts are listed in `open-questions.md`. Acceptance criteria IDs are `<story>-AC<n>`.

## Index

| Story | Epic | Actor | Priority | Related requirements |
|---|---|---|---|---|
| US-001 | Access | Any user | Must | FR-032, FR-019, NFR-016 |
| US-002 | Access | Employee | Must | FR-019, FR-031, BR-028, NFR-005 |
| US-003 | Claims | Employee | Must | FR-001, FR-021, FR-022, NFR-020, NFR-021 |
| US-004 | Claims | Employee | Must | FR-002 |
| US-005 | Claims | Employee | Must | FR-001, BR-007, NFR-018 |
| US-006 | Claims | Employee | Must | FR-003, FR-004, FR-023, FR-018, BR-005 |
| US-007 | Claims | Employee | Must | FR-019, FR-018 |
| US-008 | Claims | Employee | Must | FR-007, FR-025, BR-019 |
| US-009 | Claims | Employee | Must | NFR-001 |
| US-010 | Policy | Finance head | Must | BR-001, BR-010, FR-004 |
| US-011 | Policy | Finance head | Must | BR-002, BR-003, BR-010, FR-022, BR-030 |
| US-012 | Policy | Finance head | Must | BR-004, BR-010 |
| US-013 | Policy | Finance auditor | Must | BR-005 |
| US-014 | Policy | Finance auditor | Must | BR-007, FR-023 |
| US-015 | Policy | Finance auditor | Must | BR-006 |
| US-016 | Policy | Line manager | Must | BR-008, BR-012 |
| US-017 | Policy | Finance head | Must | BR-030, BR-011, FR-026, NFR-011 |
| US-018 | Approvals | Line manager | Must | FR-005, FR-020, FR-031, BR-013, BR-018 |
| US-019 | Approvals | Any approver | Must | FR-007 |
| US-020 | Approvals | Any approver | Must | FR-006, FR-018 |
| US-021 | Approvals | Finance auditor | Must | FR-005, BR-013, BR-014 |
| US-022 | Approvals | Finance head | Must | FR-005, BR-013, BR-014 |
| US-023 | Approvals | Any approver | Must | FR-008, BR-020, BR-021, BR-017 |
| US-024 | Approvals | Finance auditor | Must | BR-017, BR-016, BR-015 |
| US-025 | Approvals | Line manager | Must | NFR-002 |
| US-026 | Audit queue | Finance auditor | Must | FR-009, NFR-003 |
| US-027 | Audit queue | Finance auditor | Must | FR-010, BR-017, NFR-020 |
| US-028 | Audit queue | Finance auditor | Must | FR-011, FR-024, BR-022, BR-023 |
| US-029 | Payout | Finance head | Must | FR-012, BR-024, BR-026 |
| US-030 | Payout | Finance head | Must | FR-013, FR-014, BR-025, BR-031, NFR-019, FR-030, NFR-021 |
| US-031 | Dashboard | Finance head | Must | FR-015 |
| US-032 | Dashboard | Finance head | Must | FR-016, FR-017 |
| US-033 | Audit | Finance auditor | Must | FR-018, BR-029, NFR-004 |
| US-034 | Data | Team | Must | FR-027, FR-028, FR-029, FR-030, NFR-012 |
| US-035 | Ops | Support engineer | Must | NFR-008 |
| US-036 | Ops | Support engineer | Must | NFR-008 |
| US-037 | Ops | Support engineer | Must | NFR-010 |
| US-038 | Ops | Team | Must | NFR-007, NFR-015 |
| US-039 | Ops | Team | Must | NFR-006 |
| US-040 | Notifications | Employee | Could | — |
| US-041 | Policy | Finance head | Should | FR-026, BR-027 |
| US-042 | Claims | Employee | Could | — |
| US-043 | Policy | Finance auditor | Must | BR-009, FR-021 |
| US-044 | Audit | Finance head | Should | FR-018, BR-028 |
| US-045 | Quality | Team | Must | NFR-015 |
| US-046 | Quality | Team | Must | NFR-009, NFR-016, NFR-017 |

## US-001 — Sign in

| Field | Detail |
|---|---|
| Story ID | US-001 |
| Epic | Access |
| Actor | Any user |
| Description | As a user, I want to sign in, so that I see only the screens and claims my role allows. |
| Business value | Identity is the basis for own-claim privacy and role-based approvals. |
| Related requirements | FR-032, FR-019, NFR-016 |
| Priority | Must |
| Note | Sign-in method open (OQ-022); team proposes email + demo password + JWT. |

**Acceptance criteria**

- **US-001-AC1** — *Given* a seeded user, *when* they sign in with valid credentials, *then* they are signed in and their role is known.
- **US-001-AC2** — *Given* a user, *when* they sign in with invalid credentials, *then* an error is shown and no access is granted.
- **US-001-AC3** — *Given* a signed-in user, *when* they open a screen their role does not allow, *then* access is refused.

## US-002 — Be the only person who can see my claims

| Field | Detail |
|---|---|
| Story ID | US-002 |
| Epic | Access |
| Actor | Employee |
| Description | As an employee, I want to be the only person who can see my claims, so that my expenses stay private. |
| Business value | Meets the privacy requirement and prevents data leaks between employees. |
| Related requirements | FR-019, FR-031, BR-028, NFR-005 |
| Priority | Must |

**Acceptance criteria**

- **US-002-AC1** — *Given* employee A, *when* they list claims, *then* only A's own claims are returned.
- **US-002-AC2** — *Given* employee A, *when* they request employee B's claim by ID, *then* the claim is not found and no data is revealed.
- **US-002-AC3** — *Given* an approver, *when* they open their approval list, *then* they see only claims assigned to them or that they acted on.

## US-003 — Create a claim with several line items

| Field | Detail |
|---|---|
| Story ID | US-003 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to create a claim with several line items, so that I can claim a whole trip at once. |
| Business value | Replaces email and Excel with one structured claim. |
| Related requirements | FR-001, FR-021, FR-022, NFR-020, NFR-021 |
| Priority | Must |
| Note | Team version also rejects future dates — not documented (OQ-031). |

**Acceptance criteria**

- **US-003-AC1** — *Given* an employee creating a claim, *when* they add a line, *then* category, amount (INR), expense date, vendor and receipt number are captured.
- **US-003-AC2** — *Given* a hotel line, *when* it is saved, *then* city and number of nights are also required.
- **US-003-AC3** — *Given* a line, *when* the amount is 0 or negative, *then* a field error is shown and the input is kept.

## US-004 — Save my claim as a draft

| Field | Detail |
|---|---|
| Story ID | US-004 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to save my claim as a draft, so that I can finish it later. |
| Business value | Employees can build claims over time without losing work. |
| Related requirements | FR-002 |
| Priority | Must |

**Acceptance criteria**

- **US-004-AC1** — *Given* a claim in progress, *when* the employee saves it as a draft, *then* it is stored as Draft and can be reopened, edited and lines added or removed.
- **US-004-AC2** — *Given* a draft, *when* an approver looks for it, *then* it is not visible to any approver.
- **US-004-AC3** — *Given* a draft, *when* it is saved, *then* no policy checks are run.

## US-005 — Upload a receipt for a line

| Field | Detail |
|---|---|
| Story ID | US-005 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to upload a receipt for a line, so that approvers can verify the spend. |
| Business value | Receipts are the evidence for every reimbursement. |
| Related requirements | FR-001, BR-007, NFR-018 |
| Priority | Must |
| Note | Allowed types/size open (OQ-021); team proposes PDF/JPG/PNG up to 5 MB. |

**Acceptance criteria**

- **US-005-AC1** — *Given* a line, *when* the employee uploads an allowed file, *then* it is stored and its SHA-256 hash recorded on the line.
- **US-005-AC2** — *Given* a file of a disallowed type or size, *when* it is uploaded, *then* it is rejected with a clear message.
- **US-005-AC3** — *Given* an approver of the claim, *when* they open the line, *then* they can view the receipt.

## US-006 — Submit my claim

| Field | Detail |
|---|---|
| Story ID | US-006 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to submit my claim, so that it starts moving to payment. |
| Business value | Starts automated policy checking and approval. |
| Related requirements | FR-003, FR-004, FR-023, FR-018, BR-005 |
| Priority | Must |
| Note | Team version also locks amounts after submit (OQ-041). |

**Acceptance criteria**

- **US-006-AC1** — *Given* a draft with at least one line, *when* the employee submits it, *then* the policy engine checks every line and shows the outcome with a reason per line.
- **US-006-AC2** — *Given* a submitted claim, *when* the outcome is decided, *then* it is auto-approved, auto-rejected or routed for approval, and an audit entry is written.
- **US-006-AC3** — *Given* a line above 500 INR without a receipt, *when* the employee submits, *then* the defined handling applies (blocked at submission per ASM-003, pending OQ-028).

## US-007 — See the status and history of my claims

| Field | Detail |
|---|---|
| Story ID | US-007 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to see the status and history of my claims, so that I don't have to chase anyone. |
| Business value | Removes follow-up emails and makes approval delays visible. |
| Related requirements | FR-019, FR-018 |
| Priority | Must |

**Acceptance criteria**

- **US-007-AC1** — *Given* an employee, *when* they open My claims, *then* each claim shows status, total, current approver and age.
- **US-007-AC2** — *Given* a claim, *when* the employee opens it, *then* every action, comment and outcome is listed in order.

## US-008 — Edit and resubmit a claim that was sent back

| Field | Detail |
|---|---|
| Story ID | US-008 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to edit and resubmit a claim that was sent back, so that I can answer the approver's question. |
| Business value | Fixes problems without starting a new claim. |
| Related requirements | FR-007, FR-025, BR-019 |
| Priority | Must |

**Acceptance criteria**

- **US-008-AC1** — *Given* a claim sent back to its owner, *when* the owner opens it, *then* the lines and receipts are editable and the approver's comment is shown.
- **US-008-AC2** — *Given* a corrected claim, *when* the owner resubmits it, *then* policy is re-run, the chain is recalculated from the new total and restarts at the manager.
- **US-008-AC3** — *Given* a resubmitted claim, *when* the chain restarts, *then* approvals given before the send-back no longer count.

## US-009 — Capture a claim comfortably on my phone

| Field | Detail |
|---|---|
| Story ID | US-009 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want to capture a claim comfortably on my phone, so that I can claim on the go. |
| Business value | Claims are filed when the expense happens, not days later. |
| Related requirements | NFR-001 |
| Priority | Must |

**Acceptance criteria**

- **US-009-AC1** — *Given* a phone-width screen (375 px), *when* the employee fills the claim form, *then* it is usable with no horizontal scrolling.
- **US-009-AC2** — *Given* a phone, *when* the employee adds a receipt, *then* the camera or file picker can be used.

## US-010 — Meals limited to 1,500 INR per day

| Field | Detail |
|---|---|
| Story ID | US-010 |
| Epic | Policy |
| Actor | Finance head |
| Description | As a finance head, I want meals limited to 1,500 INR per day, so that meal spend stays within policy. |
| Business value | Stops meal overspend automatically. |
| Related requirements | BR-001, BR-010, FR-004 |
| Priority | Must |
| Note | Team version routes with a flag instead of auto-rejecting — conflicts with CL-07 (OQ-038). Aggregation scope OQ-011. |

**Acceptance criteria**

- **US-010-AC1** — *Given* meal spend for an employee and date of exactly 1,500 INR, *when* the claim is submitted, *then* the meal rule passes.
- **US-010-AC2** — *Given* meal spend for an employee and date above 1,500 INR, *when* the claim is submitted, *then* the whole claim is auto-rejected with a reason naming the line, limit and amount.
- **US-010-AC3** — *Given* the meal rule, *when* unit tests run, *then* cases below, at and above the limit pass.

## US-011 — Hotels limited by city tier

| Field | Detail |
|---|---|
| Story ID | US-011 |
| Epic | Policy |
| Actor | Finance head |
| Description | As a finance head, I want hotels limited by city tier, so that hotel spend stays within policy. |
| Business value | Applies the right limit for expensive cities. |
| Related requirements | BR-002, BR-003, BR-010, FR-022, BR-030 |
| Priority | Must |
| Note | Team version routes with a flag (OQ-038). |

**Acceptance criteria**

- **US-011-AC1** — *Given* a hotel line in a tier-1 city, *when* amount ÷ nights is above 6,000 INR, *then* the claim is auto-rejected with a reason.
- **US-011-AC2** — *Given* a hotel line in another city, *when* amount ÷ nights is above 4,000 INR, *then* the claim is auto-rejected with a reason.
- **US-011-AC3** — *Given* the tier-1 list, *when* it is changed in configuration, *then* the new list is used without a code change.

## US-012 — Local cabs limited to 2,000 INR per trip

| Field | Detail |
|---|---|
| Story ID | US-012 |
| Epic | Policy |
| Actor | Finance head |
| Description | As a finance head, I want local cabs limited to 2,000 INR per trip, so that cab spend stays within policy. |
| Business value | Stops cab overspend automatically. |
| Related requirements | BR-004, BR-010 |
| Priority | Must |
| Note | Team version routes with a flag (OQ-038). One line = one trip (OQ-027). |

**Acceptance criteria**

- **US-012-AC1** — *Given* a cab line of exactly 2,000 INR, *when* the claim is submitted, *then* the cab rule passes.
- **US-012-AC2** — *Given* a cab line above 2,000 INR, *when* the claim is submitted, *then* the claim is auto-rejected with a reason.
- **US-012-AC3** — *Given* the cab rule, *when* unit tests run, *then* below, at and above cases pass.

## US-013 — Lines above 500 INR to require a receipt

| Field | Detail |
|---|---|
| Story ID | US-013 |
| Epic | Policy |
| Actor | Finance auditor |
| Description | As a finance auditor, I want lines above 500 INR to require a receipt, so that unsupported spend is never paid. |
| Business value | No reimbursement without evidence. |
| Related requirements | BR-005 |
| Priority | Must |
| Note | Team version auto-rejects; ASM-003 blocks submission instead (OQ-028). |

**Acceptance criteria**

- **US-013-AC1** — *Given* a line of exactly 500 INR without a receipt, *when* the claim is submitted, *then* the receipt rule passes.
- **US-013-AC2** — *Given* a line above 500 INR without a receipt, *when* the claim is submitted, *then* the defined handling applies (OQ-028).
- **US-013-AC3** — *Given* the receipt rule, *when* unit tests run, *then* below, at and above cases pass.

## US-014 — A receipt file used twice to be caught

| Field | Detail |
|---|---|
| Story ID | US-014 |
| Epic | Policy |
| Actor | Finance auditor |
| Description | As a finance auditor, I want a receipt file used twice to be caught, so that the same bill is never paid twice. |
| Business value | Directly addresses 'duplicate receipts get paid'. |
| Related requirements | BR-007, FR-023 |
| Priority | Must |
| Note | Team version auto-rejects; source says 'flagged' (OQ-030). |

**Acceptance criteria**

- **US-014-AC1** — *Given* a line whose receipt hash matches an earlier line, *when* the claim is submitted, *then* the line is flagged as a duplicate, naming the earlier claim.
- **US-014-AC2** — *Given* a claim with a duplicate flag, *when* the outcome is decided, *then* the defined consequence applies (OQ-030).

## US-015 — A line matching an earlier line's vendor, amount and date flagged

| Field | Detail |
|---|---|
| Story ID | US-015 |
| Epic | Policy |
| Actor | Finance auditor |
| Description | As a finance auditor, I want a line matching an earlier line's vendor, amount and date flagged, so that likely duplicates get a human look. |
| Business value | Catches duplicates that use different receipt files. |
| Related requirements | BR-006 |
| Priority | Must |
| Note | Team limits matching to the same employee; source does not say (OQ-029). |

**Acceptance criteria**

- **US-015-AC1** — *Given* a line with the same vendor, amount and date as an earlier line, *when* the claim is submitted, *then* the line is flagged as a duplicate.
- **US-015-AC2** — *Given* the duplicate rule, *when* unit tests run, *then* match and near-miss cases pass.

## US-016 — Weekend spend flagged

| Field | Detail |
|---|---|
| Story ID | US-016 |
| Epic | Policy |
| Actor | Line manager |
| Description | As a line manager, I want weekend spend flagged, so that I can question it before approving. |
| Business value | Puts unusual spend in front of the manager. |
| Related requirements | BR-008, BR-012 |
| Priority | Must |

**Acceptance criteria**

- **US-016-AC1** — *Given* a line dated Saturday or Sunday, *when* the claim is submitted, *then* the line is flagged and the claim is routed to the manager.
- **US-016-AC2** — *Given* a weekend claim that would otherwise be auto-approved, *when* the outcome is decided, *then* it is still routed to the manager.

## US-017 — Policy rules kept as configuration

| Field | Detail |
|---|---|
| Story ID | US-017 |
| Epic | Policy |
| Actor | Finance head |
| Description | As a finance head, I want policy rules kept as configuration, so that I can change a limit without a release. |
| Business value | Policy changes without developer effort. |
| Related requirements | BR-030, BR-011, FR-026, NFR-011 |
| Priority | Must |
| Note | Team version auto-approves only up to 5,000 INR — conflicts with CL-02 (OQ-037). |

**Acceptance criteria**

- **US-017-AC1** — *Given* seeded policy configuration, *when* a limit value is changed, *then* the next evaluation uses the new value with no code change.
- **US-017-AC2** — *Given* a claim with total ≤ 25,000 INR, no breaches and no weekend line, *when* it is submitted, *then* it is auto-approved.
- **US-017-AC3** — *Given* a claim with total above 25,000 INR and no breaches, *when* it is submitted, *then* it is routed into the approval chain.

## US-018 — See claims waiting for me and approve or reject them

| Field | Detail |
|---|---|
| Story ID | US-018 |
| Epic | Approvals |
| Actor | Line manager |
| Description | As a line manager, I want to see claims waiting for me and approve or reject them, so that my team gets paid on time. |
| Business value | Cuts manager response time from days. |
| Related requirements | FR-005, FR-020, FR-031, BR-013, BR-018 |
| Priority | Must |

**Acceptance criteria**

- **US-018-AC1** — *Given* claims at my step, *when* I open my queue, *then* they are listed oldest first.
- **US-018-AC2** — *Given* a claim at my step, *when* I approve it, *then* it moves to the next required step or to Approved.
- **US-018-AC3** — *Given* a claim at my step, *when* I reject it without a comment, *then* the rejection is refused until a comment is entered.

## US-019 — Send a claim back for clarification

| Field | Detail |
|---|---|
| Story ID | US-019 |
| Epic | Approvals |
| Actor | Any approver |
| Description | As an approver, I want to send a claim back for clarification, so that I can ask a question instead of rejecting. |
| Business value | Avoids needless rejections and resubmissions. |
| Related requirements | FR-007 |
| Priority | Must |
| Note | Team version is for line managers only; FR-007 covers all approvers. Comment mandatory? (OQ-034) |

**Acceptance criteria**

- **US-019-AC1** — *Given* a claim at my step, *when* I send it back with a comment, *then* it returns to the employee as Sent Back with my comment.
- **US-019-AC2** — *Given* a claim already actioned by someone else, *when* I send it back, *then* the action is refused as out of date.

## US-020 — Comment on a claim

| Field | Detail |
|---|---|
| Story ID | US-020 |
| Epic | Approvals |
| Actor | Any approver |
| Description | As an approver, I want to comment on a claim, so that the discussion stays with the claim. |
| Business value | Keeps context in one place for later audit. |
| Related requirements | FR-006, FR-018 |
| Priority | Must |

**Acceptance criteria**

- **US-020-AC1** — *Given* a claim I can open, *when* I add a comment, *then* it appears in the claim history with author and time.

## US-021 — Approve every claim above 25,000 INR after the manager

| Field | Detail |
|---|---|
| Story ID | US-021 |
| Epic | Approvals |
| Actor | Finance auditor |
| Description | As a finance auditor, I want to approve every claim above 25,000 INR after the manager, so that large claims get a finance check. |
| Business value | Second line of control on large claims. |
| Related requirements | FR-005, BR-013, BR-014 |
| Priority | Must |

**Acceptance criteria**

- **US-021-AC1** — *Given* a claim total of exactly 25,000.00 INR, *when* the manager approves, *then* it is Approved with no auditor step.
- **US-021-AC2** — *Given* a claim total of 25,000.01 INR, *when* the manager approves, *then* it moves to the finance auditor.

## US-022 — Approve every claim above 1,00,000 INR last

| Field | Detail |
|---|---|
| Story ID | US-022 |
| Epic | Approvals |
| Actor | Finance head |
| Description | As a finance head, I want to approve every claim above 1,00,000 INR last, so that the largest claims get my sign-off. |
| Business value | Senior control on the largest claims. |
| Related requirements | FR-005, BR-013, BR-014 |
| Priority | Must |

**Acceptance criteria**

- **US-022-AC1** — *Given* a claim total of exactly 1,00,000.00 INR, *when* the auditor approves, *then* it is Approved.
- **US-022-AC2** — *Given* a claim total of 1,00,000.01 INR, *when* the auditor approves, *then* it moves to the finance head.

## US-023 — Delegate my approvals while I'm on leave

| Field | Detail |
|---|---|
| Story ID | US-023 |
| Epic | Approvals |
| Actor | Any approver |
| Description | As an approver, I want to delegate my approvals while I'm on leave, so that claims don't wait for me. |
| Business value | Approvals keep moving during absences. |
| Related requirements | FR-008, BR-020, BR-021, BR-017 |
| Priority | Must |
| Note | Team version does not restrict the delegate's role (CL-13 does). Leave dates per ASM-007. |

**Acceptance criteria**

- **US-023-AC1** — *Given* an approver, *when* they choose a delegate who is a finance auditor or finance head, with leave dates, *then* the delegation is saved.
- **US-023-AC2** — *Given* a delegate of another role, *when* it is chosen, *then* the delegation is refused.
- **US-023-AC3** — *Given* an active delegation, *when* a claim reaches the approver's step, *then* the delegate can act and the audit records both the delegate and the original approver.
- **US-023-AC4** — *Given* delegation dates, *when* tests run, *then* inside, outside and boundary dates behave correctly.

## US-024 — Nobody to approve their own claim

| Field | Detail |
|---|---|
| Story ID | US-024 |
| Epic | Approvals |
| Actor | Finance auditor |
| Description | As a finance auditor, I want nobody to approve their own claim, so that approvals cannot be bypassed. |
| Business value | Closes the most obvious approval bypass. |
| Related requirements | BR-017, BR-016, BR-015 |
| Priority | Must |
| Note | Team version also flags finance head claims — not documented (OQ-040). |

**Acceptance criteria**

- **US-024-AC1** — *Given* a claim whose step approver (or delegate) is the claimant, *when* the chain is built, *then* that person cannot approve it (handling per ASM-009).
- **US-024-AC2** — *Given* a finance head's own claim, *when* it is routed, *then* it goes to a finance auditor whatever the amount.
- **US-024-AC3** — *Given* a claimant without a manager, *when* the claim is routed, *then* it moves to the next level.

## US-025 — See each receipt beside its line while I review

| Field | Detail |
|---|---|
| Story ID | US-025 |
| Epic | Approvals |
| Actor | Line manager |
| Description | As a line manager, I want to see each receipt beside its line while I review, so that I can verify quickly. |
| Business value | Faster, more accurate approval decisions. |
| Related requirements | NFR-002 |
| Priority | Must |

**Acceptance criteria**

- **US-025-AC1** — *Given* a claim under review, *when* I open it, *then* lines and receipts are shown side by side.
- **US-025-AC2** — *Given* PDF and image receipts, *when* they are opened, *then* both display.

## US-026 — Filter my review queue

| Field | Detail |
|---|---|
| Story ID | US-026 |
| Epic | Audit queue |
| Actor | Finance auditor |
| Description | As a finance auditor, I want to filter my review queue, so that I can focus on the riskiest claims. |
| Business value | Puts auditor time where risk is highest. |
| Related requirements | FR-009, NFR-003 |
| Priority | Must |
| Note | Exact filters open (OQ-024); team proposes department, category, amount, date and flag type. |

**Acceptance criteria**

- **US-026-AC1** — *Given* my review queue, *when* I apply filters, *then* only matching claims are shown.
- **US-026-AC2** — *Given* my review queue, *when* it loads, *then* it includes claims at the auditor step and claims held for sample audit.

## US-027 — Approve several claims at once

| Field | Detail |
|---|---|
| Story ID | US-027 |
| Epic | Audit queue |
| Actor | Finance auditor |
| Description | As a finance auditor, I want to approve several claims at once, so that clean claims clear quickly. |
| Business value | Reduces queue clearing time. |
| Related requirements | FR-010, BR-017, NFR-020 |
| Priority | Must |

**Acceptance criteria**

- **US-027-AC1** — *Given* several selected claims at my step, *when* I bulk approve, *then* each is approved individually with its own audit entry.
- **US-027-AC2** — *Given* a selection including claims not at my step or my own claims, *when* I bulk approve, *then* those are skipped and listed; the rest are approved.

## US-028 — A random 10% of auto-approved claims sampled

| Field | Detail |
|---|---|
| Story ID | US-028 |
| Epic | Audit queue |
| Actor | Finance auditor |
| Description | As a finance auditor, I want a random 10% of auto-approved claims sampled, so that auto-approval stays honest. |
| Business value | Deters abuse of auto-approval. |
| Related requirements | FR-011, FR-024, BR-022, BR-023 |
| Priority | Must |

**Acceptance criteria**

- **US-028-AC1** — *Given* auto-approved claims, *when* selection runs, *then* about 10% are flagged for sample audit (method per OQ-035).
- **US-028-AC2** — *Given* a sampled claim, *when* payout is prepared, *then* it cannot be added to a batch until reviewed.
- **US-028-AC3** — *Given* a sampled claim, *when* the auditor approves or rejects it with a comment, *then* it becomes eligible for payout or Rejected.

## US-029 — Put approved claims into a payout batch

| Field | Detail |
|---|---|
| Story ID | US-029 |
| Epic | Payout |
| Actor | Finance head |
| Description | As a finance head, I want to put approved claims into a payout batch, so that I can pay everyone in one run. |
| Business value | One controlled payment run instead of ad-hoc transfers. |
| Related requirements | FR-012, BR-024, BR-026 |
| Priority | Must |

**Acceptance criteria**

- **US-029-AC1** — *Given* approved claims not held for sample audit, *when* I create a batch, *then* I can add them.
- **US-029-AC2** — *Given* a held, unapproved or already batched claim, *when* I try to add it, *then* it is refused.
- **US-029-AC3** — *Given* a user without the finance head or admin role, *when* they try to create a batch, *then* access is refused.

## US-030 — Export a batch as a bank-file CSV

| Field | Detail |
|---|---|
| Story ID | US-030 |
| Epic | Payout |
| Actor | Finance head |
| Description | As a finance head, I want to export a batch as a bank-file CSV, so that the bank can process the payments. |
| Business value | Produces the payment file without manual Excel work. |
| Related requirements | FR-013, FR-014, BR-025, BR-031, NFR-019, FR-030, NFR-021 |
| Priority | Must |
| Note | Team version uses a 9-column format; CL-11 defines 13 columns (OQ-036). |

**Acceptance criteria**

- **US-030-AC1** — *Given* a batch, *when* I export it, *then* a CSV in the agreed format is produced with a control summary (rows, total, file hash).
- **US-030-AC2** — *Given* an exported batch, *when* export completes, *then* every claim in it moves to Paid and the batch is locked.
- **US-030-AC3** — *Given* a value starting with =, +, - or @, *when* it is written to the CSV, *then* it is neutralised against formula injection.

## US-031 — See spend by category, department and month

| Field | Detail |
|---|---|
| Story ID | US-031 |
| Epic | Dashboard |
| Actor | Finance head |
| Description | As a finance head, I want to see spend by category, department and month, so that I know where the money goes. |
| Business value | Visibility of spend patterns. |
| Related requirements | FR-015 |
| Priority | Must |
| Note | Who may view the dashboard per ASM-010. |

**Acceptance criteria**

- **US-031-AC1** — *Given* claim data, *when* I open the dashboard, *then* spend by category, by department and by month is shown.
- **US-031-AC2** — *Given* the dashboard, *when* I filter by period, *then* the figures update.

## US-032 — See policy violations and average approval time

| Field | Detail |
|---|---|
| Story ID | US-032 |
| Epic | Dashboard |
| Actor | Finance head |
| Description | As a finance head, I want to see policy violations and average approval time, so that I can spot problems and slow approvals. |
| Business value | Shows whether the new process fixes the original problems. |
| Related requirements | FR-016, FR-017 |
| Priority | Must |

**Acceptance criteria**

- **US-032-AC1** — *Given* evaluated claims, *when* I open the dashboard, *then* violations are counted by rule.
- **US-032-AC2** — *Given* approved claims, *when* I open the dashboard, *then* average approval time is shown (definition per OQ-023).

## US-033 — Every action recorded permanently

| Field | Detail |
|---|---|
| Story ID | US-033 |
| Epic | Audit |
| Actor | Finance auditor |
| Description | As a finance auditor, I want every action recorded permanently, so that I can prove who did what and when. |
| Business value | Audit evidence that cannot be tampered with. |
| Related requirements | FR-018, BR-029, NFR-004 |
| Priority | Must |

**Acceptance criteria**

- **US-033-AC1** — *Given* any action (state change, comment, delegation, configuration change), *when* it happens, *then* an audit entry is written in the same transaction.
- **US-033-AC2** — *Given* an audit entry, *when* anyone tries to update or delete it, *then* it is refused by the application and the database.

## US-034 — Realistic synthetic data seeded

| Field | Detail |
|---|---|
| Story ID | US-034 |
| Epic | Data |
| Actor | Team |
| Description | As a team member, I want realistic synthetic data seeded, so that the demo and dashboard have something to show. |
| Business value | Credible demo without real data. |
| Related requirements | FR-027, FR-028, FR-029, FR-030, NFR-012 |
| Priority | Must |

**Acceptance criteria**

- **US-034-AC1** — *Given* an empty database, *when* the seed runs, *then* 150 employees in 8 departments with a reporting hierarchy, auditors and a finance head exist.
- **US-034-AC2** — *Given* the seed, *when* it runs, *then* 3,000 historical claims including planted duplicates and limit breaches exist, and each employee has synthetic bank details.
- **US-034-AC3** — *Given* the seed, *when* it is run twice, *then* the result is the same (fixed random seed).

## US-035 — Metrics for queue size, approval age and page latency

| Field | Detail |
|---|---|
| Story ID | US-035 |
| Epic | Ops |
| Actor | Support engineer |
| Description | As a support engineer, I want metrics for queue size, approval age and page latency, so that I can spot backlogs. |
| Business value | Early warning before employees complain. |
| Related requirements | NFR-008 |
| Priority | Must |

**Acceptance criteria**

- **US-035-AC1** — *Given* the running backend, *when* Prometheus scrapes it, *then* queue size, approval age and latency metrics are available.

## US-036 — A dashboard and an alert on the oldest pending approval

| Field | Detail |
|---|---|
| Story ID | US-036 |
| Epic | Ops |
| Actor | Support engineer |
| Description | As a support engineer, I want a dashboard and an alert on the oldest pending approval, so that stuck claims get noticed. |
| Business value | Stuck approvals are acted on. |
| Related requirements | NFR-008 |
| Priority | Must |
| Note | Alert threshold to be agreed by the team (OQ-042). |

**Acceptance criteria**

- **US-036-AC1** — *Given* collected metrics, *when* I open Grafana, *then* a dashboard shows the three metrics.
- **US-036-AC2** — *Given* an approval older than the agreed threshold, *when* the alert rule evaluates, *then* the alert fires.

## US-037 — A runbook

| Field | Detail |
|---|---|
| Story ID | US-037 |
| Epic | Ops |
| Actor | Support engineer |
| Description | As a support engineer, I want a runbook, so that I know what to do when the alert fires. |
| Business value | Faster recovery. |
| Related requirements | NFR-010 |
| Priority | Must |

**Acceptance criteria**

- **US-037-AC1** — *Given* the runbook, *when* the alert fires, *then* it describes the checks and recovery steps.

## US-038 — CI with tests and a dependency check

| Field | Detail |
|---|---|
| Story ID | US-038 |
| Epic | Ops |
| Actor | Team |
| Description | As a team member, I want CI with tests and a dependency check, so that broken or vulnerable code is caught. |
| Business value | Quality gate on every change. |
| Related requirements | NFR-007, NFR-015 |
| Priority | Must |

**Acceptance criteria**

- **US-038-AC1** — *Given* a push or pull request, *when* CI runs, *then* backend tests, frontend lint/build, API tests and a dependency vulnerability check run.

## US-039 — Start a seeded demo with one command

| Field | Detail |
|---|---|
| Story ID | US-039 |
| Epic | Ops |
| Actor | Team |
| Description | As a team member, I want to start a seeded demo with one command, so that anyone can run the demo. |
| Business value | Reliable demos on any laptop. |
| Related requirements | NFR-006 |
| Priority | Must |

**Acceptance criteria**

- **US-039-AC1** — *Given* a clean clone, *when* docker compose up is run, *then* all services start with seeded data.

## US-040 — Be notified when my claim changes status

| Field | Detail |
|---|---|
| Story ID | US-040 |
| Epic | Notifications |
| Actor | Employee |
| Description | As an employee, I want to be notified when my claim changes status, so that I don't need to keep checking. |
| Business value | Convenience. |
| Related requirements | — |
| Priority | Could |
| Note | Not in requirements.docx — needs owner approval before any work (OQ-044). |

**Acceptance criteria**

- **US-040-AC1** — *Given* a claim, *when* its status changes, *then* the owner sees an in-app notification.

## US-041 — Edit policy rules on a screen

| Field | Detail |
|---|---|
| Story ID | US-041 |
| Epic | Policy |
| Actor | Finance head |
| Description | As a finance head, I want to edit policy rules on a screen, so that I don't need database access. |
| Business value | Self-service policy changes. |
| Related requirements | FR-026, BR-027 |
| Priority | Should |
| Note | Team marks it Stretch; whether a screen is required is open (OQ-043). |

**Acceptance criteria**

- **US-041-AC1** — *Given* a finance head or admin, *when* they edit a limit and save, *then* the change applies to new submissions and is audited.
- **US-041-AC2** — *Given* another role, *when* they open the screen, *then* access is refused.

## US-042 — Amount and vendor read from my receipt

| Field | Detail |
|---|---|
| Story ID | US-042 |
| Epic | Claims |
| Actor | Employee |
| Description | As an employee, I want amount and vendor read from my receipt, so that I type less. |
| Business value | Convenience. |
| Related requirements | — |
| Priority | Could |
| Note | Not in requirements.docx — needs owner approval (OQ-044). |

**Acceptance criteria**

- **US-042-AC1** — *Given* an uploaded receipt, *when* it is processed, *then* amount and vendor are pre-filled and editable.

## US-043 — A repeated date and receipt number caught

| Field | Detail |
|---|---|
| Story ID | US-043 |
| Epic | Policy |
| Actor | Finance auditor |
| Description | As a finance auditor, I want a repeated date and receipt number caught, so that the same bill cannot be claimed again. |
| Business value | Closes a duplicate path the file hash alone misses. |
| Related requirements | BR-009, FR-021 |
| Priority | Must |
| Note | New — no team story covered CL-05. |

**Acceptance criteria**

- **US-043-AC1** — *Given* a line with the same expense date and receipt number as an earlier line, *when* the claim is submitted, *then* the line is flagged as a duplicate, naming the earlier claim.
- **US-043-AC2** — *Given* the rule, *when* unit tests run, *then* match and non-match cases pass.

## US-044 — View the audit trail

| Field | Detail |
|---|---|
| Story ID | US-044 |
| Epic | Audit |
| Actor | Finance head |
| Description | As a finance head, I want to view the audit trail, so that I can investigate any claim's history. |
| Business value | Makes the audit trail usable, not just stored. |
| Related requirements | FR-018, BR-028 |
| Priority | Should |
| Note | New. Viewing rights per ASM-010. |

**Acceptance criteria**

- **US-044-AC1** — *Given* a finance head, *when* they open the audit trail, *then* all entries can be filtered by claim, actor, action and date.
- **US-044-AC2** — *Given* any other user, *when* they open a claim they can see, *then* its history is shown, and nothing else.

## US-045 — Rule-by-rule, threshold, delegation and 10 end-to-end tests with a defect log

| Field | Detail |
|---|---|
| Story ID | US-045 |
| Epic | Quality |
| Actor | Team |
| Description | As a team member, I want rule-by-rule, threshold, delegation and 10 end-to-end tests with a defect log, so that the app is proven to work. |
| Business value | Evidence for judges and a safety net for changes. |
| Related requirements | NFR-015 |
| Priority | Must |
| Note | New — Testing phase deliverable. |

**Acceptance criteria**

- **US-045-AC1** — *Given* the policy engine, *when* unit tests run, *then* every policy rule has its own tests.
- **US-045-AC2** — *Given* approval thresholds and delegation, *when* tests run, *then* boundaries and delegation windows are covered.
- **US-045-AC3** — *Given* the test plan, *when* it is reviewed, *then* 10 written end-to-end cases and a defect log exist.

## US-046 — A basic security checklist run against the code

| Field | Detail |
|---|---|
| Story ID | US-046 |
| Epic | Quality |
| Actor | Team |
| Description | As a team member, I want a basic security checklist run against the code, so that real vulnerabilities are fixed. |
| Business value | Reduces the risk of approval bypass and data leaks. |
| Related requirements | NFR-009, NFR-016, NFR-017 |
| Priority | Must |
| Note | New — Testing and Deployment phase deliverable. |

**Acceptance criteria**

- **US-046-AC1** — *Given* the codebase, *when* the checklist is run, *then* each finding is recorded as fixed or a false alarm with a reason.
- **US-046-AC2** — *Given* the repository, *when* it is scanned, *then* no secrets are committed.
