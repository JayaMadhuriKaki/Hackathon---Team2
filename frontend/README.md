# ExpenseFlow — Frontend

React 19 + Vite single-page app for ExpenseFlow (expense claims and policy-driven approvals).
Specification: [`SPEC.md`](SPEC.md). API: Spring Boot backend under `/api/v1`, described by
[`architecture/api-contracts.yaml`](../architecture/api-contracts.yaml) (browse it in Swagger UI at
http://localhost:8082 when the Docker stack is up). Field names in `src/api/` follow the contract, and
`tests/frontend/unit/contract.test.js` fails if a frontend call is not a documented operation.

## Run

```powershell
cd frontend
npm install
npm run dev            # http://localhost:5173, proxies /api to http://localhost:8080
```

With the whole stack in Docker (`docker compose up -d --build` at the repo root) the app is served by nginx at
http://localhost:3000 and proxies `/api` to the backend container.

Demo users (seeded when `SEED_DEMO_USERS=true`; password = `SEED_DEMO_PASSWORD`):

| Email | Role | Notes |
|---|---|---|
| `employee@expenseflow.test` | Employee | Reports to Manoj (manager) |
| `employee2@expenseflow.test` | Employee | Reports to Manoj |
| `manager@expenseflow.test` | Employee + line manager | Approves manager steps, can delegate |
| `auditor@expenseflow.test`, `auditor2@…` | Finance auditor | Review queue, sample audits, dashboard |
| `financehead@expenseflow.test` | Finance head (admin) | Payouts, audit trail, policy rules, dashboard |
| `nomanager@expenseflow.test` | Employee, no manager | Exercises CL-09 skip |
| `staff@expenseflow.test` | Employee reporting to an auditor | |

**Session:** the JWT is kept in memory only (SPEC §6.2), so reloading the page signs you out.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with API proxy |
| `npm run build` | Production build to `dist/` |
| `npm run lint` | oxlint |
| `npm test` | Vitest component/unit tests in `../tests/frontend/unit` |
| `npm run test:e2e:ui` | Playwright browser checks with a mocked API (layout at 1280×720 and 360×640, keyboard, side-by-side receipt) — no backend needed |
| `npm run test:e2e:stack` | Playwright demo path against the running Docker stack. Needs `E2E_PASSWORD` (= `SEED_DEMO_PASSWORD`); optional `E2E_BASE_URL` (default `http://localhost:3000`) |

If the Playwright browser download is blocked, set `PW_CHANNEL=msedge` (or `chrome`).

## Structure

```
src/
  api/          client.js (fetch wrapper: auth, errors, Idempotency-Key, blobs) + index.js (one function per API-###)
  components/   AppLayout (nav + gates), DataTable, ConfirmDialog, FormField, StatusBadge/MoneyDisplay/DateDisplay,
                ClaimParts (policy results, steps, comments, history), ReceiptViewer, LineItemForm
  context/      AuthContext (in-memory session + client config), ToastContext
  hooks/        useQuery, useMutation (duplicate-submit guard + idempotency key), useUrlQuery, useDocumentTitle
  pages/        one per screen S-01..S-13
  styles/       index.css (tokens, responsive rules)
  utils/        money (paise, ₹ Indian grouping), dates (IST), errors (code → message), validation
```

## Screens

| Route | Screen | Who |
|---|---|---|
| `/login` | S-01 Sign in | Everyone |
| `/claims` | S-02 My claims | Everyone (own claims only) |
| `/claims/new`, `/claims/:id/edit` | S-03 Claim editor (mobile-first) | Claimant, DRAFT/SENT_BACK |
| `/claims/:id` | S-04 Claim detail | Anyone the server lets see it |
| `/approvals`, `/approvals/:id` | S-05 Approvals queue, S-06 Review (receipt side by side) | Approvers |
| `/review-queue`, `/review-queue/:id` | S-07 Review queue with filters + bulk approve | Finance auditor |
| `/delegations` | S-08 Delegations | Managers |
| `/payout-batches`, `/payout-batches/:id` | S-09/S-10 Payout batches, export, CSV download | Finance head / admin |
| `/dashboard` | S-11 Dashboard | Finance auditor, finance head |
| `/audit-trail` | S-12 Audit trail (read-only) | Finance head / admin |
| `/policy-rules` | S-13 Policy rules | Finance head / admin |

Hiding a link or button is only for usability — the backend enforces every permission.
