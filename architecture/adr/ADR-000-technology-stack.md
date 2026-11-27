# ADR-000 — Technology stack

| Field | Value |
|---|---|
| Status | **Accepted** for the stack in CLAUDE.md §8.1 (CL-17, CL-18). P-01, P-02, P-03, P-06, P-11 **implemented** on the owner's instruction to complete the backend (2026-11-27); the rest still **Proposed** |
| Date | 2026-11-27 |
| Sources | CLAUDE.md §8, AI Manthan Prerequisites Guide §3–§4, `architecture/SPEC.md` §13 and §15 |

## Context

The project owner approved the core stack (CL-17) and JUnit 5 for Java unit tests (CL-18). CLAUDE.md §8.1 also names items marked "Proposed" that must be confirmed here before they are added (§7.4: no dependency without a stated reason). The event rules (§8.2) require exactly the guide's versions, open-source tools only, and no system installs.

## Decision — approved stack (no further approval needed)

| Layer | Choice | Version in this repo |
|---|---|---|
| Frontend | React 19 + Vite, JavaScript | React 19.3, Vite 8.4, Node 26.13.1 |
| Backend | Spring Boot on JDK 21 (Temurin), Maven wrapper | Spring Boot 4.1.3 |
| Database | PostgreSQL 18 in Docker | `postgres:18` (18.7) |
| Java unit tests | JUnit 5 via `spring-boot-starter-test` | from Spring Boot BOM |
| API / integration tests | pytest on Python 3.13 with httpx | `py -3.13` |
| E2E | Playwright ≥ 1.60 | — |
| Load | Locust (Python 3.13) | — |
| Delivery | Docker Compose | Compose v5 |
| Monitoring | Prometheus + Grafana via Actuator + Micrometer | — |
| CI | GitHub Actions | — |

## Proposed items — need confirmation before they are added

| # | Item | Reason | Alternative | Status |
|---|---|---|---|---|
| P-01 | **Spring Data JPA (Hibernate)** + PostgreSQL JDBC | Already in the base; standard for Spring Boot; parameterised queries only (§11.7) | JdbcTemplate | In use since base setup |
| P-02 | **Flyway** for migrations, scripts in `database/migrations/` included as a Maven resource; backend Docker build context moves to the repo root; `ddl-auto=validate` | Versioned schema; Spring Boot auto-configures it; runs as `expenseflow_owner` | Liquibase; or a separate one-shot migrate container (keeps owner credentials out of the running backend) | **Implemented** (V001–V005). Flyway runs inside the backend with the owner credentials; the app datasource uses the least-privilege role created by V005 from Flyway placeholders |
| P-03 | **Spring Security + JWT (HS256) + BCrypt** | Stateless auth for seeded synthetic users (SPEC-Q-07); BCrypt hashes from pgcrypto (`$2a$`) are accepted | Session cookies (needs CSRF) | **Implemented** via `spring-boot-starter-oauth2-resource-server` (Nimbus HS256 encoder/decoder), no hand-written crypto |
| P-04 | **pgcrypto** extension, created by a migration | Seed hashes demo passwords in SQL; no extra Python package | Python `bcrypt` | Extension created by V001; demo users are currently seeded by the backend (`DemoUserSeeder`, BCrypt in Java). Still used by the future Python seed |
| P-05 | **psycopg 3** + **Faker** for the seed script | Faker is named by the guide; psycopg is the standard PostgreSQL driver | — | Proposed |
| P-06 | **UUIDv7** primary keys (PostgreSQL 18 `uuidv7()`); `audit_events` uses BIGINT identity | Time-ordered keys, no enumeration of IDs; strict audit order (ASM-S-06) | BIGINT identity everywhere | **Implemented**: IDs generated in Java (UUIDv7, `Ids.newId()`) so the same entities run on H2 tests; DB default `uuidv7()` as fallback |
| P-07 | **Vitest + React Testing Library** | Frontend component tests (§16.4) | Jest | Proposed |
| P-08 | Linting: keep **oxlint** (already in the Vite template) instead of ESLint + Prettier | Already configured and passing; avoids a second linter | ESLint + Prettier as §8.1 lists | Proposed |
| P-09 | **OWASP Dependency-Check** (Maven) + **npm audit** in CI | NFR-007 | GitHub Dependabot | Proposed |
| P-10 | Pinned image tags for Postgres 18.x, Temurin 21, Node 26.13.1, nginx, Python 3.13, Prometheus, Grafana | §8.2 exact versions; today Prometheus and Grafana use `latest` | — | Proposed; tags chosen in Phase 7 |
| P-11 | H2 kept only for Spring context tests; database behaviour (incl. NFR-004 permissions) proven by pytest against PostgreSQL in Docker | H2 is not in §8.1 and cannot prove PostgreSQL grants | Testcontainers (not in the guide) | **In use**: H2 (`NON_KEYWORDS=VALUE`) runs the JUnit API flow tests; Flyway SQL and grants verified on the Docker stack |

## Consequences

- The policy and approval-chain engines need none of P-02…P-11; Phase 4a/4b can proceed now.
- Task 9 onward was built on P-02, P-03, P-06 as proposed, on the owner's instruction "complete entire backend work" (2026-11-27). Reverting any of them needs a new ADR.
- Any change to the approved stack needs a new ADR and the owner's approval (§8).
