# Architecture Decisions — ExpenseFlow

| Field | Value |
|---|---|
| Version | 1.0 — **proposed, for review and approval** |
| Date | 2026-11-27 |
| Format | Lightweight ADRs: context, options, decision, consequences, status, requirements |
| Status values | **Accepted** = already approved by the owner (CL-xx) · **Proposed** = needs approval · **Open** = needs an owner answer first |

## 1. Decision index

| ADR | Title | Status |
|---|---|---|
| ADR-000 | Technology stack | Accepted for the core stack (CL-17, CL-18); additions Proposed |
| ADR-001 | Approval chain modelled as data | Proposed |
| ADR-002 | Same-person step coverage | Accepted rule (CL-26, CL-27); delegate case **Open** |
| ADR-003 | Error response format | Proposed |
| ADR-004 | Authentication and session | Proposed |
| ADR-005 | Schema migrations and database roles | Proposed |
| ADR-006 | Primary key type | Proposed |
| ADR-007 | Policy as versioned configuration | Proposed |
| ADR-008 | Test strategy and test database | Proposed |
| ADR-009 | Frontend tooling | Proposed |
| ADR-010 | Deployment and CI hardening | Proposed |
| ADR-011 | Idempotency and concurrency | Proposed |
| ADR-012 | Package structure and pure engines | Proposed |
| ADR-013 | File storage for receipts and exports | Proposed |

---

## ADR-000 — Technology stack
- **Context:** The owner approved the stack (CL-17, CL-18); the repository already uses it.
- **Decision:** React 19 + Vite, Spring Boot 4.1.x on JDK 21, PostgreSQL 18 in Docker, JUnit 5 + pytest, Docker Compose, Prometheus + Grafana. Keep libraries already in the repository (React Router, Lombok, oxlint, Micrometer). Additions only through the ADRs below (`technology-stack.md` §2).
- **Consequences:** No technology replacement. Each addition is small and standard for this stack.
- **Status:** Core **Accepted**; additions **Proposed**. **Requirements:** NFR-014.

## ADR-001 — Approval chain modelled as data
- **Context:** The source asks for an ADR on how approval chains are modelled; routing must be auditable and not bypassable.
- **Options:** (a) hard-coded `if` logic per amount; (b) chain computed as ordered step rows at submit.
- **Decision:** (b). A pure `ApprovalChainEngine` computes levels from configured thresholds (manager always; auditor above 25,000; finance head above 1,00,000), applies the finance-head rule (CL-16, only when routed — CL-25), skips steps with no approver (CL-09), and stores `approval_steps` for the submission. Manager steps are individual (assigned manager or delegate); auditor and finance-head steps are pools. Resubmission marks old steps `SUPERSEDED` and restarts (CL-12). Config changes never re-route a claim in flight.
- **Consequences:** Every routing decision is visible in data and the audit trail; unit-testable without a database. Slightly more tables than (a).
- **Status:** Proposed. **Requirements:** FR-005, BR-013–BR-019, BR-033.

## ADR-002 — Same-person step coverage
- **Context:** CL-26/CL-27: when one person is the approver for more than one step, one approval covers all of their steps.
- **Decision:** After a user approves step *n*, each following step the same user could approve **in their own right** (assigned manager or holder of the pool role), and that is not their own claim, is set `APPROVED` with `covered_by_step_id = n` and audit action `STEP_COVERED`. Coverage stops at the first step they cannot approve.
- **Open question (UD-01):** Delegates are always finance auditors or finance heads (CL-13). If approving **as a delegate** also counted, every delegated manager approval on a claim above 25,000 INR would automatically cover the auditor step — the claim would get one review instead of two. **Recommendation: coverage applies only to steps held in the person's own right, never through delegation.**
- **Consequences:** Fewer clicks; reduced separation of duties in same-person cases (accepted by the owner). Coverage is visible in the UI and the audit trail.
- **Status:** Rule **Accepted**; delegate case **Open**. **Requirements:** BR-033, CL-26, CL-27.

## ADR-003 — Error response format
- **Context:** Existing code returns RFC 9457 problem details (`status`, `title`, `detail`); CLAUDE.md §11.4 and the team spec require `code`, `message`, `details`, `traceId`. The frontend client reads `detail`.
- **Options:** (a) replace with a plain `{code, message, details, traceId}` body; (b) keep RFC 9457 (`application/problem+json`) and add `code`, `message`, `details`, `traceId` as extension members.
- **Decision:** (b). Keeps the existing `GlobalExceptionHandler` and Spring's built-in support, and satisfies every field CLAUDE.md asks for. The frontend reads `code` and `message`.
- **Consequences:** Small change to the handler and client; one shape everywhere. CLAUDE.md §11.4 and the team spec §9 should say "RFC 9457 with extension members".
- **Status:** Proposed. **Requirements:** NFR-016, CLAUDE.md §11.

## ADR-004 — Authentication and session
- **Context:** OQ-022 (sign-in method) is open; the team spec proposes seeded users, email + password, JWT.
- **Decision:** Spring Security with the OAuth2 resource-server JWT support (HS256, secret from `JWT_SECRET`); BCrypt hashes for seeded synthetic users; access token lifetime 8 hours; no refresh token; token held **in memory** in the SPA (no `localStorage`); rate limit on login (5/min per IP) and upload (30/min per user) with an in-process limiter (no new library). Roles and capabilities checked on the server for every call; object-level checks in services.
- **Consequences:** No external identity provider. A page reload requires signing in again — acceptable for the demo; revisit if the team prefers an HttpOnly cookie (then CSRF protection is required).
- **Status:** Proposed (depends on OQ-022, OQ-050). **Requirements:** FR-032, NFR-016.

## ADR-005 — Schema migrations and database roles
- **Context:** `ddl-auto=update` cannot create roles, grants or the audit trigger; the backend connects as the superuser.
- **Decision:** Flyway migrations in `database/migrations/`, run by the backend with the owner role; `ddl-auto=validate`; runtime uses `expenseflow_app` (no DDL, insert-only on audit). Backend Docker build context moves to the repo root to include the migrations.
- **Consequences:** Append-only audit enforced in the database; repeatable schema. One new library; Dockerfile and compose changes.
- **Status:** Proposed. **Requirements:** NFR-004, NFR-017.

## ADR-006 — Primary key type
- **Context:** `BaseEntity` uses `Long` identity IDs; the team spec proposes UUIDv7 (non-guessable, defence in depth against ID probing).
- **Decision:** UUIDv7 for all tables (PostgreSQL 18 `uuidv7()`), `BIGINT` identity for `audit_events`. Change `BaseEntity` to a UUID id (no entity extends it yet, so the cost is minimal); keep its timestamp fields.
- **Consequences:** IDs in URLs cannot be guessed; strict ordering of audit events kept.
- **Status:** Proposed. **Requirements:** NFR-005, B-10.

## ADR-007 — Policy as versioned configuration
- **Decision:** `policy_rules` is insert-only; each change inserts a new version. The current version number is stored on each claim at submit and used for its evaluation. Only finance head/admin may change values (API-035); each change is audited. The frontend reads limits from API-037 for hints only.
- **Status:** Proposed. **Requirements:** BR-027, BR-030, NFR-011.

## ADR-008 — Test strategy and test database
- **Context:** H2 is not in the approved stack and cannot prove PostgreSQL triggers, roles or JSONB behaviour.
- **Decision:** (1) Pure JUnit 5 tests for the policy and chain engines, written first. (2) Spring slice tests for controllers and services. (3) Database behaviour tested against real PostgreSQL — **either** Testcontainers in `mvnw verify` **or** pytest against the Docker Compose stack (no new dependency). Recommendation: pytest against compose now; Testcontainers only if the team wants DB tests inside Maven. Keep H2 only for the existing context-load test until it is replaced.
- **Status:** Proposed. **Requirements:** NFR-015, CL-18.

## ADR-009 — Frontend tooling
- **Decision:** Keep oxlint (already in use; replacing it brings no requirement benefit). Add Vitest + React Testing Library for component tests and Playwright for E2E. No UI kit, no global state or cache library.
- **Status:** Proposed. **Requirements:** NFR-015, NFR-020.

## ADR-010 — Deployment and CI hardening
- **Decision:** Pin exact image tags (no `latest`); bind ports to `127.0.0.1`; add `receipts`, `exports`, `prometheusdata` volumes; replace default passwords with fail-fast `${VAR:?}`; raise nginx `client_max_body_size` to the receipt limit; add `seed` service; CI adds pytest, frontend tests, Dependency-Check and `npm audit`, and a secret scan.
- **Status:** Proposed. **Requirements:** NFR-006, NFR-007, NFR-017.

## ADR-011 — Idempotency and concurrency
- **Decision:** `Idempotency-Key` header on submit, approve, reject, send-back, bulk approve, batch create and export; keys stored 24 h per user and operation. State-changing operations lock the claim (or batch) row; the database enforces one pending step per claim.
- **Status:** Proposed. **Requirements:** CLAUDE.md §10.5, B-06.

## ADR-012 — Package structure and pure engines
- **Decision:** Layered packages (`controller`, `service`, `repository`, `domain`, `dto`, `security`, `exception`, `config`) plus framework-free `policy` and `chain` packages, as in `backend/SPEC.md`. Existing `common` and `health` move into this layout. A JUnit test enforces engine purity.
- **Status:** Proposed. **Requirements:** CLAUDE.md §9.

## ADR-013 — File storage for receipts and exports
- **Decision:** Local Docker volumes; generated file names; SHA-256 recorded; files served only through authorised endpoints; exported files stored once and re-downloaded unchanged.
- **Status:** Proposed. **Requirements:** NFR-013, NFR-018, BR-031.

---

## 2. Unresolved decisions

| ID | Decision needed | Options | Recommendation | Blocks |
|---|---|---|---|---|
| **UD-01** | Does approving **as a delegate** trigger same-person coverage (CL-27)? | (a) No — own-right steps only; (b) Yes | **(a)** — keeps two reviews on large delegated claims | Chain engine |
| UD-02 | Error format | ADR-003 (a) / (b) | (b) | All endpoints, client |
| UD-03 | Migrations and DB roles | Flyway / keep `ddl-auto` | Flyway | Database, audit trigger |
| UD-04 | Primary keys | UUIDv7 / Long | UUIDv7 | All entities |
| UD-05 | Sign-in method, token lifetime, rate limits | ADR-004 | As ADR-004 (OQ-022, OQ-050) | Auth |
| UD-06 | Database tests | Testcontainers / pytest vs compose | pytest vs compose | CI, tests |
| UD-07 | Skip a step whose only approver is the claimant (ASM-009) | Confirm / change | Confirm | Chain engine |
| UD-08 | Mandatory send-back comment (OQ-034) | Yes / No | Yes | API-017, UI |
| UD-09 | Receipt types and size (OQ-021) | PDF/JPG/PNG, 5 MB proposed | Accept proposal | Upload, nginx |
| UD-10 | Delegator can still act during their delegation (ASM-S-04) | Confirm / change | Confirm | Chain engine |
| UD-11 | Alert threshold for oldest pending approval (OQ-042) | Team sets in runbook | Team decision | Ops |
| UD-12 | Frontend lint | oxlint / ESLint + Prettier | oxlint | Frontend CI |

## 3. Architectural risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | Team specs predate baseline v1.3 (duplicates, finance-head auto-approve, coverage) and could be built as written | High | High | `component-design.md` §9 change list; update specs before Phase 4 |
| R-02 | Drive and GitHub copies diverge (team specs exist only in GitHub; these documents only in Drive) | High | Medium | Agree one source of truth; copy these documents into the PR when GitHub access resumes |
| R-03 | Same-person coverage plus delegation weakens separation of duties | Medium | High | UD-01 recommendation; coverage visible in UI and audit |
| R-04 | Scope: 38 API operations and 13 screens in a hackathon time box | High | High | Build the demo path first (J1, J2, J6, J7); slice by user story priority |
| R-05 | H2 tests pass while PostgreSQL behaviour differs (triggers, JSONB, `uuidv7()`) | Medium | Medium | ADR-008 |
| R-06 | Superuser DB connection and default passwords | Medium | High | ADR-005, ADR-010 |
| R-07 | Error-format change breaks the existing client | Low | Medium | ADR-003 keeps RFC 9457; client updated in the same change |
| R-08 | Concurrent approvals or double clicks create inconsistent state | Medium | High | ADR-011 row locks, idempotency, one-pending-step index |
| R-09 | Seed rules drift from engine rules, giving misleading demo data | Medium | Medium | Seed uses the same routing table; spot-check with engine tests |
| R-10 | Token in memory: reload logs the user out during the demo | Medium | Low | Accept; rehearse; revisit in ADR-004 |
| R-11 | Unpinned images change between laptops | Medium | Medium | ADR-010 |
| R-12 | Python 3.15 picked up instead of 3.13 on laptops | Medium | Low | Always `py -3.13`; seed runs in a container |

## 4. Approval requested

1. Approve the architecture documents as the design baseline.
2. Decide **UD-01** (delegate coverage) — the only item that changes business behaviour.
3. Approve or amend ADR-001 to ADR-013 and UD-02 to UD-12 (recommendations given).

After approval: update the team specs to v1.3 (R-01), then the api-design skill produces `api-contracts.yaml`.
