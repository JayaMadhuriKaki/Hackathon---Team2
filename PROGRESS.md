# PROGRESS — ExpenseFlow

Updated at the end of every task (CLAUDE.md §15). Report actual results only.

| Field | Value |
|---|---|
| Last updated | 2026-11-27 |
| Current phase | 2 — Architecture (requirement baseline approved) |
| Approach | Spec-driven: specs are reviewed and approved before any feature code |

## Phase status

| # | Phase | Status | Notes |
|---|---|---|---|
| 0 | Setup | In progress | Repo matches the Drive structure; environment check done (below); specs drafted |
| 1 | Requirement analysis | Done | Analysis v1.1 approved by the project owner on 2026-11-27 (CL-19): 32 FR, 21 NFR, 31 BR, 46 user stories, 106 acceptance criteria; `use-cases.docx` v1.0 written. 6 critical open questions remain (`requirements/open-questions.md`) |
| 2 | Architecture | In progress | `architecture/SPEC.md` drafted; ADR-000, ADR-001, diagrams and `security-review.md` not started |
| 3 | API design | Not started | API catalogue API-001..API-036 fixed in `architecture/SPEC.md`; `api-contracts.yaml` still the template placeholder |
| 4a | Policy engine (test-first) | Not started | |
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
| Requirement analysis v1.1 approved; workbooks `user-stories.xlsx` and `requirement-traceability.xlsx` aligned; SPEC open questions merged into `requirements/open-questions.md` | All FR/NFR/BR | (this commit) |
| CLAUDE.md updated from Drive (stack approved, CL-12..CL-18) | CL-17, CL-18 | 604bdcc |

## Tests run

| Date | Command | Result |
|---|---|---|
| 2026-11-27 | `backend: .\mvnw.cmd test` | 2 passed, 0 failed |
| 2026-11-27 | `frontend: npm run lint; npm run build` | Lint clean; build succeeded |
| 2026-11-27 | `docker compose up -d --build` + health checks | All 5 services up; frontend → backend → database health UP; Prometheus target up |

## Open blockers

Single list: `requirements/open-questions.md` (OQ IDs; SPEC-Q IDs mapped there).

| ID | Blocker | Blocks |
|---|---|---|
| OQ-028 | Line above 500 INR without receipt: block submission (ASM-003, spec default) or auto-reject? | POL-005 / BR-005 |
| OQ-029 (SPEC-Q-05) | Scope of duplicate checks | BR-006, BR-007, BR-009 |
| OQ-030 | Duplicate consequence: route with flag (ASM-004, spec default) or auto-reject? | BR-006, BR-007, BR-009 |
| OQ-032 (SPEC-Q-02) | Expense categories beyond meals, hotel, local cab | Line items, policy engine, UI |
| OQ-046 (SPEC-Q-14) | Finance head's clean claim ≤ 25,000: auditor or auto-approve? | Routing |
| OQ-047 (SPEC-Q-16) | May one finance head approve two levels of an auditor's claim? | Chain engine |
| OQ-034 (SPEC-Q-03) | Is a comment mandatory on send-back? (team treats as blocking) | API-017, UI |
| — | Specs need team review against the approved baseline (CL-19) before code (CLAUDE.md §14.2) | All build phases |

Resolved on approval of the analysis (CL-20): OQ-036 payout CSV format, OQ-037 auto-approve ceiling, OQ-038 limit-breach outcome, OQ-039 delegate roles.

## Next step

Project owner answers the blocking questions; team reviews the six specs against the approved requirement baseline (CL-19), then: ADR-000 and ADR-001 (Phase 2), `api-contracts.yaml` (Phase 3), JUnit policy tests before the policy engine (Phase 4a).
