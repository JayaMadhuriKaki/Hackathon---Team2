---
name: backend-development
description: Implement the Spring Boot backend against the approved OpenAPI contract — controllers, services and repositories, business rules (policy engine, approval chain, delegation, payout), server-side validation, database operations, authentication and authorization, logging, consistent exception handling, security guardrails, and JUnit 5 unit tests plus pytest API tests. Use this skill for any change under backend/ or database/, any business-rule, entity, migration, security or error-handling work, and whenever an endpoint's behaviour must change.
---

# Backend Development

The backend is where ExpenseFlow's money, approval and audit rules are actually enforced. Treat every request as untrusted, every rule as configuration-driven and test-first, and every state change as something an auditor will later inspect.

## Responsibilities
1. Implement backend APIs.
2. Implement business rules and validations.
3. Implement database operations.
4. Implement authentication and authorization.
5. Implement logging and exception handling.
6. Apply security guardrails.
7. Implement unit and integration tests.

## Inputs
| Input | Location |
|---|---|
| Business rules, routing, state machine, bypass list | `CLAUDE.md` §2, §2.6–2.8, §4, §5 |
| Backend, security and testing standards; approved stack | `CLAUDE.md` §8, §11, §13, §16 |
| API contract | `architecture/api-contracts.yaml` |
| Data model and ADRs | `architecture/database-design.md`, `architecture/adr/` |
| Approved plan for the task | The current conversation |

## Inspect the repository first
1. `git status` / `git log --oneline -10`.
2. Read `backend/pom.xml`: Spring Boot version, Java 21, starters present (web MVC, data JPA, validation, actuator, Micrometer Prometheus, Lombok, devtools, H2 for tests). Do not add dependencies without approval (CLAUDE.md §7.4); security (Spring Security/JWT) and Flyway are "Proposed" until an ADR accepts them.
3. Read `application.properties` (main and test): datasource env vars, `ddl-auto` setting, problem-details error format, CORS, actuator exposure. Respect the error-format and schema-management ADRs; if they do not exist yet, raise them before building endpoints.
4. Map existing packages under `com.hackathon.backend` (`config`, `common.entity.BaseEntity`, `common.exception.GlobalExceptionHandler`, `ResourceNotFoundException`, `health`) and follow the same style (tabs, naming, test layout). Extend `BaseEntity` and the global handler instead of creating parallel ones.
5. Read existing tests (`src/test/java/...`) to copy their patterns.
6. Check `database/` for migrations/seeds and `tests/backend`, `tests/integration` for pytest suites.

## Step-by-step execution
1. **Plan first** (CLAUDE.md §14.2) and wait for approval.
2. **Contract check**: confirm every operation you will implement exists in `api-contracts.yaml`. If not, stop and use api-design.
3. **Tests before rules** (mandatory for the policy and approval-chain engines): write JUnit 5 tests per rule — one per POL rule and per CL routing decision, including just-below / exactly-at / just-above boundaries — run them, confirm they fail, then implement.
4. **Package by module** (e.g. `claims`, `policy`, `approval`, `audit`, `payout`, `dashboard`, `org`, `security`), each with `controller`, `service`, `repository`, `dto`, `domain` as needed. Controllers are thin: map DTOs, call a service, return a response.
5. **Validation**: Bean Validation on every request DTO matching the contract; reject unknown/forbidden fields; validate path/query params; validate uploads (size, allow-listed content type checked by content, not extension) and compute SHA-256.
6. **Business rules in services**: policy evaluation (limits from configuration, never literals), routing per CLAUDE.md §2.7, chain snapshot at submit, skip rules, self-approval block, delegation (allowed roles and dates), send-back restart, rejection with mandatory comment, sample-audit selection and payout hold, payout batch eligibility and export. Each state change validates the current state and the actor's right to act (409 / 403 otherwise).
7. **Persistence**: Spring Data JPA repositories or parameterized JPQL only — never concatenate SQL. Money in `BigDecimal` with fixed scale or integer paise. Multi-step changes (state + audit, batch export + Paid) in one `@Transactional` method. Unique constraints in the schema for business keys (e.g. date + receipt number).
8. **Audit trail**: written in the same transaction as the action; append-only (no update/delete code path; DB permissions per ADR).
9. **Authentication & authorization**: per the security ADR. Enforce role checks and **object-level** checks on every read and write (employee: own claims; approver: assigned/acted-on claims, CL-15). Return 404 for objects the user may not see.
10. **Exception handling**: add handlers to the existing `GlobalExceptionHandler` for domain exceptions (state conflict → 409, business rule → 422, forbidden → 403, upload limits → 413/415). Response shape per the error-format ADR; never leak stack traces or SQL.
11. **Logging and metrics**: SLF4J with request/trace ID; never log receipts, passwords, tokens or bank details. Expose Micrometer metrics needed by SRE (pending-approval queue size, oldest pending approval age, request latency).
12. **Security guardrails**: CSV formula-injection neutralisation in exports; CORS limited to known origins; secrets only via env vars (update `.env.example` with placeholders, never real values).
13. **Tests**:
    - JUnit 5 unit tests for services/engines (`./mvnw test`);
    - Spring slice/integration tests where useful (`@WebMvcTest`, `@DataJpaTest`) — note H2 may differ from PostgreSQL; run DB-sensitive tests against PostgreSQL (Docker) per the testing ADR;
    - pytest API tests in `tests/backend/` and `tests/integration/` against the running service (happy path, 400, 401, 403/404 for other users' claims, 409, 422, boundaries).
14. **Run** `./mvnw -B verify` (or `mvnw.cmd` on Windows) and the pytest suite. Report the exact commands and pass/fail counts. Never weaken or delete a test to get green.
15. **Update** the API inventory status, RTM "Code Module / File" and test columns, `PROGRESS.md`; commit a checkpoint.

## Expected outputs
- Java code under `backend/src/main/java/com/hackathon/backend/...`
- Migrations/seed (per ADR) under `database/` or the backend resources
- JUnit 5 tests under `backend/src/test/java/...`; pytest suites under `tests/backend/`, `tests/integration/`
- Updated `deliverables/api-inventory.xlsx`, RTM, `PROGRESS.md`
- Test results in the task summary

## Quality checklist
- [ ] Every implemented operation matches the contract (paths, fields, status codes, error shape)
- [ ] Policy/approval tests were written first and cover every rule and boundary
- [ ] No limit, threshold or rate is a literal in code
- [ ] Every endpoint has authentication, role and object-level authorization
- [ ] Every state change validates current state, writes an audit entry, runs in one transaction
- [ ] No string-built SQL; money never `double`/`float`
- [ ] No secrets, stack traces or sensitive data in responses or logs
- [ ] Existing classes reused (BaseEntity, GlobalExceptionHandler); package style followed
- [ ] `mvnw verify` and pytest actually run and pass; results reported

## Rules for avoiding assumptions
- Implement only rules documented in CLAUDE.md §2 or confirmed CL-xx decisions. Unknown behaviour → stop and raise Q-##.
- Do not invent fields, enum values, roles, statuses or endpoints; the contract and data model are the source of truth.
- Do not choose a boundary (">" vs "≥") or rounding rule yourself — use the documented one (e.g. CL-03) or ask.
- Do not add or upgrade dependencies, or change `ddl-auto`/schema strategy, without an approved ADR.
- Never claim tests passed without running them.
