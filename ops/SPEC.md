# ExpenseFlow — Deployment, CI and SRE Specification

| Field | Value |
|---|---|
| Status | Draft v0.2 — for team review before any change to compose, CI or `ops/` |
| Date | 2026-11-27 |
| Parent spec | `architecture/SPEC.md` **v0.2** (backbone; metric names, `step` values, health split and DB roles are used here exactly) |
| Sources | `CLAUDE.md` §2.4 (Deployment, Support/SRE), §2.5 NFR-006/007/008, §7.1, §8 (CL-17 stack, §8.2 event rules), §13, §14.1 phases 7–8, §17; current `docker-compose.yml`, `ops/`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.github/workflows/ci.yml`, `backend/src/main/resources/application.properties` |
| Owner phases | Phase 7 (Deployment), Phase 8 (Support/SRE). Metrics code is Phase 4c (backbone §10, §14) |
| Changes in v0.2 | Aligned to backbone v0.2: `step` label values incl. `SAMPLE_AUDIT`, age from `approval_steps.activated_at`, health split (backbone §8), roles `expenseflow_owner` / `expenseflow_app` + audit trigger, repo-root backend build context, Flyway migrations as a Maven resource, `ddl-auto=validate`, seed via `psycopg` with `pgcrypto` bcrypt, `exports` volume. Re-seed-in-place path removed (audit trigger blocks `TRUNCATE`). Issues I-02..I-09 resolved (§11) |

**Rule:** every number marked **PROPOSED** is a placeholder for the team to validate (CLAUDE.md §2.4 "team validates thresholds"; §2.5 "any further NFR target must be agreed in §6"). Nothing here is a committed SLO. No secret values appear in this spec; only variable names and non-secret role names.

---

## 1. Requirement coverage

| Requirement | What it needs | Covered in |
|---|---|---|
| NFR-006 | Whole stack in Docker Compose; demo seeded with one command | §2 |
| NFR-007 | CI runs tests and a dependency vulnerability check on every push | §3 |
| NFR-008 | Metrics for queue size, approval age, page latency; dashboard; alert on oldest pending approval | §4, §5, §6 (page latency: SPEC-Q-12) |
| Deployment deliverable (§2.4) | Docker Compose, CI with tests + dependency check, one-command seeded demo; team confirms no secrets committed | §2, §3, §8 |
| SRE deliverable (§2.4) | Metrics, dashboard, alert on oldest pending approval, runbook | §4–§7 |
| CL-17 | Postgres 18 in Docker, Docker Compose delivery, Prometheus + Grafana | §2, §4, §5 |
| NFR-004, §13.1, §13.6 | DB permissions deny audit update/delete; least privilege; no committed secrets | §2.4, §3.3, §8 |
| §8.2 rule 5 | Exact versions, no upgrades | §8 |

## 2. Docker Compose target

One file at repo root: `docker-compose.yml`. Compose project name `hackathon` (keep, or rename to `expenseflow` — cosmetic).

### 2.1 Services

| Service | Image / build | Host port → container | Runs as | Healthcheck | depends_on |
|---|---|---|---|---|---|
| `postgres` | `postgres:18` pinned to an exact 18.x tag (§8) | `127.0.0.1:${DB_PORT}` (default 5433) → 5432 | `postgres` (image drops root after init) | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` | — |
| `backend` | build: context `.` (repo root), `dockerfile: backend/Dockerfile`; Flyway scripts from `database/migrations/` packaged as a Maven resource | `127.0.0.1:${BACKEND_PORT}` (default 8080) → 8080 | `app` (non-root, already done) | `GET /actuator/health` (Dockerfile `HEALTHCHECK`, backbone §8) | `postgres: service_healthy` |
| `seed` | build `database/seeds/Dockerfile` (`python:3.13-slim`, pinned; `psycopg`, Faker) | none | non-root user created in Dockerfile | none (one-shot, `restart: "no"`) | `backend: service_healthy` (Flyway has run) |
| `frontend` | build `frontend/Dockerfile` (nginx) | `127.0.0.1:${FRONTEND_PORT}` (default 3000) → nginx port | non-root (OPS-Q-07) | `wget -qO- http://127.0.0.1:<port>/` | `backend: service_healthy` |
| `prometheus` | `prom/prometheus:<exact tag>` | `127.0.0.1:${PROMETHEUS_PORT}` (default 9090) → 9090 | `nobody` (image default) | `wget -qO- http://localhost:9090/-/ready` | `backend: service_healthy` |
| `grafana` | `grafana/grafana:<exact tag>` | `127.0.0.1:${GRAFANA_PORT}` (default 3001) → 3000 | uid 472 (image default) | `wget -qO- http://localhost:3000/api/health` | `prometheus: service_healthy` |

Ports bind to `127.0.0.1` so the demo laptop does not expose the DB, actuator or Grafana to the network. The backend image must copy `backend/` paths explicitly now that its context is the repo root; add a root `.dockerignore` (exclude `.env`, `node_modules`, `target`, `.git`, `demo/`).

### 2.2 Environment variables (names only; values live in `.env`, never committed)

`.env.example` holds **placeholders only** (e.g. `change-me`); `.env` is git-ignored (already in `.gitignore`).

| Service | Variables |
|---|---|
| `postgres` | `POSTGRES_DB` ← `DB_NAME`; `POSTGRES_USER` / `POSTGRES_PASSWORD` ← `DB_BOOTSTRAP_USERNAME` / `DB_BOOTSTRAP_PASSWORD` (superuser, used only by the init script); `DB_OWNER_PASSWORD`, `DB_APP_PASSWORD` (read by the init script); `TZ=UTC` |
| `backend` | `DB_URL`, `DB_USERNAME` (= `expenseflow_app`), `DB_PASSWORD` (= app password), `FLYWAY_USERNAME` (= `expenseflow_owner`), `FLYWAY_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `RECEIPTS_DIR`, `EXPORTS_DIR`, `TZ=UTC`, `JAVA_OPTS` |
| `seed` | `DB_HOST`, `DB_PORT_INTERNAL`, `DB_NAME`, `DB_OWNER_USERNAME` (= `expenseflow_owner`), `DB_OWNER_PASSWORD`, `SEED_RANDOM_SEED`, `SEED_DEMO_PASSWORD`, `RECEIPTS_DIR` |
| `grafana` | `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD`, `GF_AUTH_ANONYMOUS_ENABLED=false`, `GF_USERS_ALLOW_SIGN_UP=false` |
| compose | `DB_PORT`, `BACKEND_PORT`, `FRONTEND_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT` |

Password variables use `${VAR:?set in .env}` (fail fast) instead of the current `:-postgres` / `:-admin` defaults (gap G-01).

### 2.3 Volumes and mounts

| Name | Mounted in | Path | Purpose |
|---|---|---|---|
| `pgdata` | `postgres` | `/var/lib/postgresql` (correct for the PG 18 image layout) | Database files |
| `receipts` | `backend` (rw), `seed` (rw) | `${RECEIPTS_DIR}` e.g. `/data/receipts` | Receipt files, generated names (CLAUDE.md §8.1, §13.5) |
| `exports` | `backend` (rw) | `${EXPORTS_DIR}` e.g. `/data/exports` | Exported payout CSVs, stored once; re-downloads return the same bytes and SHA-256 (backbone §6, CLAUDE.md §2.8) |
| `prometheusdata` | `prometheus` | `/prometheus` | TSDB |
| `grafanadata` | `grafana` | `/var/lib/grafana` | Grafana state |
| bind `./ops/postgres/init` | `postgres` | `/docker-entrypoint-initdb.d:ro` | Creates the two roles on an empty volume (§2.4) |
| bind `./ops/prometheus` | `prometheus` | `/etc/prometheus:ro` | `prometheus.yml` + `alerts.yml` |
| bind `./ops/grafana/provisioning` | `grafana` | `/etc/grafana/provisioning:ro` | Datasource + dashboard provider |
| bind `./ops/grafana/dashboards` | `grafana` | `/var/lib/grafana/dashboards:ro` | Dashboard JSON |

`receipts` and `exports` must be writable by the non-root `app` user (create the directories with `app` ownership in the Dockerfile so new named volumes inherit it).

### 2.4 Database roles (backbone §6; NFR-004, §13.1)

| Account | Created by | Rights | Used by |
|---|---|---|---|
| bootstrap superuser (`DB_BOOTSTRAP_USERNAME`) | Postgres image | Superuser | `ops/postgres/init/01-roles.sh` only; never by an application |
| `expenseflow_owner` | init script (password from env, no literals) | Owns the database and schema; DDL; may `CREATE EXTENSION pgcrypto` (trusted extension) | Flyway (Spring Boot `spring.flyway.user/password`) and the `seed` service |
| `expenseflow_app` | init script | DML on business tables; **only `SELECT`, `INSERT` on `audit_events`**; no DDL | Backend runtime |

Grants live in a Flyway migration (`ALTER DEFAULT PRIVILEGES` + explicit `REVOKE UPDATE, DELETE, TRUNCATE ON audit_events`). A trigger additionally rejects `UPDATE`/`DELETE`/`TRUNCATE` on `audit_events` for **every** role (backbone §6). pytest checks: app user `UPDATE audit_events` → permission denied; owner `DELETE FROM audit_events` → rejected by trigger.

Trade-off noted: Flyway runs inside the backend, so the backend container holds owner credentials (see §11 I-10).

### 2.5 One-command seeded demo

| Step | Command / mechanism |
|---|---|
| One-time setup | `copy .env.example .env` (PowerShell) and fill values (OPS-Q-09) |
| **Start (the one command)** | `docker compose up -d --build` |
| Order | `postgres` healthy (init script creates roles on first start) → `backend` starts, Flyway migrates as `expenseflow_owner`, `ddl-auto=validate`, healthy → `seed` runs once → `frontend`, `prometheus`, `grafana` |
| Seed behaviour | Python + `psycopg`, connects as `expenseflow_owner`; deterministic data (`SEED_RANDOM_SEED`); password hashes via `pgcrypto` `crypt(..., gen_salt('bf'))` (BCrypt, readable by Spring Security — verify `$2a$` prefix compatibility in a test); writes DATA-001..005 and receipt files; **idempotent**: exits 0 without changes if seed data already exists |
| Check seed finished | `docker compose ps -a seed` shows `Exited (0)`; `docker compose logs seed` |
| Reset to a clean demo | `docker compose down -v` then `docker compose up -d --build` (drops `pgdata`, `receipts`, `exports`, Prometheus and Grafana data). This is the **only** reset path: the audit trigger blocks `TRUNCATE`/`DELETE` of `audit_events`, so in-place re-seeding is not offered |
| Stop | `docker compose down` (keeps data) |

Note: `docker compose up --wait` handles an exited one-shot container differently across Compose versions; CI uses `up -d --build --wait` for long-running services, then `docker compose run --rm seed` explicitly (§3.1).

### 2.6 Gap vs current `docker-compose.yml` and backend config

| ID | Current | Target |
|---|---|---|
| G-01 | Password defaults `postgres` / `admin` inline | `${VAR:?}` required; values only in `.env` |
| G-02 | Single DB user (`postgres` superuser) used by the app | Bootstrap + `expenseflow_owner` + `expenseflow_app` (§2.4) |
| G-03 | `build: ./backend` | Context `.` + `dockerfile: backend/Dockerfile`; `database/migrations/` as a Maven resource; root `.dockerignore` |
| G-04 | No `seed` service, no `receipts` / `exports` volumes | Add all three |
| G-05 | `prom/prometheus:latest`, `grafana/grafana:latest` | Exact tags (§8, OPS-Q-11) |
| G-06 | Frontend `depends_on: backend` without condition; nginx runs as root | `service_healthy`; non-root nginx (OPS-Q-07) |
| G-07 | Prometheus mounts only `prometheus.yml`; no rules, no TSDB volume | Mount `ops/prometheus/` dir; `prometheusdata` volume |
| G-08 | No dashboard provider or dashboard JSON | `ops/grafana/provisioning/dashboards/expenseflow.yml` + `ops/grafana/dashboards/expenseflow.json` |
| G-09 | Ports bound to all interfaces; Prometheus/Grafana ports hard-coded | `127.0.0.1` binding; env-driven ports |
| G-10 | No healthchecks on Prometheus/Grafana | Add (§2.1) |
| G-11 | `spring.jpa.hibernate.ddl-auto=update`; no Flyway dependency | `validate`; Flyway owns the schema (backbone §13) |
| G-12 | App health at `/api/health` (`HealthController`) | API-001 `/api/v1/health` (backbone §8); container check stays on `/actuator/health` |
| G-13 | No `percentiles-histogram` property, no `expenseflow_*` gauges | Added in Phase 4c (backend spec) |

## 3. CI (GitHub Actions, `.github/workflows/ci.yml`)

Triggers: `push` (all branches — NFR-007 says every push) and `pull_request`. Workflow-level `permissions: contents: read`. `concurrency` group per ref with `cancel-in-progress: true`. Every job has a `timeout-minutes`.

### 3.1 Jobs

| Job | Steps | Blocks merge? |
|---|---|---|
| `secrets-scan` | checkout (full history); fail if `.env` or any `.env.*` other than `.env.example` is tracked (`git ls-files`); run a secret scanner (gitleaks via its Docker image, OPS-Q-06) | Yes |
| `backend` | setup-java Temurin 21 (`cache: maven`); `./mvnw -B verify` in `backend/` (JUnit 5, incl. policy and chain engines; linter if adopted in ADR-000); upload surefire reports on failure | Yes |
| `backend-deps` | setup-java; restore NVD data cache; `./mvnw -B org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=<PROPOSED 7, OPS-Q-05>` with `NVD_API_KEY` from GitHub secrets; upload HTML report | Yes |
| `frontend` | setup-node 26.13.1 (`cache: npm`); `npm ci`; `npm run lint`; `npm test -- --run` (Vitest, once added); `npm run build` | Yes |
| `frontend-deps` | `npm ci`; `npm audit --audit-level=<PROPOSED high, OPS-Q-05>` | Yes |
| `api-tests` | needs `backend`, `frontend`; `cp .env.example .env`; `docker compose up -d --build --wait postgres backend`; `docker compose run --rm seed`; setup-python 3.13 (`cache: pip`); `pip install -r tests/requirements.txt`; `pytest tests/backend tests/integration --junitxml=...` (incl. DB-permission, audit-trigger and `/actuator/prometheus` metric-name checks); on failure upload `docker compose logs`; always `docker compose down -v` | Yes |
| `e2e` | needs `api-tests`; full `docker compose up -d --build`; `npx playwright install --with-deps chromium`; `npx playwright test`; upload `playwright-report/` (also the source of page timings if SPEC-Q-12 keeps that default) | PROPOSED: non-blocking until the 10 E2E cases are stable (OPS-Q-08) |
| `docker` | needs `backend`, `frontend`; `docker compose build` (also covered by `api-tests`; keep as a fast check or drop) | Yes |

### 3.2 Caching

| Cache | Key |
|---|---|
| Maven `~/.m2/repository` | `setup-java cache: maven` (hash of `pom.xml`) |
| OWASP NVD data | `actions/cache` on the dependency-check data dir, key by week |
| npm | `setup-node cache: npm`, `cache-dependency-path: frontend/package-lock.json` |
| pip | `setup-python cache: pip`, `tests/requirements.txt` |
| Docker layers | optional buildx + GHA cache; skip if it complicates the compose build |
| Playwright browsers | `~/.cache/ms-playwright` keyed by Playwright version |

### 3.3 Security of CI

- Actions pinned to a major tag at minimum (`@v4`); pin to commit SHA if the team agrees.
- Only one repository secret: `NVD_API_KEY`. CI DB passwords come from `.env.example` placeholders — never real values.
- Merge blocking is enforced by **branch protection on `main`** requiring the "Yes" jobs above (GitHub setting, done by the team).

### 3.4 Gap vs current `ci.yml`

| Current | Missing |
|---|---|
| `backend`: `mvnw verify` | OK. Add surefire report upload. Tests run on H2 (backbone §13 note); pytest on the compose stack covers Postgres behaviour and permissions |
| `frontend`: `npm ci`, lint (oxlint), build | No test step (Vitest not yet in `package.json`); oxlint vs ESLint + Prettier is an ADR-000 decision (backbone §13) |
| `docker`: `docker compose build` | OK; must still work after the build-context change (G-03) |
| — | `secrets-scan`, `backend-deps` (OWASP), `frontend-deps` (npm audit), `api-tests` (pytest), `e2e`, `permissions`, `concurrency`, timeouts |
| `push` only on `main`/`master` | NFR-007 says every push: widen to all branches |

## 4. Metrics

### 4.1 Metric catalogue (names and labels exactly as backbone §10)

| Metric | Type | Labels | Meaning | Source in backend (Phase 4c) |
|---|---|---|---|---|
| `expenseflow_approval_queue_size` | Gauge | `step` = `MANAGER`, `FINANCE_AUDITOR`, `FINANCE_HEAD`, `SAMPLE_AUDIT`; plus global `application` | Claims waiting at each step; **0 when empty** (all four series always present) | Micrometer gauges registered for all four values at startup; values refreshed by a scheduled query (PROPOSED every 15 s = scrape interval) on `PENDING` `approval_steps` by step type, and `SAMPLE_AUDIT_PENDING` claims |
| `expenseflow_oldest_pending_approval_seconds` | Gauge | same `step` values | Now − oldest `approval_steps.activated_at` of a `PENDING` step; for `SAMPLE_AUDIT`, now − sample-audit selection time; **0 when empty** | Same component |
| `http_server_requests_seconds` (`_count`, `_sum`, `_max`, `_bucket`) | Histogram | Spring defaults (`method`, `uri`, `status`, `outcome`, `exception`) + `application` | API latency | Actuator auto-instrumentation with `percentiles-histogram` enabled (backbone §10) |
| `up` | Gauge (Prometheus) | `job`, `instance` | Scrape success of the backend | Prometheus |

Delegated manager steps count under `MANAGER`. **Page latency** (NFR-008) is open as SPEC-Q-12; until answered, the dashboard shows API latency and page timings come from Playwright/Locust reports (not Prometheus series).

### 4.2 Prometheus config (`ops/prometheus/prometheus.yml`)

| Item | Value |
|---|---|
| `global.scrape_interval` / `evaluation_interval` | 15s (current value; PROPOSED, fine for demo) |
| `rule_files` | `/etc/prometheus/alerts.yml` |
| Job `backend` | `metrics_path: /actuator/prometheus`, target `backend:8080` (unchanged; backbone §8) |
| Job `prometheus` | self-scrape `localhost:9090` (optional) |
| Alertmanager | none (§6.3) |

Recording rules: **none**. Queries are cheap at demo scale; recording-rule names would be new shared names (backbone rule).

## 5. Grafana dashboard

Provisioned, never hand-built: datasource `ops/grafana/provisioning/datasources/prometheus.yml` (exists; add `uid: prometheus`), provider `ops/grafana/provisioning/dashboards/expenseflow.yml` (folder "ExpenseFlow", `allowUiUpdates: false`), JSON `ops/grafana/dashboards/expenseflow.json`, title **ExpenseFlow — Approvals and Latency**, refresh 30s, default range last 6h, variable `$step` (`label_values(expenseflow_approval_queue_size, step)`, multi, include All).

| Row | Panel | Type | PromQL |
|---|---|---|---|
| 1 Status | Pending approvals (total) | Stat | `sum(expenseflow_approval_queue_size)` |
| 1 | Oldest pending approval | Stat (unit: duration s; colour at `<OLDEST_PENDING_THRESHOLD_SECONDS>`) | `max(expenseflow_oldest_pending_approval_seconds)` |
| 1 | Backend up | Stat (1 = Up, 0 = Down) | `up{job="backend"}` |
| 1 | Firing alerts | Stat | `count(ALERTS{alertstate="firing"}) or vector(0)` |
| 2 Approvals | Queue size by step | Time series | `sum by (step) (expenseflow_approval_queue_size{step=~"$step"})` |
| 2 | Oldest pending by step | Time series + threshold line | `max by (step) (expenseflow_oldest_pending_approval_seconds{step=~"$step"})` |
| 3 Latency | API p95 latency | Time series (s) | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="backend", uri=~"/api/.*"}[5m])))` |
| 3 | API p95 by endpoint (top 10) | Table / bar | `topk(10, histogram_quantile(0.95, sum by (le, method, uri) (rate(http_server_requests_seconds_bucket{uri=~"/api/.*"}[5m]))))` |
| 3 | Mean latency | Time series | `sum(rate(http_server_requests_seconds_sum{uri=~"/api/.*"}[5m])) / sum(rate(http_server_requests_seconds_count{uri=~"/api/.*"}[5m]))` |
| 4 Traffic | Requests by status | Time series | `sum by (status) (rate(http_server_requests_seconds_count{uri=~"/api/.*"}[5m]))` |
| 4 | 5xx ratio | Time series | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` |

Layout: 24-column grid; row 1 four stats (w=6, h=4); rows 2–4 two panels each (w=12, h=8). Actuator paths are excluded by the `uri=~"/api/.*"` filter.

## 6. Alerts (`ops/prometheus/alerts.yml`)

### 6.1 Oldest pending approval (the required alert)

| Field | Value |
|---|---|
| Group / name | `expenseflow-approvals` / `ExpenseFlowOldestPendingApprovalHigh` |
| Expression | `max by (step) (expenseflow_oldest_pending_approval_seconds) > <OLDEST_PENDING_THRESHOLD_SECONDS>` |
| Threshold | **PROPOSED, not final:** 172800 s (48 h, calendar time) as a starting point; team validates (§2.4, OPS-Q-01). Per-step values possible later (e.g. `SAMPLE_AUDIT` may warrant its own) |
| `for` | **PROPOSED:** 5m |
| Labels | `severity: warning`, `team: expenseflow`, `step` (from expression) |
| Annotations | `summary: Oldest pending {{ $labels.step }} approval is {{ $value \| humanizeDuration }} old`; `description: Approvals at step {{ $labels.step }} are waiting longer than the agreed threshold.`; `runbook: ops/runbook.md#alert-oldest-pending-approval` |

Because the gauge is 0 when a queue is empty, the alert resolves cleanly and never goes blind on an empty queue. Demo note: seeded historical claims may leave old pending steps, so this alert can fire right after seeding (OPS-Q-10).

### 6.2 Supporting alerts

| Name | Expression | `for` (PROPOSED) | Severity |
|---|---|---|---|
| `ExpenseFlowBackendDown` | `up{job="backend"} == 0` | 1m | critical |
| `ExpenseFlowApprovalMetricsMissing` | `absent(expenseflow_oldest_pending_approval_seconds)` | 5m | warning |

With series always present (backbone §10), `ExpenseFlowApprovalMetricsMissing` only fires if the metrics component is broken or not registered.

### 6.3 Delivery

Alerts are evaluated by Prometheus and seen in the Prometheus UI (`/alerts`) and the Grafana "Firing alerts" panel. **No Alertmanager, e-mail or paging** for the hackathon (none is required by the source). Validation: `promtool check rules ops/prometheus/alerts.yml` (via the Prometheus image, locally or in CI).

## 7. Runbook outline (`ops/runbook.md`, written in Phase 8)

| Section | Content |
|---|---|
| 1 Overview | Services, ports, URLs (§2.1), volumes (§2.3), who owns what |
| 2 Prerequisites | Docker Desktop running; `.env` created from `.env.example`; ports free |
| 3 Start / stop / reset | `docker compose up -d --build`; `docker compose down`; reset only via `docker compose down -v` + up (§2.5) |
| 4 Health checks | `docker compose ps` (all healthy, `seed` Exited 0); container liveness `GET :8080/actuator/health`; app + DB status `GET /api/v1/health` (API-001); scrape `GET :8080/actuator/prometheus` lists both `expenseflow_*` metrics with 4 `step` values; Prometheus `/targets` backend UP; Grafana dashboard loads |
| 5 Alert: oldest pending approval | Dashboard row 2 → which `step`. `MANAGER`: is the manager on leave without a delegate? set a delegation (CL-13). `FINANCE_AUDITOR` / `FINANCE_HEAD` / `SAMPLE_AUDIT`: pool steps — remind the pool via the review queue / approvals list. Check the claim's `approval_steps` and audit trail (`STEP_ACTIVATED`, `STEP_SKIPPED`) for a routing fault. Record outcome |
| 6 Alert: backend down | `docker compose ps backend`; `docker compose logs --tail=200 backend`; check DB health, credentials, Flyway errors; `docker compose restart backend` |
| 7 Alert: metrics missing | Backend up but gauge absent → check `/actuator/prometheus`, metrics component logs, scheduled refresh |
| 8 Common failures | Table below |
| 9 Logs | `docker compose logs -f <service>`; backend logs are structured to stdout; no log files inside containers |
| 10 Trace IDs | Every response carries `X-Trace-Id`; error bodies carry `traceId` (backbone §8, §9); search `docker compose logs backend \| Select-String <traceId>`; the same ID is stored on `audit_events` |
| 11 Escalation | Team contact list (`<fill in>`) |

| Common failure | Symptom | Fix |
|---|---|---|
| Port 5432 conflict with a local PostgreSQL | Wrong DB reached / connection refused when running backend from the IDE | Compose maps Postgres to host **5433**; local runs use `localhost:5433`; never install local Postgres (§8.1) |
| Postgres 18 rejects Windows timezone `Asia/Calcutta` | Backend run on Windows fails on connect with an invalid time zone error | Already handled: `BackendApplication` maps `Asia/Calcutta` → `Asia/Kolkata`; containers use `TZ=UTC` |
| Maven download hiccup during `docker build` | `dependency:go-offline` fails with network/timeouts | Re-run `docker compose build backend`; check proxy; layer cache keeps finished downloads |
| Port 3000/3001/8080/9090 in use | Container fails to start | Change `*_PORT` in `.env` |
| Roles missing | Backend/Flyway auth fails for `expenseflow_owner` / `expenseflow_app` | Init scripts run only on an empty `pgdata`; full reset (`down -v`) |
| Flyway validate / checksum error, or `ddl-auto=validate` mismatch | Backend unhealthy at start | Fix the migration; full reset in demo environments only |
| Seed exits non-zero | `seed` shows `Exited (1)` | `docker compose logs seed`; usually backend not migrated, owner credentials wrong, or `pgcrypto` not created by migrations; fix, then full reset |
| Line endings | `mvnw` "not found" in build | `.gitattributes` keeps `mvnw` LF; Dockerfile also strips CR |

## 8. Security for ops

| Control | Spec |
|---|---|
| Secrets | Only via env/`.env`; `.env.example` placeholders only; `${VAR:?}` for passwords; CI `secrets-scan`; root `.dockerignore` excludes `.env`; team confirms no secrets committed (§2.4) |
| Container users | backend `app` (done); seed non-root; nginx non-root (OPS-Q-07); Prometheus/Grafana/Postgres image defaults are non-root at runtime |
| DB least privilege | Bootstrap superuser used only by init; `expenseflow_owner` for Flyway + seed; `expenseflow_app` runtime with `SELECT`/`INSERT` only on `audit_events`; trigger blocks audit `UPDATE`/`DELETE`/`TRUNCATE` for all roles (NFR-004, B-11) |
| Network exposure | Host ports on `127.0.0.1`; nginx proxies only `/api/`, so actuator is not reachable via the frontend; actuator exposure stays `health,info,prometheus`; health details hidden |
| Grafana | Admin from env; anonymous and sign-up disabled; no DB datasource (reads Prometheus only) |
| Dependency scan | OWASP Dependency-Check (Maven) + `npm audit` in CI (NFR-007, §13.10) |
| Image pinning (§8.2 rule 5) | `postgres:18.<x>`, `eclipse-temurin:21-jdk`/`21-jre` (exact patch tag), `node:26.13.1-alpine` (exact), `nginx:<exact>`, `python:3.13.<x>-slim`, `prom/prometheus:<exact>`, `grafana/grafana:<exact>`. Exact tags chosen once and recorded in ADR-000 (backbone §13, OPS-Q-11); no `latest` |
| CI token | `permissions: contents: read` |

## 9. Ordered task list

| # | Phase | Task | Done-check |
|---|---|---|---|
| — | 4c (backend) | Metrics component + `percentiles-histogram` (backbone §10) — prerequisite for tasks 11–14 | `/actuator/prometheus` shows both `expenseflow_*` names with all 4 `step` values, 0 on an empty DB |
| 1 | 7 | Record exact image tags in ADR-000; replace `latest` | `grep -n latest docker-compose.yml` returns nothing |
| 2 | 7 | `.env.example` with all §2.2 names (placeholders); `${VAR:?}` for passwords | `docker compose config` fails without `.env`, succeeds with it; `git ls-files .env` empty |
| 3 | 7 | Init script for `expenseflow_owner` / `expenseflow_app`; grants + audit trigger migration | pytest: app `UPDATE audit_events` denied; owner `DELETE FROM audit_events` rejected |
| 4 | 7 | Backend build context → repo root, migrations as Maven resource, `.dockerignore`, `ddl-auto=validate` | Fresh `up`: schema created by Flyway only; backend healthy |
| 5 | 7 | `receipts` and `exports` volumes writable by `app` | Uploaded receipt and exported CSV survive `docker compose restart backend`; re-download SHA-256 unchanged |
| 6 | 7 | `seed` service (`psycopg`, `pgcrypto` bcrypt, deterministic, idempotent) | Fresh `up` → seed Exited 0; 150 users, 8 departments, 3,000 claims; a seeded user can log in; second `up` changes nothing |
| 7 | 7 | Non-root nginx; `service_healthy` dependencies; 127.0.0.1 ports | `docker compose exec frontend id -u` ≠ 0; all services healthy |
| 8 | 7 | CI jobs §3.1 incl. OWASP, npm audit, pytest-on-compose, secrets scan | Green run on a push; a planted fake secret on a branch fails `secrets-scan` |
| 9 | 7 | Branch protection on `main` | PR cannot merge with a failing required job |
| 10 | 8 | Prometheus rules + mounts | `promtool check rules` passes; `/alerts` lists 3 rules; `/targets` backend UP |
| 11 | 8 | Grafana provider + dashboard JSON | Fresh reset → dashboard present, all panels show data |
| 12 | 8 | Threshold workshop with team | OPS-Q-01 answered in CLAUDE.md §6; `alerts.yml` updated; PROPOSED tag removed |
| 13 | 8 | Fire-drill | Stop backend → `ExpenseFlowBackendDown` fires; claim pending past a lowered test threshold → oldest-pending alert fires; screenshot to `demo/screenshots/` |
| 14 | 8 | Write `ops/runbook.md` (§7) | Another team member resets and checks the demo using only the runbook |
| 15 | 7–8 | Update `PROGRESS.md`, README setup section, traceability for NFR-006/007/008 | Rows filled with evidence |

## 10. Open questions (ops)

To be copied into `CLAUDE.md` §6 / backbone §11 when accepted.

| ID | Question | Proposed default |
|---|---|---|
| OPS-Q-01 | Oldest-pending threshold and `for` duration; one value or per step? | 48 h, 5m, single value (team validates) |
| ~~OPS-Q-02~~ | ~~Age reference point~~ | **Resolved** by backbone v0.2 §10 (`activated_at`) |
| ~~OPS-Q-03~~ | ~~Does sample audit count?~~ | **Resolved** by backbone v0.2 §10 (`SAMPLE_AUDIT`) |
| ~~OPS-Q-04~~ | ~~Page latency meaning~~ | Moved to backbone **SPEC-Q-12** |
| OPS-Q-05 | Dependency-check fail levels | OWASP CVSS ≥ 7; `npm audit --audit-level=high` |
| OPS-Q-06 | Approve gitleaks (Docker image) as the secret scanner (new tool, §7.4); who holds `NVD_API_KEY`? | gitleaks; repo admin |
| OPS-Q-07 | Non-root nginx: `nginx-unprivileged` image (image change) or non-root user + port 8080 in the current image? | Config change in the current image |
| OPS-Q-08 | Is the Playwright E2E job merge-blocking? | Non-blocking until stable |
| OPS-Q-09 | Does "one command" allow a one-time `.env` copy first, or must compose run with safe demo defaults? | One-time `.env` copy, documented |
| OPS-Q-10 | Should seeded data leave pending claims old enough to fire the alert during the demo? | Yes, a few, to show the alert |
| OPS-Q-11 | Exact image tags for Postgres 18.x, Prometheus, Grafana, nginx, Python 3.13, Temurin 21 | Team picks current stable at event start; ADR-000 |

Backbone questions affecting ops: SPEC-Q-12 (page latency — dashboard scope); SPEC-Q-06 (receipt size limit — nginx `client_max_body_size` must be ≥ the limit); SPEC-Q-13 (rate limits — nginx passes `X-Forwarded-For`, so per-IP limits must read it from the trusted proxy only).

## 11. Issues found in `architecture/SPEC.md`

### 11.1 Raised against v0.1

| ID | Issue | Status in v0.2 |
|---|---|---|
| I-01 | `http_server_requests_seconds` is API latency, not page latency; histogram not enabled | **Partly resolved:** histogram enabled; page latency open as SPEC-Q-12 |
| I-02 | `step` label values undefined; sample audit unclear | **Resolved** (§10: 4 values incl. `SAMPLE_AUDIT`) |
| I-03 | No pending-since timestamp | **Resolved** (`approval_steps.activated_at`, §5.3, §6) |
| I-04 | Empty-queue value undefined | **Resolved** (0, series always present) |
| I-05 | Health endpoints' roles unclear | **Resolved** (§8 Health row); code path rename is gap G-12 |
| I-06 | No phase owns the metrics code | **Resolved** (Phase 4c, §10, §14) |
| I-07 | No shared DB role names | **Resolved** (`expenseflow_owner` / `expenseflow_app` + trigger, §6) |
| I-08 | Seed bcrypt dependency and order vs Flyway | **Resolved** per coordinator (psycopg + pgcrypto, after backend healthy); see I-11 |
| I-09 | Migrations unreachable from `./backend` build context; `ddl-auto=update` | **Resolved** (§13 note: repo-root context, Maven resource, `validate`; ADR-000) |

### 11.2 Remaining or new in v0.2

| ID | Section | Issue | Suggested fix |
|---|---|---|---|
| I-10 | §6 roles | `expenseflow_owner` runs Flyway, and Flyway runs inside the backend, so the runtime container holds DDL credentials alongside the app user's — weaker least privilege (§13.1) | Accept for the hackathon and note it in ADR-000, or run migrations in a one-shot container (extra image, needs approval) |
| I-11 | §13 notes | Only `psycopg` is named for the seed; `pgcrypto` (BCrypt via `crypt`/`gen_salt('bf')`) is not, and it needs a migration that creates the extension. Spring's BCrypt must accept pgcrypto's `$2a$` hashes | Add `pgcrypto` to the §13 ADR-000 list and to `database/SPEC.md` |
| I-12 | §10 metrics | `SAMPLE_AUDIT` age uses "sample-audit selection time", but §6 lists only unnamed "sample-audit fields" on `claims` | Name the column (e.g. a selection timestamp) in §6 |
| I-13 | §10, SPEC-Q-12 | The proposed default takes page timings from Playwright/Locust runs; those are test reports, not Prometheus series, so NFR-008's "dashboard" would not show page latency | State that the dashboard covers API latency only, or add a frontend metric if the owner wants it on the dashboard |
| I-14 | §6 audit trigger | The trigger blocks `TRUNCATE` for every role, so any in-place demo reset is impossible; only `docker compose down -v` resets | Acknowledge in backbone/database spec (this spec already reflects it) |
| I-15 | §13 notes | "Pinned Prometheus/Grafana image tags" are required but no tags are named; the same applies to Postgres 18.x, nginx, Python 3.13 and Temurin patch tags | Record exact tags in ADR-000 (OPS-Q-11) |
