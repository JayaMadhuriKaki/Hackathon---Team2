# ExpenseFlow – Expense Claims and Policy-Driven Approvals

AI Manthan hackathon, Track A. Expense system with a policy engine, a multi-level approval chain, an auditor review queue, a payout export and a dashboard. Read this file before every task.

## Ground rules (non-negotiable)

- Synthetic data only. Never use real names, bank details or client data.
- Open-source tools only. No secrets in git: config comes from env vars / `.env` (gitignored).
- Policy rules are **configuration** (DB rows seeded at startup), never hard-coded in `if` statements.
- Every state change writes an audit event. The audit trail is append-only.
- Money is `BigDecimal` in Java and `numeric(12,2)` in Postgres. INR only. Never use `double` for money.
- Plan first: for anything non-trivial, propose the plan and the files you will touch before editing.
- Write the tests for a policy rule before the rule's implementation.

## Stack and commands

- `backend/` Spring Boot 4.1.3, Java 21, Maven wrapper, Postgres 18, JPA (`ddl-auto=update`)
- `frontend/` React 19 + Vite, React Router, fetch wrapper in `src/api/client.js`
- `docker-compose.yml` postgres :5433, backend :8080, frontend :3000, prometheus :9090, grafana :3001

```powershell
docker compose up -d --build                 # whole stack
docker compose up -d postgres                # DB only, for local dev
cd backend;  .\mvnw.cmd spring-boot:run      # backend with hot reload
cd backend;  .\mvnw.cmd test                 # tests (H2, no Postgres needed)
cd frontend; npm run dev                     # :5173, proxies /api to :8080
cd frontend; npm run lint; npm run build
```

## Code conventions

**Backend** – one package per feature under `com.hackathon.backend` (`claim`, `policy`, `approval`, `audit`, `payout`, `employee`, `dashboard`, `auth`). Each has entity, repository, service, controller, and request/response records. Controllers are thin; business logic lives in services. Entities extend `common.entity.BaseEntity`. Throw `ResourceNotFoundException` / `IllegalArgumentException`; `GlobalExceptionHandler` turns them into problem JSON. All endpoints are under `/api`. Validate request bodies with `@Valid`.

**Frontend** – one file per resource in `src/api/`, one page per route in `src/pages/`, shared UI in `src/components/`. Must be usable at phone width (claim capture especially).

**Tests** – every policy rule has unit tests for under, at and over the limit. Approval thresholds and delegation have their own tests.

## Domain

### Personas and visibility

| Role | Can see / do |
|---|---|
| EMPLOYEE | Create, edit (in DRAFT / CLARIFICATION_REQUESTED) and submit **only their own** claims |
| MANAGER | Everything an employee can, plus approve claims assigned to them (their direct reports, or as delegate) |
| FINANCE_AUDITOR | Review queue (all claims at auditor step + sample audits), bulk approve, filters |
| FINANCE_HEAD | Final approval above 1,00,000; admin: policy config, payout batches, dashboard |

Manager is not a stored role: anyone with direct reports acts as a manager.

### Claim state machine

```
DRAFT ──submit──► SUBMITTED ──policy engine──┬─► AUTO_REJECTED            (terminal)
                                             ├─► APPROVED (auto)          (may be held for sample audit)
                                             └─► PENDING_APPROVAL
PENDING_APPROVAL ──approve (last step)──► APPROVED
PENDING_APPROVAL ──approve (more steps)──► PENDING_APPROVAL (next step)
PENDING_APPROVAL ──reject──► REJECTED                                     (terminal)
PENDING_APPROVAL ──send back──► CLARIFICATION_REQUESTED ──resubmit──► SUBMITTED
APPROVED ──added to batch──► IN_PAYOUT ──batch exported──► PAID           (terminal)
```

Any transition not drawn above is invalid and must throw. Transitions live in one place (the claim service / a state machine class), not spread across controllers.

### Policy engine

Runs on submit and resubmit. Each line gets an outcome and a list of violations (rule code + human-readable reason). The **claim outcome is the worst line outcome**: AUTO_REJECT > ROUTE > AUTO_APPROVE.

| Rule (seeded config) | Parameters | Outcome when triggered |
|---|---|---|
| Meal daily limit | 1,500 per employee per day, summed across all lines and non-rejected claims for that date | ROUTE (flag) |
| Hotel nightly limit | 6,000/night tier-1, 4,000/night other; line has `city` and `nights`; limit × nights | ROUTE (flag) |
| Local cab per trip | 2,000 per line | ROUTE (flag) |
| Receipt mandatory | amount > 500 and no receipt | AUTO_REJECT |
| Duplicate receipt file | same SHA-256 as any earlier line (any employee) | AUTO_REJECT |
| Duplicate line | same employee + vendor + amount + date as an earlier line | ROUTE (flag) |
| Weekend spend | expense date is Saturday or Sunday | ROUTE (flag for manager) |
| Auto-approve ceiling | claim total ≤ 5,000 and no flags | AUTO_APPROVE, otherwise ROUTE |

Tier-1 cities (config): Mumbai, Delhi, Bengaluru, Chennai, Kolkata, Hyderabad, Pune, Ahmedabad.

Rules are stored as `policy_rule` rows (`code`, `category`, `type`, `params` JSON, `outcome`, `active`) and evaluated by one evaluator class per rule `type`. Changing a limit must not require a code change.

### Approval chain

The chain is computed **at submit time** from the claim total and stored as ordered `approval_step` rows:

1. Line manager – always.
2. Finance auditor – if total **> 25,000**.
3. Finance head – if total **> 1,00,000**.

- Thresholds are on the claim total and are strict (exactly 25,000 does not need the auditor).
- Approvers can approve, reject (reason required), send back for clarification (comment required), or comment.
- Resubmitting after clarification re-runs the policy engine and **restarts the chain from step 1**.
- **Delegation:** an approver sets a delegate with a from/to date range. During it, steps assigned to them are actionable by the delegate. The audit event records both the delegate and the original approver.
- **No self-approval:** if the resolved approver (or their delegate) is the claimant, the step goes to the next person up the hierarchy. The finance head's own claims go to a finance auditor and are flagged.

### Auditor queue and sample audit

- The queue contains claims whose current step is the auditor, plus sample-audit claims. It supports filters (department, category, amount range, date range, flags) and bulk approve.
- When a claim is auto-approved, it is picked for sample audit with 10% probability (`sample_audit = PENDING`). It cannot go into a payout batch until an auditor clears it.

### Payout

- The finance head creates a batch from APPROVED claims that are not held for sample audit. A claim can be in only one batch.
- Exporting the batch produces a CSV (mock bank file) and moves its claims to PAID.
- CSV columns: `batch_id, payment_ref, employee_code, employee_name, bank_account, ifsc, amount, claim_id, narration`. Bank details are synthetic.

### Audit trail

Table `audit_event` (`id`, `claim_id`, `actor_id`, `on_behalf_of_id`, `action`, `from_status`, `to_status`, `comment`, `created_at`). Insert only: there are no update or delete code paths, and a DB trigger rejects UPDATE/DELETE.

### Receipts

Uploaded files are stored on the `receipts` Docker volume (path from config), not in the DB. Only PDF, JPG and PNG up to 5 MB are accepted. The SHA-256 hash is computed on upload and stored on the line.

### Dashboard

Spend by category, department and month; policy violations by rule; average approval time (submit → final approval).

## Auth (hackathon level)

Seeded users log in with email + a shared demo password from config. JWT is sent in the `Authorization` header. Role checks happen in services as well as controllers, so an employee can never read another employee's claim by ID.

## Synthetic data

A seeder (dev profile, idempotent) creates 8 departments, 150 employees with a reporting hierarchy, at least one finance auditor and one finance head, and 3,000 historical claims across states. It plants known duplicates (same vendor/amount/date, and same receipt hash) and limit breaches so the dashboard and tests have something to show.

## Observability

Expose Micrometer metrics: `expenseflow_queue_size{step}`, `expenseflow_oldest_pending_approval_seconds`, `expenseflow_approval_duration_seconds`, plus standard HTTP latency. Prometheus scrapes `/actuator/prometheus`. Alert when the oldest pending approval is older than the threshold in the runbook.

## Docs (deliverables)

Put architecture diagrams, ADRs, test cases, defect log, security checklist and runbook under `docs/`.
