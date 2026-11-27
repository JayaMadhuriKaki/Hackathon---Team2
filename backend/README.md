# Backend — ExpenseFlow API

Spring Boot 4.1.3 on JDK 21 (Maven wrapper). Implements API-001..API-038 of `../architecture/SPEC.md` §7; behaviour is specified in `SPEC.md` in this folder.

## Run with Docker (normal way)

From the repo root:

```powershell
docker compose up -d --build        # postgres, backend, frontend, prometheus, grafana
```

- API: http://localhost:8080/api/v1 (also via the frontend proxy http://localhost:3000/api/v1)
- On start the backend runs Flyway migrations `../database/migrations/V001..V005` as the database owner, then connects as the least-privilege role `expenseflow_app` (SELECT/INSERT only on `audit_events`).
- With `SEED_DEMO_USERS=true` (Compose default) it seeds 8 synthetic demo users once: `financehead`, `auditor`, `auditor2`, `manager`, `employee`, `employee2`, `nomanager`, `staff` — each `<name>@expenseflow.test`, password from `SEED_DEMO_PASSWORD`. Set your own values in `.env`.
- Actuator (`/actuator/health`, `/actuator/prometheus`) is on port 8081, which is **not** published: only Prometheus and the container healthcheck reach it.
- Reset everything: `docker compose down -v` (the audit table cannot be truncated by design).

## Environment variables

| Variable | Purpose | Compose default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/hackathon` |
| `DB_OWNER_USERNAME` / `DB_OWNER_PASSWORD` | Flyway + demo seed | the Postgres superuser from `.env` |
| `DB_USERNAME` / `DB_PASSWORD` | Runtime app role (created by V005) | `expenseflow_app` / set `DB_APP_PASSWORD` in `.env` |
| `JWT_SECRET` | HS256 key, at least 32 bytes. Unset → random per start (tokens die on restart) | unset |
| `JWT_TTL_MINUTES` | Token lifetime | 480 |
| `SEED_DEMO_USERS`, `SEED_DEMO_PASSWORD` | Demo logins | `true`, demo value |
| `RECEIPTS_DIR`, `EXPORTS_DIR` | Receipt files, payout CSVs (Docker volumes) | `/data/receipts`, `/data/exports` |
| `RATE_LIMIT_LOGIN_PER_MIN`, `RATE_LIMIT_UPLOAD_PER_MIN` | 429 limits | 5, 30 |

Business values (limits, thresholds, sample rate) are never env vars: they live in `policy_rules` (API-034/035).

## Build and test locally

```powershell
.\mvnw.cmd clean test     # 131 JUnit tests on H2: policy rules, routing, chain, delegation, API flows
.\mvnw.cmd spring-boot:run  # needs Postgres on localhost:5433 and the env vars above
```

## Package layout (`com.hackathon.backend`)

`controller` (HTTP only) → `service` (business rules, transactions, audit) → `repository` (Spring Data JPA) → PostgreSQL. Pure, framework-free engines: `policy` (POL-001..009, routing, sample draw) and `chain` (approval chain, step authorizer, delegation rules) — `EnginePurityTest` enforces this. Also `domain`, `dto`, `security`, `config`, `exception`.

## Quick API tour

```text
POST /auth/login {email,password}          -> accessToken (Bearer)
POST /claims                               -> draft claim
POST /claims/{id}/line-items               -> add MEALS / HOTEL / LOCAL_CAB line
POST /claims/{id}/line-items/{l}/receipt   -> multipart file (PDF/JPEG/PNG, 5 MB)
POST /claims/{id}/submit                   -> AUTO_REJECTED | PENDING_* | APPROVED | SAMPLE_AUDIT_PENDING
GET  /approvals, /review-queue             -> approver and auditor queues
POST /claims/{id}/approve | reject | send-back
POST /payout-batches, /payout-batches/{id}/export, GET .../file  -> NEFT-style CSV
```

All errors: `{code, message, details, traceId}`; every response carries `X-Trace-Id`.
