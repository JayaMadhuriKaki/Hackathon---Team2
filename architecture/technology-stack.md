# Technology Stack — ExpenseFlow

| Field | Value |
|---|---|
| Version | 1.0 — **proposed, for review and approval** |
| Date | 2026-11-27 |
| Approved stack | CLAUDE.md §8 (CL-17, CL-18), AI Manthan Prerequisites Guide §3–§4 |
| Source of "in repository" versions | `backend/pom.xml`, `frontend/package.json`, Dockerfiles, `docker-compose.yml`, `.github/workflows/ci.yml` in the snapshot `main` @ `fe7ee73` |

**Rule:** nothing in the "In repository" or "Approved" columns is replaced without owner approval. Items marked **Proposed** are additions that need approval through `architecture-decisions.md`.

## 1. Stack by layer

| Layer | In repository (snapshot) | Approved (CLAUDE.md §8) | Status / decision |
|---|---|---|---|
| Frontend framework | React `^19.3.1`, React DOM `^19.3.1` | React 19 | ✅ Keep |
| Routing | React Router DOM `^7.18.7` | — (not listed) | ✅ Keep — already in use |
| Build | Vite `^8.4.0`, `@vitejs/plugin-react ^6.3.0` | Vite 7 or later | ✅ Keep |
| Node.js | `node:26.13.1-alpine` (build), CI `26.13.1` | 26.13.1 | ✅ Keep |
| Frontend serving | `nginx:1.29-alpine`, proxies `/api/` to backend | — | ✅ Keep; raise body limit (ADR-010) |
| Frontend lint | oxlint `^1.94.0` | ESLint + Prettier listed as *Proposed* | **Keep oxlint** — ADR-009 (Proposed) |
| Backend framework | Spring Boot `4.1.3`: webmvc, data-jpa, validation, actuator | Spring Boot 4.1.x | ✅ Keep |
| Java | 21 (`eclipse-temurin:21-jdk` / `21-jre`) | JDK 21 (Temurin) | ✅ Keep |
| Build tool | Maven wrapper (`mvnw`, `mvnw.cmd`) | Maven wrapper | ✅ Keep |
| Boilerplate | Lombok | — | ✅ Keep — already in use |
| Database | `postgres:18`, JDBC driver | PostgreSQL 18 in Docker | ✅ Keep; pin exact 18.x tag (ADR-010) |
| Data access | Spring Data JPA (Hibernate) | Spring Data JPA (Proposed in §8.1) | ✅ Keep |
| Schema management | `ddl-auto=update` | Flyway (Proposed in §8.1) | **Proposed — ADR-005** |
| Metrics | Actuator + `micrometer-registry-prometheus` | Prometheus + Grafana | ✅ Keep |
| Monitoring | `prom/prometheus:latest`, `grafana/grafana:latest` | Prometheus + Grafana | ✅ Keep; **pin tags** (ADR-010) |
| Containers | Docker Compose (5 services) | Docker Compose | ✅ Keep; add `seed`, volumes |
| Java unit tests | `spring-boot-starter-*-test` (JUnit 5) | JUnit 5 (CL-18) | ✅ Keep |
| Backend test DB | H2 in PostgreSQL mode | — (not listed) | **Proposed — ADR-008** |
| API / integration tests | — | pytest on Python 3.13 (CL-17) | ✅ Add (approved) |
| E2E tests | — | Playwright 1.60 or later | ✅ Add (approved) |
| Load tests | — | Locust | ✅ Add (approved) |
| CI | GitHub Actions: backend verify, frontend lint/build, docker build | GitHub Actions + dependency check | ✅ Keep; add steps (ADR-010) |
| Seed | — | Python 3.13 + Faker | ✅ Add (approved); psycopg is Proposed |

## 2. Proposed additions (need approval)

| Addition | Layer | Why it is needed | Alternative considered | ADR |
|---|---|---|---|---|
| `spring-boot-starter-security` | Backend | Authentication and role checks (FR-032, NFR-016) | Hand-written filter — more risk, no benefit | ADR-004 |
| `spring-boot-starter-oauth2-resource-server` | Backend | JWT validation with Spring's own Nimbus support, no third-party JWT library | `jjwt` library | ADR-004 |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | Backend | Versioned migrations, DB roles, audit trigger (NFR-004) | Keep `ddl-auto=update` — cannot create triggers, roles or grants | ADR-005 |
| Vitest + React Testing Library + jsdom | Frontend | Component tests (CLAUDE.md §16.4) | None in the approved list | ADR-009 |
| `@playwright/test` ≥ 1.60 | Frontend | 10 E2E cases (NFR-015) | — (approved) | — |
| psycopg 3, Faker | Seed | Load synthetic data (FR-027–FR-030) | Java seeder — slower to write | ADR-005 |
| Testcontainers (PostgreSQL) | Backend tests | Run repository tests on real PostgreSQL | pytest against the compose stack (no new dependency) | ADR-008 |
| OWASP Dependency-Check (Maven) and `npm audit` | CI | Dependency check (NFR-007) | GitHub Dependabot alerts | ADR-010 |

Nothing else is added. Specifically **not** added without a new request: UI component kits, state-management libraries, data-cache libraries, form libraries, chart libraries beyond what the dashboard needs (decided when the dashboard is designed), message queues, caches, cloud services.

## 3. Not used

FastAPI or a Node backend · Python 3.15 · JDK 27 · SQLite · an installed Maven/Gradle · pip-installed Airflow/Superset · k6 on the host · the PostgreSQL desktop installer · Jira · company AWS · any live external service.

## 4. Version and environment notes

| Topic | Note |
|---|---|
| Python on laptops | Plain `python` may be 3.15; always use `py -3.13` (PROGRESS.md environment check) |
| JAVA_HOME | Must point to JDK 21 |
| Ports | Bind to `127.0.0.1` only (ADR-010) |
| Image tags | Pin exact versions at least two weeks old; no `latest` |
| Event rules | Open-source/free tools only; synthetic data only (NFR-012, NFR-013) |
