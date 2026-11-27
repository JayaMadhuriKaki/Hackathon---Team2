# ExpenseFlow — Frontend Specification

| Field | Value |
|---|---|
| Status | Draft v0.2 — for team review before any code |
| Date | 2026-11-27 |
| Parent spec | `architecture/SPEC.md` **v0.2** (roles, capabilities, claim states, step statuses, API-001..API-038, conventions, error codes, SPEC-Q-02..15, ASM-S-01..06) |
| Sources | `CLAUDE.md` §2 (FR/POL/NFR), §2.6 (CL-01..CL-18), §2.7 routing, §2.8 payout, §4 domain, §6 (Q-##, ASM-###), §8 stack, §10 frontend, §12 API, §13 security, §16 testing; current code in `frontend/` |
| Stack | React 19, Vite, **JavaScript** (kept — base is `.jsx`), `react-router-dom` 7. No new runtime dependency without ADR-000 |
| Changes in v0.2 | Aligned to backbone v0.2: capabilities from API-003 drive nav and `RoleGate`; claim visibility rule; API-037 client config; API-038 delegate picker; API-006 `allowedActions`; queue split API-014 / API-022; bulk-approve per-claim results; list/money/idempotency conventions; payout status `CREATED`/`EXPORTED`; line-item field rules; new error codes. FE-I-01..13 resolved except as noted in §12 |

**Rule:** this spec adds no screen, field, behaviour or endpoint beyond `CLAUDE.md` §2, answered §6 items, logged ASM assumptions and the backbone. Undecided items point to `SPEC-Q-##`, `Q-##`, `ASM-###` / `ASM-S-##` or an open issue `FE-I-##` (§12). Names (roles, capabilities, states, API IDs, error codes) are the backbone's, exactly.

---

## 1. Requirement coverage

| Requirement | Screen / component |
|---|---|
| FR-001, CL-05, CL-08, ASM-005 | S-03 ClaimEditorPage, `LineItemForm`, `ReceiptUpload` |
| FR-002 | S-03 "Save draft" |
| FR-003, ASM-003, CL-12 | S-03 / S-04 "Submit" |
| FR-004, CL-07, ASM-004 | S-04 / S-06 `PolicyResultList` |
| FR-005, CL-03, CL-09, CL-16 | S-04 `ApprovalSteps`, S-05 ApprovalsQueuePage, S-06 ApproverReviewPage |
| FR-006 | S-06 comment box (API-018) |
| FR-007, SPEC-Q-03 | S-06 "Send back" `ConfirmDialog` |
| FR-008, CL-13, ASM-007 | S-08 DelegationsPage (API-038 picker) |
| FR-009, FR-010, SPEC-Q-09 | S-07 ReviewQueuePage (filters + bulk approve) |
| FR-011, CL-14 | S-07 `queue=SAMPLE_AUDIT`, S-06 in review mode |
| FR-012–FR-014, CL-06, CL-11 | S-09 PayoutBatchesPage, S-10 PayoutBatchDetailPage |
| FR-015–FR-017, ASM-010, SPEC-Q-11 | S-11 DashboardPage |
| FR-018, NFR-004 | S-12 AuditTrailPage, `AuditHistory` on S-04/S-06 |
| FR-019, CL-15, NFR-005 | S-02 (own claims), claim visibility rule → 404 handling, `RoleGate` |
| FR-020, CL-04 | S-06 "Reject" with mandatory comment |
| CL-10, B-08 | S-13 PolicyRulesPage |
| NFR-001 | S-03 phone layout (§7) |
| NFR-002 | S-06 `ReceiptViewer` side by side |
| NFR-003 | `DataTable` (S-02, S-05, S-07, S-09, S-10, S-12, S-13) |
| §10.3 | API-037 client config + backbone §4.4 rules |
| §10.5 | `useMutation` + `Idempotency-Key` (§6.4) |
| ASM-002 | `DateDisplay` (IST) |
| §4.3 | `MoneyDisplay` (₹1,00,000.00), money as string |

## 2. Route map

Access uses the role (`user.role`) and the capabilities from API-003: `isManager`, `isApprover`, `canDelegate`, `isAdmin` (backbone §2). The UI never derives capabilities itself. Every route except `/login` needs a session.

| Path | Screen | Allowed (UI gate) | API IDs | Requirements |
|---|---|---|---|---|
| `/login` | S-01 LoginPage | Public | API-002, API-003 | Q-22, SPEC-Q-07 |
| `/` | redirect → `/claims` | Any | — | — |
| `/claims` | S-02 MyClaimsPage | Any | API-004 | FR-019, CL-15 |
| `/claims/new` | S-03 ClaimEditorPage (create) | Any | API-005, 007–010, 012, 037 | FR-001–003 |
| `/claims/:claimId/edit` | S-03 ClaimEditorPage (edit) | Claimant; `allowedActions` contains edit (`DRAFT`/`SENT_BACK`) | API-006, 007–010, 012, 037 | FR-001–003, CL-12, B-04 |
| `/claims/:claimId` | S-04 ClaimDetailPage | Claim visibility rule (server) | API-006, 011, 012, 013 | FR-004, FR-018 |
| `/approvals` | S-05 ApprovalsQueuePage | `isApprover` | API-014 | FR-005, FR-008 |
| `/approvals/:claimId` | S-06 ApproverReviewPage | Claim visibility rule; actions from `allowedActions` | API-006, 011, 013, 015–018 | FR-005–007, FR-020, NFR-002 |
| `/review-queue` | S-07 ReviewQueuePage | role `FINANCE_AUDITOR` | API-022, 023, 036 | FR-009–011, CL-14 |
| `/review-queue/:claimId` | S-06 in review mode | role `FINANCE_AUDITOR` | API-006, 011, 013, 015–018 | FR-005, CL-14 |
| `/delegations` | S-08 DelegationsPage | `canDelegate` | API-019, 020, 021, 038 | FR-008, CL-13 |
| `/payout-batches` | S-09 PayoutBatchesPage | `isAdmin` | API-024, 025, 026 | FR-012 |
| `/payout-batches/:batchId` | S-10 PayoutBatchDetailPage | `isAdmin` | API-027, 028, 029 | FR-013, FR-014 |
| `/dashboard` | S-11 DashboardPage | role `FINANCE_AUDITOR` or `isAdmin` | API-030, 031, 032, 036 | FR-015–017, ASM-010 |
| `/audit-trail` | S-12 AuditTrailPage | `isAdmin` | API-033 | FR-018, ASM-010 |
| `/policy-rules` | S-13 PolicyRulesPage | `isAdmin` | API-034, 035 | CL-10 |
| `*` | NotFoundPage | Any | — | — |

- **Nav items** follow the same gates. `ADMIN` (no seeded user, ASM-S-01) sees no Approvals/Review links because `ADMIN` never approves.
- API-001 is not used by a required screen. Hiding a route or button is UX only; the backend enforces every permission (§10.9).
- **Finance head / admin do not see claims by role** (backbone §7): payout and audit screens show claim data from API-027/API-033 and do **not** link to `/claims/:claimId` unless the user can otherwise see it; a 404 there is expected.

## 3. Shared screen rules (apply to every screen)

| Rule | Detail |
|---|---|
| States | Every data screen renders `LoadingState`, `EmptyState`, `ErrorState` or content. A successful mutation shows a `Toast` (role `status`) and refreshes data |
| Duplicate submit | Action button disabled + spinner while in flight; idempotent calls (API-012, 015, 016, 017, 023, 026, 028) send `Idempotency-Key` (§6.4) |
| Errors | Mapped by `code` (§3.1). `traceId` shown as "Reference: …" with a copy button. Never raw bodies or stack traces |
| 401 | Clear session, go to `/login?next=<path>` (same-origin relative path only — no open redirect, §13.3). JWT lifetime per SPEC-Q-13 |
| 404 on a claim | "Not found or you do not have access" — same text for both cases (CL-15, B-10) |
| Lists | Server-side paging and sorting: `page` (1-based), `pageSize` (default 20, max 100), `sort` (`field` / `-field`); response `{items, page, pageSize, total}`. Page, size, sort and filters live in the URL query |
| Money | API sends strings with 2 decimals (`"27450.00"`). `MoneyDisplay` only; client sums in integer paise parsed from strings, never floats (§4.3) |
| Time | `DateDisplay`: timestamps UTC → IST; `YYYY-MM-DD` dates shown as-is (no TZ shift). "Today" for validation = current IST date |
| Claim identity | Show `claimNumber` (`CLM-YYYY-NNNNN`); route param is the claim `id` |
| Actions | Claim actions shown only if in API-006 `allowedActions` (FE-I-14 for value names) |
| Page title | `document.title = "<Screen> · ExpenseFlow"`; focus moves to `<h1>` on route change |

### 3.1 Error code → message

| HTTP / `code` | Message shown | Where |
|---|---|---|
| 400 `VALIDATION_FAILED` | Field errors from `details.fields` on each `FormField`; summary "Please fix the highlighted fields." | Inline + `ErrorSummary` |
| 401 `UNAUTHENTICATED` | Login: "Email or password is incorrect." Elsewhere: "Your session has ended. Please sign in again." | Login form / redirect |
| 403 `FORBIDDEN` | "You do not have permission to do this." | `ForbiddenState` / toast |
| 403 `NOT_CURRENT_APPROVER` | "This claim is not waiting for your decision any more." → reload claim | Toast + reload |
| 404 `NOT_FOUND` | "Not found or you do not have access." | `ErrorState` |
| 405 `METHOD_NOT_ALLOWED` | Generic "Something went wrong." (client bug; log traceId) | Toast |
| 409 `STATE_CONFLICT` | "This claim has changed since you opened it (now {status}). The page has been refreshed." → refetch. On API-029: "Export the batch before downloading." | Toast + reload |
| 413 `FILE_TOO_LARGE` | "The file is too large. Maximum size is {limit}." (SPEC-Q-06) | `ReceiptUpload` |
| 415 `UNSUPPORTED_FILE_TYPE` | "This file type is not accepted. Use {types}." (SPEC-Q-06) | `ReceiptUpload` |
| 422 `RECEIPT_REQUIRED` | "A receipt is required for lines above {receiptMandatoryAbove}." on each listed line | Line rows |
| 422 `CLAIM_HAS_NO_LINES` | "Add at least one expense line before submitting." | Editor |
| 422 `COMMENT_REQUIRED` | "Please add a comment." | Dialog comment field |
| 422 `SELF_APPROVAL_FORBIDDEN` | "You cannot act on your own claim." | Toast |
| 422 `INVALID_DELEGATE` | "Choose a finance auditor or finance head other than yourself." (CL-13) | Delegate field |
| 422 `DELEGATION_OVERLAP` | "You already have a delegation for these dates. Cancel it first or pick other dates." | Delegation form |
| 422 `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` | "One or more claims are no longer eligible for payout. The list has been refreshed." | Toast + refetch |
| 422 `IDEMPOTENCY_KEY_REUSED` | "Please try again." — client discards the key and generates a new one (should not occur, §6.4) | Toast |
| 422 `BUSINESS_RULE_VIOLATION` | Server `message`, else "This action breaks a business rule." | Toast |
| 429 `RATE_LIMITED` | "Too many attempts. Please wait a moment and try again." | Inline |
| 500 `INTERNAL_ERROR` / network / non-JSON body | "Something went wrong. Please try again." + traceId (body or `X-Trace-Id` header) | `ErrorState` / toast |

## 4. Screen specifications

### S-01 LoginPage
- **Purpose:** sign in (SPEC-Q-07 default: seeded users, email + password, JWT).
- **Layout:** single centred card, full width on phone.
- **Fields:** Email (required, `type=email`, `autocomplete=username`), Password (required, `autocomplete=current-password`).
- **Actions:** Sign in → API-002, then API-003 (user, role, capabilities) → redirect to `next` or `/claims`.
- **States:** button disabled in flight; 401 / 429 inline (§3.1). No "forgot password" or sign-up (not documented).

### S-02 MyClaimsPage
- **Purpose:** list the caller's own claims (FR-019, CL-15). Every user sees this.
- **Data:** API-004 (paged, §3).
- **Desktop:** `DataTable` — Claim number, Submitted (`DateDisplay`), Lines, Total (`MoneyDisplay`, right-aligned), Status (`StatusBadge`). Sortable per contract. Row click → S-04.
- **Phone (< 640 px):** each row is a card (claim number, status badge, total, date).
- **Actions:** "New claim" → `/claims/new`. Row action "Edit" only for `DRAFT`/`SENT_BACK`.
- **Empty:** "You have no claims yet." + "New claim" button.

### S-03 ClaimEditorPage (mobile-first, NFR-001)
- **Purpose:** create/edit a claim with line items; save as draft (FR-002); submit (FR-003).
- **Data:** API-037 client config (`categories`, `receiptMandatoryAbove`, `tier1Cities`), loaded once and cached for the session.
- **Create flow:** first "Save draft" or first line add calls API-005 (creates `DRAFT`, returns `claimNumber`), then the URL replaces to `/claims/:claimId/edit`.
- **Edit guard:** load API-006; if the edit action is not in `allowedActions` → redirect to S-04 (B-04).
- **SENT_BACK banner:** the latest send-back comment from API-006 comments, plus "Resubmitting restarts approval from the first step" (CL-12).
- **Layout desktop (≥ 1024 px):** two columns — line list (left), active `LineItemForm` (right). **Phone:** single column; lines as stacked cards; "Add line" opens the form full-screen; sticky bottom bar with running total + Save draft + Submit; inputs ≥ 44 px tall; native date pickers; camera capture via `<input type=file capture>` (`accept` per SPEC-Q-06).
- **Line fields** (`LineItemForm`; rules = backbone §4.4, backend authoritative):

| Field (JSON) | Control | Client validation | Source |
|---|---|---|---|
| `category` | select | Required; options from API-037 `categories` (`MEALS`, `HOTEL`, `LOCAL_CAB`), labels Meals / Hotel / Local cab | FR-001, SPEC-Q-02 |
| `amount` | `inputmode=decimal` | Required; > 0; max 2 decimals; ≤ 9,999,999,999.99 (`NUMERIC(12,2)`); sent as string | FR-001, §10.3 |
| `expenseDate` | date | Required; not after today (IST). For `HOTEL`: hidden and set equal to `checkInDate` (ASM-S-03, proposed) | FR-001 |
| `vendor` | text | Required; 1–120 chars after trim | POL-006 |
| `receiptNumber` | text | Required; 1–60 chars after trim (SPEC-Q-15 default: every line) | CL-05, POL-009 |
| `city` | text + `<datalist>` of `tier1Cities` | `HOTEL` only; required; free text allowed (other cities valid) | CL-08, ASM-006 |
| `checkInDate` | date | `HOTEL` only; required | ASM-005 |
| `checkOutDate` | date | `HOTEL` only; required; > `checkInDate`. Shows "Nights: n" read-only | ASM-005 |
| receipt file | `ReceiptUpload` | Optional on draft (SPEC-Q-10); required to submit when amount > `receiptMandatoryAbove` (ASM-003); type/size per SPEC-Q-06 | FR-001, POL-005 |

- Switching category away from `HOTEL` clears hotel fields. The UI does not show or check spending limits — policy results come only from the server.
- **Actions:** Add/save line → API-007 / API-008; delete line → `ConfirmDialog` → API-009; upload receipt → API-010 (multipart, progress, file name, replace); Save draft (persists pending line edits); **Submit** → `ConfirmDialog` ("Submit claim of ₹X? Lines cannot be edited after submit.") → API-012 with `Idempotency-Key`.
- **Submit pre-check (UX only):** ≥ 1 line; all required fields; lines above `receiptMandatoryAbove` without a receipt are highlighted (exactly equal is allowed).
- **After submit:** navigate to S-04 showing the resulting status (`AUTO_REJECTED`, `PENDING_*`, `SAMPLE_AUDIT_PENDING`, `APPROVED`) and policy results. The UI never predicts the outcome.
- **Errors:** `RECEIPT_REQUIRED` → mark lines from `details`; `CLAIM_HAS_NO_LINES`; 400 → field errors per line; 409 → reload.
- **Unsaved changes:** warn on navigation away while a line form is dirty.

### S-04 ClaimDetailPage
- **Purpose:** view claim status, lines, policy results, steps, comments and history.
- **Data:** API-006 (backbone §7.1: header, lines with policy results and receipt metadata, approval steps, comments, `allowedActions`), API-013 (history), API-011 (receipt on demand).
- **Layout:** header (claim number, `StatusBadge`, total, claimant + department, submitted); lines `DataTable` with per-line `PolicyResultList`; `ApprovalSteps` (level, step type, approver or pool, `step_status` incl. `SKIPPED` with reason, decision, `onBehalfOf`; `SUPERSEDED` steps collapsed under "Earlier submission"); comments; `AuditHistory`. Phone: sections stacked; lines as cards; receipt full-screen `ReceiptViewer`.
- **Policy results:** rule ID, reason, configured limit, actual value (`MoneyDisplay`; for duplicates the matching claim number and line). `AUTO_REJECTED` shows a banner listing every breaching line (CL-07). Duplicate and weekend flags shown as warnings (ASM-004, POL-008).
- **Actions:** only those in `allowedActions` (claimant: edit, submit in `DRAFT`/`SENT_BACK`). No withdraw (SPEC-Q-10). If the caller can decide on the claim, a "Review" button opens S-06.

### S-05 ApprovalsQueuePage
- **Purpose:** claims waiting for the caller via API-014 — manager steps assigned to them or to a delegator they cover, and `PENDING_FINANCE_HEAD` claims for finance heads (backbone §7.2). Never the caller's own claims. Auditor-step work is in S-07.
- **Desktop `DataTable`:** Claim number, Claimant, Department, Step (`PENDING_MANAGER` / `PENDING_FINANCE_HEAD` via `StatusBadge`), Total, Flags (duplicate/weekend, icon + text), Submitted, Waiting since, On behalf of (delegated items). Default sort: oldest waiting first. Row click → S-06.
- **Phone:** cards. **Empty:** "Nothing is waiting for your approval."
- No bulk action here (bulk approve is only documented for the review queue, FR-010).

### S-06 ApproverReviewPage (side by side, NFR-002)
- **Purpose:** decide on one claim quickly (stopwatch-tested flow, §2.4).
- **Data:** API-006, API-013, API-011 per line.
- **Desktop (≥ 1024 px):** split pane — left 55 %: claim header, lines table (selected row highlighted), `PolicyResultList`, comments, history; right 45 %: sticky `ReceiptViewer` for the selected line. Selecting a line (click or ↑/↓) swaps the receipt. **Tablet/phone:** receipt in a collapsible panel above the lines; action bar sticky at the bottom.
- **Actions** (rendered only when present in `allowedActions`):

| Action | API | Dialog | Comment | Idempotency |
|---|---|---|---|---|
| Approve | API-015 | `ConfirmDialog` | Optional | Yes |
| Reject | API-016 | `ConfirmDialog` | **Mandatory**, trimmed non-empty (CL-04, CL-14) | Yes |
| Send back | API-017 (`PENDING_*` only; never for `SAMPLE_AUDIT_PENDING`) | `ConfirmDialog` | Mandatory (SPEC-Q-03 default) | Yes |
| Comment | API-018 | inline textarea | Required, non-empty | No |

- **Own claim:** never in the queues; if opened directly, `allowedActions` is empty and the page shows "You cannot act on your own claim" (B-02).
- **Delegated:** banner "Acting on behalf of {delegator}".
- **After decision:** toast; return to the originating queue (S-05 or S-07), next item focused. 409 / `NOT_CURRENT_APPROVER` → message + reload.

### S-07 ReviewQueuePage (finance auditor, FR-009/010/011)
- **Data:** API-022 (paged) — `PENDING_AUDITOR` and `SAMPLE_AUDIT_PENDING` claims, never the caller's own; API-036 for departments; API-037 for categories.
- **Filters** (backbone §7.2, proposed under SPEC-Q-09): `departmentId`, `category`, `minAmount`, `maxAmount`, `fromDate`, `toDate`, `flag` (`DUPLICATE`, `WEEKEND`), `queue` (`AUDITOR_STEP`, `SAMPLE_AUDIT`) — shown as tabs "Auditor step" / "Sample audit" / "All". Client checks: min ≤ max, from ≤ to. Filters in the URL; "Clear filters".
- **`DataTable` (dense):** checkbox, Claim number, Claimant, Department, Status, Total, Flags, Submitted, Waiting since. Sortable columns, sticky header, total count, keyboard row selection.
- **Bulk approve:** select rows (max 100; select-all limited to the current page) → `BulkActionBar` "Approve selected (n) · ₹total" → `ConfirmDialog` stating count, total and how many are flagged (flagged allowed, SPEC-Q-09) → API-023 `{claimIds}` with `Idempotency-Key`.
- **Bulk result (per-claim):** summary toast "n approved, m failed"; failed rows stay selected and show their `code` message inline (e.g. `STATE_CONFLICT`, `SELF_APPROVAL_FORBIDDEN`); the list refreshes.
- **Sample audits (CL-14):** `SAMPLE_AUDIT_PENDING` rows open S-06 in review mode (Approve / Reject with comment).
- **Empty:** "No claims match these filters." **Phone:** cards with selection checkbox; bulk bar sticky at the bottom.

### S-08 DelegationsPage (FR-008, CL-13, ASM-007)
- **Data:** API-019 (caller's delegations). **Table:** Delegate, Start date, End date (inclusive, IST), Status (active / scheduled / ended / cancelled — derived from dates and cancelled flag). "Cancel" → `ConfirmDialog` → API-021 (delegator only).
- **Create form:** Delegate (select from API-038 eligible delegates — already filtered to `FINANCE_AUDITOR`/`FINANCE_HEAD`, not self), Start date (required), End date (required, ≥ start). Submit → API-020. Errors: `INVALID_DELEGATE` on delegate field; `DELEGATION_OVERLAP` on the dates.
- **Help text:** "Your delegate can act on manager approvals assigned to you during these dates. You can still act yourself." (backbone §5.4, ASM-S-04). See FE-I-15.

### S-09 PayoutBatchesPage (FR-012, §2.8)
- **Data:** API-024 (paged). **Table:** Batch number (`PB-YYYY-NNNN`), Status (`CREATED` / `EXPORTED`), Rows, Total, Created by/at, Exported by/at. Row → S-10.
- **Create batch:** "New batch" opens a panel listing API-025 eligible claims (`APPROVED`, in no batch; sampled claims excluded by the server) in a selectable `DataTable` (claim number, employee, department, amount); footer shows count + total → `ConfirmDialog` → API-026 with `Idempotency-Key` → navigate to S-10. `CLAIM_NOT_ELIGIBLE_FOR_PAYOUT` → refetch.
- **Empty eligible list:** "No approved claims are waiting for payout."

### S-10 PayoutBatchDetailPage (FR-013, FR-014, CL-06)
- **Data:** API-027. Header: batch number, status, rows, total; claims table (claim number, employee code, name, department, amount) — not linked to S-04 (§2).
- **Export:** shown only for `CREATED` → `ConfirmDialog` ("Export locks this batch and marks {n} claims as Paid. This cannot be undone.") → API-028 with `Idempotency-Key`.
- **Control summary** (for `EXPORTED`, from API-028/API-027): rows, total amount, SHA-256 (monospace, copy button) (§2.8).
- **Download:** shown only for `EXPORTED` → API-029 as blob; file name from `Content-Disposition`, fallback `EXPENSEFLOW_PAYOUT_<batchNumber>_<YYYYMMDD>.csv`. Re-downloads return the same file. No edit controls.

### S-11 DashboardPage (FR-015–017, ASM-010, SPEC-Q-11)
- **Data:** API-030 spend, API-031 policy violations, API-032 average approval time, API-036 departments.
- **Filters** (backbone §7.3): `fromDate`, `toDate` (expense date), `departmentId`; in the URL; applied to all three sections.
- **Layout:** (1) Spend by category, by department, by month — compact tables with an inline CSS bar column (no chart library approved), footnote "Approved, in-batch and paid claims; month of expense date"; (2) Policy violations by rule (POL-### + count); (3) Average approval time (single figure, footnote "Submit to final approval, calendar time" — SPEC-Q-08). Desktop 2-column grid; phone stacked. Each section has its own loading/empty/error state.
- Aggregates only; no drill-down into claims (ASM-010).

### S-12 AuditTrailPage (FR-018, NFR-004)
- **Data:** API-033 (paged, newest first). **Table:** Timestamp (IST), Actor, On behalf of, Action (backbone audit action enum), Entity type + ID, Claim number, From → To state, Comment, Trace ID. Expand row → `details` JSON rendered as key/value text (escaped).
- Read-only: **no edit or delete controls anywhere** (NFR-004). Filters: none until the contract defines them (FE-I-07).

### S-13 PolicyRulesPage (CL-10, B-08)
- **Data:** API-034. **Table:** key (e.g. `meals.max_per_day`), current value, type, related rule (POL-### / routing), config version, last changed by/at if returned.
- **Edit:** one key at a time in a dialog → API-035. Input by type (backbone §4.1): money > 0 (`receipt.mandatory_above` ≥ 0; `approval.finance_head_threshold` > `approval.auditor_threshold`); decimal 0 < x ≤ 1 (`sample_audit.rate`); non-empty string list (`hotel.tier1_cities`, `duplicate.match_fields`); boolean (flags). Server `400 VALIDATION_FAILED` shown on the field. Notice: "Changes apply to new submissions only; submitted claims keep their rules" (§4.2, B-08). Every change is audited server-side.

## 5. Reusable component inventory (`src/components/`)

| Component | Purpose / key props | Used by |
|---|---|---|
| `AppLayout` | Header (ExpenseFlow, user name, role, sign out), capability-filtered nav, skip link, `<main>` | All |
| `RequireAuth` | Redirects to `/login?next=` when no session | Routes |
| `RoleGate` | `roles=[…]` and/or `capabilities=[…]` (`isManager`, `isApprover`, `canDelegate`, `isAdmin`), any-of → children or `ForbiddenState`; wraps routes and nav items | Routes, nav |
| `DataTable` | `columns`, `rows`, `getRowId`, server `page/pageSize/sort/total`, `onQueryChange`, `selectable`, `selected`, `onSelectionChange`, `rowStatus` (inline per-row message), `onRowClick`, `cardLayoutBelow`, `loading`, `empty`; sticky header; `aria-sort` | S-02, 05, 07, 09, 10, 12, 13 |
| `Pagination` | Page, size (20/50/100), total | `DataTable` |
| `BulkActionBar` | Selected count + total + actions; sticky | S-07, S-09 |
| `FilterBar` | Controlled filters synced to URL query | S-07, S-11 |
| `StatusBadge` | Claim status, step status or batch status (backbone enums) → label + colour + icon | All |
| `MoneyDisplay` | String → `Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',minimumFractionDigits:2})` → ₹1,00,000.00; tabular numerals; right-aligned in tables | All |
| `MoneyInput` | Decimal text input, ≤ 2 decimals; emits string | S-03, S-07, S-13 |
| `DateDisplay` | Timestamps → IST (`timeZone:'Asia/Kolkata'`); date-only values unchanged; `<time dateTime>` | All |
| `FormField` | Label, input slot, hint, error; wires `id`, `aria-describedby`, `aria-invalid` | All forms |
| `ErrorSummary` | Focusable list of field errors linking to fields | Forms |
| `LineItemForm` | Line fields of S-03 incl. hotel-only fields | S-03 |
| `ReceiptUpload` | File pick/camera, progress, 413/415 mapping, replace | S-03 |
| `ReceiptViewer` | API-011 as blob → object URL; image (zoom, rotate, fit) or PDF (`<object>`); revokes URL on unmount; "Open in new tab" | S-04, S-06 |
| `PolicyResultList` | Results by line: rule ID, reason, limit vs actual, type (`LIMIT_BREACH` / `DUPLICATE_FLAG` / `WEEKEND_FLAG`) | S-04, S-06 |
| `ApprovalSteps` | Steps with `step_status`, approver/pool, `onBehalfOf`, skip reason | S-04, S-06 |
| `CommentList` | Comments from API-006 | S-03 banner, S-04, S-06 |
| `AuditHistory` | Timeline from API-013 | S-04, S-06 |
| `ConfirmDialog` | `title`, `body`, `confirmLabel`, `commentMode` (`none`/`optional`/`required`), `onConfirm(comment)`; focus trap, Esc closes, focus restored; confirm disabled while required comment empty or request in flight | S-03, 06, 07, 08, 09, 10, 13 |
| `LoadingState` / `EmptyState` / `ErrorState` / `ForbiddenState` | Standard states; `ErrorState` shows message, retry, traceId | All |
| `Toast` | `role="status"` (success) / `role="alert"` (error) | All |

No copy-pasted variants (§10.1).

## 6. State and data handling

### 6.1 API layer (`src/api/`)
- `client.js`: `BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'`; `Authorization: Bearer <token>`; JSON and `FormData` bodies; `blob` mode (API-011, API-029) returning blob + `Content-Disposition` name; query builder for `page/pageSize/sort` + filters; optional `idempotencyKey` → `Idempotency-Key`; errors parsed into `ApiError {status, code, message, details, traceId}`; non-JSON body → `INTERNAL_ERROR` with `X-Trace-Id`; 401 → `onUnauthorized`.
- One module per resource, functions named after the operation, API ID in a comment:

| Module | API IDs |
|---|---|
| `auth.js` | API-002, API-003 |
| `claims.js` | API-004–API-013 |
| `approvals.js` | API-014–API-018 |
| `delegations.js` | API-019–API-021, API-038 |
| `reviewQueue.js` | API-022, API-023 |
| `payoutBatches.js` | API-024–API-029 |
| `dashboard.js` | API-030–API-032 |
| `auditEvents.js` | API-033 |
| `policyRules.js` | API-034, API-035, API-037 |
| `departments.js` | API-036 |

Field names follow `architecture/api-contracts.yaml` (§12.5); `health.js` is removed.

### 6.2 Auth context (`src/context/AuthContext.jsx`)
- Holds `user` (API-003: id, name, role, `isManager`, `isApprover`, `canDelegate`, `isAdmin`), `token`, `login()`, `logout()`, `hasRole(...)`, `can(capability)`. Re-fetch API-003 after creating/cancelling a delegation is not needed (delegate status affects the delegate, not the delegator).
- **Token storage (§10.10): dependent on SPEC-Q-07 and ADR-000.** Default: JWT **in memory only** (React state); a reload requires sign-in again. `localStorage`/`sessionStorage` not used unless the ADR approves it. If the ADR picks an HttpOnly cookie, `client.js` switches to `credentials:'include'` and CSRF handling (§13.3); screens are unchanged.
- Logout is client-side (no logout endpoint): clear state, go to `/login`.
- `ClientConfigContext`: API-037 loaded after login, shared by S-03 and S-07.

### 6.3 Hooks (`src/hooks/`)
| Hook | Behaviour |
|---|---|
| `useQuery(fn, deps)` | `{data, error, loading, reload}`; aborts on unmount (`AbortController`) |
| `usePagedQuery(fn, query)` | Wraps `useQuery` for `{items,page,pageSize,total}` |
| `useMutation(fn)` | `{run, pending, error}`; ignores calls while `pending`; manages Idempotency-Key |
| `useUrlQuery(schema)` | Page/sort/filters ↔ URL query |
| `useUnsavedChangesPrompt(dirty)` | Warns before navigation |

No global data-cache library (none approved).

### 6.4 Idempotency-Key
- `crypto.randomUUID()` created when the user opens the action (dialog/form). The **same key and same body are reused on retry** after a network error/timeout; a new key after success or whenever the body changes (e.g. comment edited, selection changed) — so `IDEMPOTENCY_KEY_REUSED` cannot occur in normal use.
- Used on API-012, 015, 016, 017, 023, 026, 028 only (backbone §8). Server keeps keys 24 h.

### 6.5 Utilities (`src/utils/`)
`money.js` (format, string ↔ paise, sum in paise), `dates.js` (IST formatting, today in IST, nights between dates), `errors.js` (code → message, §3.1), `validation.js` (rules of S-03, S-07 filters, S-08, S-13).

## 7. Accessibility and responsive requirements (§10.2, §10.6)

| ID | Requirement | Concrete check |
|---|---|---|
| A11Y-01 | Every input has a linked `<label>` | RTL `getByLabelText` finds every field |
| A11Y-02 | Errors announced and linked | `aria-invalid="true"` + `aria-describedby` → error id; `ErrorSummary` focused on failed submit |
| A11Y-03 | Keyboard only | S-03 and S-06 flows completed with Tab/Enter/Space/Esc in E2E; no `tabindex` > 0 |
| A11Y-04 | Visible focus | `:focus-visible` outline ≥ 2 px, contrast ≥ 3:1 |
| A11Y-05 | Contrast | Text ≥ 4.5:1 (WCAG 2.1 AA); current `--muted` `#6b6b72` on `--bg` `#f7f7f8` ≈ 5:1, OK |
| A11Y-06 | Not colour alone | `StatusBadge` and flags have text labels |
| A11Y-07 | Dialogs | `role="dialog"`, `aria-modal`, labelled, focus trapped and restored |
| A11Y-08 | Tables | `<th scope>`, `aria-sort`, checkbox labels "Select claim {claimNumber}" |
| A11Y-09 | Live regions | Toasts and bulk results use `role="status"` / `"alert"` |
| A11Y-10 | Landmarks | One `<h1>` per page, skip link, `<nav>`, `<main>` |
| RESP-01 | Breakpoints | Phone < 640 px, tablet 640–1023 px, desktop ≥ 1024 px |
| RESP-02 | Claim capture on phone | At 360×640 S-03 has no horizontal scroll; touch targets ≥ 44×44 px; sticky Save/Submit bar |
| RESP-03 | Tables | Below 640 px `DataTable` renders cards; dense rows (≈ 36 px) on desktop |
| RESP-04 | Side by side | S-06 shows receipt and claim together at ≥ 1024 px |
| RESP-05 | Zoom | Usable at 200 % zoom |

## 8. Gap analysis vs current base

| Area | Current | Required | Action |
|---|---|---|---|
| `client.js` base URL | `'/api'` | `'/api/v1'` (§9.4) | Change default |
| Error parsing | RFC 9457 (`detail`, `title`) | `{code,message,details,traceId}` (§11.4) | Rewrite `ApiError` |
| Non-JSON error bodies | `JSON.parse` throws | Fallback `INTERNAL_ERROR` + `X-Trace-Id` | Add |
| Auth header, 401, blob/FormData, paging query, Idempotency-Key | Missing | §6.1 | Add |
| `health.js`, `HomePage.jsx` | Health check page | Not a required screen | Replace `/` with redirect to `/claims` |
| `Layout.jsx` | "Hackathon App", Home link | `AppLayout` with capability nav, user, sign out, skip link | Rewrite |
| `index.html` title | "Hackathon App" | "ExpenseFlow" | Change |
| `index.css` | `.app-main` max 960 px; no breakpoints, focus styles, badges | Full-width tables, breakpoints, focus, tokens | Extend; move to `src/styles/` (README) |
| Folders | `api`, `components`, `pages` | + `hooks`, `context`, `styles`, `utils` | Create |
| Routing | Home + 404 | §2 route map, `RequireAuth`, `RoleGate` | Add |
| Tests | None; no test script | Vitest + RTL (Proposed, ADR-000), Playwright | Add after ADR-000 |
| Lint | `oxlint` | §8.1 lists ESLint + Prettier (Proposed); backbone §13 defers to ADR-000 | Decide in ADR-000 |
| `nginx.conf` | Default `client_max_body_size` 1 MB | ≥ receipt limit (SPEC-Q-06 default 5 MB) | Raise when SPEC-Q-06 answered |
| `README.md` | `.env.example` with `VITE_API_BASE_URL` | Document default `/api/v1` | Update |
| Vite | `^8.4.0` | "Vite 7 or later" (§8.1) | OK |

## 9. Implementation tasks (CLAUDE.md Phase 5)

Prerequisites: `api-contracts.yaml` for API-001..API-038 written and linted (Phase 3); ADR-000 confirms Vitest + RTL and the lint tool; SPEC-Q-07 answered or default accepted. Each task: plan first (§14.2), tests with it, `PROGRESS.md` updated.

| # | Task | Requirements | Done-check |
|---|---|---|---|
| T-01 | `client.js` (base `/api/v1`, error shape, auth, blob, FormData, paging, Idempotency-Key, non-JSON fallback) + `utils/errors.js`, `money.js`, `dates.js` | §11.4, §10.5, §10.7, §4.3 | Unit tests for every error code, non-JSON body, paise sums |
| T-02 | Folders, styles, `AppLayout`, `RequireAuth`, `RoleGate`, route skeleton, `index.html` title | §10.9, FR-019 | Nav hides links per capability; 404 page |
| T-03 | `AuthContext`, `ClientConfigContext`, S-01 LoginPage | SPEC-Q-07, §10.10 | Login/401/429 tests; token not in Web Storage |
| T-04 | Base components: `FormField`, `ErrorSummary`, `MoneyDisplay`, `MoneyInput`, `DateDisplay`, `StatusBadge`, states, `Toast`, `ConfirmDialog` | §10.1, §10.6, ASM-002 | `₹1,00,000.00` and IST tests; required-comment test |
| T-05 | `DataTable`, `Pagination`, `BulkActionBar`, `FilterBar` | NFR-003 | Server sort/page, selection, per-row status, card layout tests |
| T-06 | S-02 MyClaimsPage | FR-019, CL-15 | Loading/empty/error/content tests |
| T-07 | S-03 ClaimEditorPage + `LineItemForm` + `ReceiptUpload` | FR-001–003, CL-05, CL-08, ASM-003, ASM-005 | Validation tests (amount 0, negative, 3 decimals, future date, vendor 121 chars, hotel dates, receipt at 500.00 vs 500.01); double-click submit sends one request |
| T-08 | S-04 ClaimDetailPage + `PolicyResultList` + `ApprovalSteps` + `CommentList` + `AuditHistory` + `ReceiptViewer` | FR-004, FR-018, CL-07 | Auto-rejected banner lists every breaching line; actions follow `allowedActions` |
| T-09 | S-05 ApprovalsQueuePage + S-06 ApproverReviewPage | FR-005–008, FR-020, CL-04, NFR-002 | Reject without comment blocked; empty `allowedActions` hides buttons; 409/`NOT_CURRENT_APPROVER` reload |
| T-10 | S-07 ReviewQueuePage | FR-009–011, CL-14, SPEC-Q-09 | Filters in URL; one API-023 call per confirm; failed rows shown with reason |
| T-11 | S-08 DelegationsPage | FR-008, CL-13, ASM-007 | Picker from API-038; `DELEGATION_OVERLAP` shown on dates |
| T-12 | S-09 / S-10 payout batches | FR-012–014, CL-06, §2.8 | Export only on `CREATED`; summary rows/total/SHA-256; download name |
| T-13 | S-11 DashboardPage | FR-015–017, ASM-010 | Filters applied to all sections; own states per section |
| T-14 | S-12 AuditTrailPage | FR-018, NFR-004 | No edit/delete control rendered |
| T-15 | S-13 PolicyRulesPage | CL-10, B-08 | Typed inputs incl. threshold ordering; non-admin sees `ForbiddenState` |
| T-16 | Accessibility + responsive pass (§7) | NFR-001, §10.2, §10.6 | All A11Y/RESP checks recorded |
| T-17 | Playwright E2E (§9.1) against the seeded Docker Compose stack | §16.5 | ≥ 10 cases pass; results in `deliverables/test-cases.xlsx` |
| T-18 | Docs: README, traceability workbook (screen column), `PROGRESS.md` | §17 | Updated in the same task |

### 9.1 Tests (`tests/frontend/`)

| Suite | Tool | Location | IDs |
|---|---|---|---|
| Component / unit | Vitest + React Testing Library + jsdom — **Proposed, pending ADR-000** | `tests/frontend/unit/` | `TC-FE-###` |
| End-to-end | Playwright ≥ 1.60 (Chromium; fallback channel `msedge`/`chrome`) | `tests/frontend/e2e/` | `TC-E2E-###` |

Minimum E2E cases (demo story, §16.5): E2E-01 phone-width claim capture + submit; E2E-02 auto-approve ≤ 25,000; E2E-03 limit breach → `AUTO_REJECTED` with reasons; E2E-04 manager approves 25,000.01 → `PENDING_AUDITOR` appears in auditor's review queue; E2E-05 three-step chain > 1,00,000; E2E-06 reject requires comment; E2E-07 send back → edit → resubmit restarts at first step (CL-12); E2E-08 delegate approves a manager step on behalf (CL-13); E2E-09 auditor filters + bulk approve incl. one failing row; E2E-10 payout batch create → export → claims `PAID` → download; E2E-11 employee opening another employee's claim URL sees not-found (CL-15); E2E-12 sample-audit claim approved from the review queue becomes payout-eligible (CL-14).

## 10. Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-01 | An employee on a 360×640 viewport | They create a claim with a `HOTEL` line (Mumbai, 2 nights) and a `MEALS` line, attach receipts and tap Submit | No horizontal scroll; every field reachable; one API-012 call with an `Idempotency-Key`; S-04 shows the resulting status and policy results |
| AC-02 | A draft line of ₹500.01 without a receipt (`receiptMandatoryAbove` = 500.00) | The employee taps Submit | The line shows "A receipt is required for lines above ₹500.00"; a server `RECEIPT_REQUIRED` gives the same message on that line |
| AC-03 | A line of exactly ₹500.00 without a receipt | The employee submits | No client receipt error |
| AC-04 | A claim with no lines | The employee taps Submit | "Add at least one expense line before submitting." and no request (or `CLAIM_HAS_NO_LINES` mapped identically) |
| AC-05 | Submit is in flight | The user clicks Submit again | The button is disabled; exactly one request is sent |
| AC-06 | A manager with a `PENDING_MANAGER` claim on desktop | They open it from S-05 | Lines, flags and the selected line's receipt are visible side by side |
| AC-07 | The approver chooses Reject | The comment is empty or whitespace | Confirm is disabled; nothing is sent |
| AC-08 | The approver approves | The server returns 409 or `NOT_CURRENT_APPROVER` | A clear message shows and the claim reloads with its current status |
| AC-09 | API-006 returns empty `allowedActions` | S-06 loads | No Approve/Reject/Send back buttons are rendered |
| AC-10 | A finance auditor selects 3 rows, one already approved by a colleague | They confirm bulk approve | One API-023 call with one key; toast "2 approved, 1 failed"; the failed row shows its reason |
| AC-11 | A finance head on S-10 with a `CREATED` batch | They confirm Export | Status `EXPORTED`; control summary shows rows, total (₹ Indian grouping) and SHA-256; Download appears; Export disappears |
| AC-12 | An `EMPLOYEE` without `isAdmin` | They visit `/payout-batches` | `ForbiddenState`; no API-024 call; no Payout nav link |
| AC-13 | A user with `isApprover` false | They view the nav | No Approvals link; `/approvals` shows `ForbiddenState` |
| AC-14 | An employee or a finance head with no part in a claim | They open `/claims/<that id>` | "Not found or you do not have access." |
| AC-15 | Creating a delegation overlapping an active one | They submit | `DELEGATION_OVERLAP` message on the date fields |
| AC-16 | Any error with `traceId` | It is shown | Human-readable message plus "Reference: {traceId}"; no raw JSON |
| AC-17 | API value `"100000.00"` | It is displayed | `₹1,00,000.00` |
| AC-18 | Timestamp `2026-11-27T18:45:00Z` | It is displayed | 28 Nov 2026, 00:15 IST |

## 11. Open questions referenced

| ID | Effect on the UI | Default used here |
|---|---|---|
| SPEC-Q-02 / Q-14 | Category options | From API-037 (`MEALS`, `HOTEL`, `LOCAL_CAB`) |
| SPEC-Q-03 | Send-back comment | Mandatory |
| SPEC-Q-05 | Duplicate flag wording | Server reason text |
| SPEC-Q-06 / Q-21 | Receipt `accept`, size message, nginx limit | PDF, JPG, PNG; 5 MB |
| SPEC-Q-07 / Q-22 | Login form, token storage | Email + password; in-memory JWT |
| SPEC-Q-08 / Q-23 | Approval-time footnote | Submit → final approval, calendar time |
| SPEC-Q-09 / Q-24 | Review-queue filters; flagged in bulk approve | Backbone §7.2; flagged allowed |
| SPEC-Q-10 / Q-25 | Receipt on draft; withdraw | Receipt at submit only; no withdraw |
| SPEC-Q-11 | Dashboard spend definition | Backbone §7.3 |
| SPEC-Q-12 | Page latency (Playwright timings may be used) | Backbone default |
| SPEC-Q-13 | Session expiry (JWT lifetime), 429 frequency | 8 h; redirect to login on 401 |
| SPEC-Q-14 | Finance head's clean claim outcome | Server decides; UI shows status |
| SPEC-Q-15 | `receiptNumber` required on every line | Required |
| ASM-S-03 | Hotel `expenseDate` = check-in | Applied (proposed) |
| ASM-S-04 | Delegator still acts during delegation | Help text in S-08 |
| Q-06, Q-13, Q-20 | Splitting, holidays, user management | Not built |
| ASM-002 | IST display | Applied |

## 12. Issues in `architecture/SPEC.md`

### 12.1 Resolved by backbone v0.2

| ID | Issue (v0.1) | Resolution |
|---|---|---|
| FE-I-01 | No approver indicator | API-003 capabilities (backbone §2) |
| FE-I-02 | No delegate list | API-038 |
| FE-I-03 | API-006 content | Backbone §7.1 incl. `allowedActions`, comments |
| FE-I-04 | Bulk-approve results; queue overlap | Backbone §7.2 |
| FE-I-05 | Paging convention | Backbone §8 |
| FE-I-06 | Batch status enum | `CREATED`, `EXPORTED` |
| FE-I-07 | Dashboard filters | Backbone §7.3 (audit-trail filters still open — FE-I-17) |
| FE-I-08 | Receipt threshold for employees | API-037 |
| FE-I-09 | Line-item rules | Backbone §4.4 (+ SPEC-Q-15, ASM-S-03) |
| FE-I-10 | Lint/test tooling | Deferred to ADR-000 (backbone §13) |
| FE-I-11 | Money in JSON | String, 2 decimals |
| FE-I-12 | Finance head sees any claim | Removed; visibility rule in backbone §7 |
| FE-I-13 | Send-back comment source | API-006 comments |

### 12.2 Still open

| ID | Issue | Impact on UI | Suggested resolution |
|---|---|---|---|
| FE-I-14 | `allowedActions` values are not enumerated (e.g. edit, submit, approve, reject, send-back, comment) | Buttons cannot be mapped without guessing names | List the enum in backbone §7.1 |
| FE-I-15 | `canDelegate` is true for a `FINANCE_AUDITOR`/`FINANCE_HEAD` who manages nobody, but delegation only covers individual (manager) steps (§5.4) — such a delegation has no effect | S-08 would let users create meaningless delegations | Make `canDelegate` = `isManager`, or state the intended effect |
| FE-I-16 | API-037 does not carry receipt file types / size (SPEC-Q-06) | `accept` attribute and size pre-check hard-coded | Add to API-037 once SPEC-Q-06 is answered |
| FE-I-17 | No filters defined for API-033 audit trail | 3,000+ claims make the trail hard to search | Define filters (e.g. date range, action, claim number) or confirm none |
| FE-I-18 | Shape of `422 RECEIPT_REQUIRED` per-line `details` not defined (only `details.fields` for 400) | Mapping errors to lines | Define `details` for 422 in §9 |
| FE-I-19 | `expenseDate` "not in the future" — reference time zone not stated | Client and server may disagree near midnight | State IST (ASM-002) |
| FE-I-20 | API-019 content unclear: only delegations the caller created, or also those where the caller is the delegate | S-08 table scope | State it in backbone §7 |
