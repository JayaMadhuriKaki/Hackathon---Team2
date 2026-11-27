# PROGRESS — ExpenseFlow

Updated at the end of every task (CLAUDE.md §15). Report actual results only.

| Field | Value |
|---|---|
| Last updated | 2026-11-27 |
| Current phase | 0 — Setup (spec-driven planning in progress) |
| Approach | Spec-driven: specs are reviewed and approved before any feature code |

## Phase status

| # | Phase | Status | Notes |
|---|---|---|---|
| 0 | Setup | In progress | Repo matches the Drive structure; environment check done (below); specs drafted |
| 1 | Requirement analysis | Not started | `requirements/requirement-analysis.md` and `use-cases.docx` are still templates |
| 2 | Architecture | In progress | `architecture/SPEC.md` v0.3; ADR-000 (stack; Proposed items P-01..P-11 await confirmation) and ADR-001 (approval chain) written; diagrams and `security-review.md` not started |
| 3 | API design | Not started | API catalogue API-001..API-036 fixed in `architecture/SPEC.md`; `api-contracts.yaml` still the template placeholder |
| 4a | Policy engine (test-first) | Done | Base fixes (API-001 path, error shape, trace ID); 9 rules + `PolicyEngine` + `RoutingDecider`, tests written and confirmed failing first |
| 4b | Approval chain + delegation | Not started | |
| 4c | Remaining backend | Not started | Base Spring Boot app exists (health endpoint only) |
| 5 | Frontend | Not started | Base React app exists (home page + health card only) |
| 6 | Validation | Not started | |
| 7 | Deployment | In progress | Docker Compose stack and CI (tests + build) exist from the base setup; dependency check and seed not done |
| 8 | Support (SRE) | Not started | Prometheus + Grafana run and scrape the backend; no custom metrics, dashboard, alert or runbook yet |
| 9 | Demo | Not started | |

## Specifications

| Spec | Status |
|---|---|
| `architecture/SPEC.md` — backbone: roles, states, transitions, policy and chain engines, tables, API catalogue (API-001..038), conventions, error codes, open questions | Draft v0.3, awaiting team review |
| `backend/SPEC.md` — packages, engines, per-API behaviour, cross-cutting, test-first task list | Draft v0.2, awaiting team review |
| `frontend/SPEC.md` — routes, 13 screens, components, states, tasks | Draft v0.2, awaiting team review |
| `database/SPEC.md` — tables, constraints, indexes, audit enforcement, Flyway plan, exact seed counts | Draft v0.2, awaiting team review |
| `tests/SPEC.md` — strategy, policy and routing matrices, API coverage, 10 E2E cases, security checklist | Draft v0.2, awaiting team review |
| `ops/SPEC.md` — Docker Compose target, CI, metrics, dashboard, alerts, runbook outline | Draft v0.2, awaiting team review |

Backbone v0.3 §15 decisions postdate the child specs; where a child spec differs, the backbone wins.

## Environment check (CLAUDE.md §8.3) — 2026-11-27, Jaya's laptop

| Check | Result | Status |
|---|---|---|
| `git --version` | 2.47.1.windows.1 | Pass |
| `java -version` / `JAVA_HOME` | OpenJDK 21.0.13.1 (Temurin); JAVA_HOME points to jdk-21 | Pass |
| `py -3.13 --version` | Python 3.13.16 | Pass — note plain `python` is 3.15.2, so always use `py -3.13` |
| `.venv` with pytest, httpx, faker | Not created yet | Pending |
| `node -v` / `npm -v` | v26.13.1 / 11.20.0 | Pass |
| Vite React build | `npm run build` in `frontend/` succeeds | Pass |
| Playwright Chromium install | Not run yet | Pending |
| `docker` | 29.9.3 | Pass |
| PostgreSQL 18 in Docker | PostgreSQL 18.7 (compose service `postgres`, host port 5433) | Pass |

## Completed

| Item | Requirement IDs | Commit |
|---|---|---|
| Base monorepo: Spring Boot 4.1.3 backend, React 19 frontend, Docker Compose with Postgres 18, Prometheus, Grafana, CI | NFR-006 (partial), NFR-007 (partial) | eca0cfe |
| Repo aligned with the Drive `hackathon-project` structure | — | cd00682 |
| CLAUDE.md updated from Drive (stack approved, CL-12..CL-18) | CL-17, CL-18 | 604bdcc |
| Specs for all folders (backbone v0.3) | — | fe7ee73 |
| ADR-000, ADR-001; SPEC-Q-02/03/05/14/16 answered | CL-17, FR-005 | 27070ff |
| Health at `/api/v1/health`, `{code,message,details,traceId}` errors incl. 405, `X-Trace-Id` | API-001, §11.4 | 8248eaa |
| Failing policy tests written first | POL-001..009 | a465f32 |
| Policy rules + engine | FR-004, POL-001..009, CL-01/05/07/08 | 109ab2c |
| Routing decider (breach → reject; > 25,000 / weekend / duplicate / finance head → route; else auto-approve) | CL-02, CL-03, CL-07, CL-16, SPEC-Q-14 | see git log |

## Tests run

| Date | Command | Result |
|---|---|---|
| 2026-11-27 | `backend: .\mvnw.cmd test` | 2 passed, 0 failed |
| 2026-11-27 | `frontend: npm run lint; npm run build` | Lint clean; build succeeded |
| 2026-11-27 | `docker compose up -d --build` + health checks | All 5 services up; frontend → backend → database health UP; Prometheus target up |
| 2026-11-27 | `backend: .\mvnw.cmd test` (policy tests before implementation) | Policy tests failed as intended (not implemented); other 6 passed |
| 2026-11-27 | `backend: .\mvnw.cmd -Dtest=RoutingDeciderTest test` (before implementation) | 9 run, 9 failed as intended |
| 2026-11-27 | `backend: .\mvnw.cmd test` | **61 run, 0 failed** (46 policy rule cases, 9 routing, 4 health/error shape, 1 purity, 1 context) |
| 2026-11-27 | `frontend: npm run lint; npm run build` | Lint clean; build succeeded |

## Open blockers

| ID | Blocker | Blocks |
|---|---|---|
| — | Each build task needs its plan approved before code (CLAUDE.md §14.2) | Current task |

All business-critical spec questions are answered (2026-11-27, Jaya): SPEC-Q-02 only three categories; SPEC-Q-03 send-back comment mandatory; SPEC-Q-05 mixed duplicate scope; SPEC-Q-14 finance head claims always go to an auditor; SPEC-Q-16 one finance head may approve both steps. SPEC-Q-01 resolved in backbone v0.2. These answers still need adding to `CLAUDE.md` §6 on Drive by the owner. Non-critical open items with proposed defaults (`architecture/SPEC.md` §11–§12): SPEC-Q-04, SPEC-Q-06..13, SPEC-Q-15, ASM-S-01..06, Q-06, Q-13, Q-20.

## Next step

Phase 4b: approval-chain engine, current-step authorisation, delegation rules and sample-audit selector — pure modules, JUnit tests first (backend/SPEC.md tasks 6–8). In parallel the team confirms ADR-000 Proposed items P-02, P-03, P-04 and P-06, which task 9 (Flyway, entities, security) needs.
