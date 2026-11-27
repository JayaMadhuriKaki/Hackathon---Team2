---
name: solution-architecture
description: Analyse the existing repository and design the solution architecture on top of the technology already in place — system context and component/container diagrams, frontend/backend responsibilities, database entities and relationships, the claim state machine, integrations and dependencies, and architecture decision records (ADRs). Use this skill whenever the task involves design, structure, module boundaries, data modelling, choosing or adding a library, diagrams, or files in architecture/ — and before starting any new feature that touches more than one layer.
---

# Solution Architecture

Produce a design that fits the code that already exists and the stack that has been approved, and that a skeptical finance auditor cannot easily bypass. Good architecture here is mostly about saying "no" to unnecessary moving parts and making every decision explicit so the team does not re-argue it later.

## Responsibilities
1. Analyse the existing repository.
2. Recommend architecture based on the existing technology.
3. Create system context and component (container) diagrams.
4. Define frontend and backend responsibilities.
5. Define database entities and relationships.
6. Identify integrations and dependencies.
7. Document architectural decisions (ADRs).

## Inputs
| Input | Location |
|---|---|
| Standards, approved stack, business rules, state machine draft, bypass list | `CLAUDE.md` §2, §4, §5, §8, §9 |
| Requirement baseline and traceability | `requirements/requirement-analysis.md`, `deliverables/requirement-traceability.xlsx` |
| Existing design docs | `architecture/*.md`, `architecture/adr/` |
| Existing code and config | `backend/`, `frontend/`, `docker-compose.yml`, `.github/workflows/`, `ops/` |

## Inspect the repository first
The existing code is the strongest constraint. Before proposing anything:
1. `git status`, `git log --oneline -15`; note open branches.
2. Read build files to identify actual versions: `backend/pom.xml` (Spring Boot version, Java version, starters, Lombok, test DB), `frontend/package.json` (React, Vite, router, linter), `docker-compose.yml` (services, ports, images, volumes), CI workflows.
3. Read configuration: `backend/src/main/resources/application.properties` (datasource, `ddl-auto`, error format, actuator), `frontend/vite.config.js`, `frontend/nginx.conf` (proxying), `.env.example`.
4. Map the existing package/folder structure (e.g. `com.hackathon.backend.{config,common,health}`, `frontend/src/{api,components,pages}`) and the patterns in use: error handling (`GlobalExceptionHandler`, RFC 9457 `ProblemDetail`), base entity, API client, routing.
5. Write a short **"Current state"** section listing what exists, what is reusable, and every place the code differs from `CLAUDE.md`. Do not "fix" differences silently — each one becomes an ADR or a question.

Known differences to resolve by ADR (verify they still exist): error format (RFC 9457 problem details in code vs `{code, message, details, traceId}` in CLAUDE.md §11.4); schema management (`ddl-auto=update` vs Flyway migrations); H2 for tests vs PostgreSQL behaviour; oxlint vs ESLint/Prettier; unpinned `latest` images for Prometheus/Grafana.

## Step-by-step execution
1. **Confirm scope.** List the requirement IDs (FR/NFR/POL/CL) being designed. Stop if a critical question (CLAUDE.md §6) blocks them.
2. **Context diagram** (Mermaid `flowchart`): actors (employee, line manager, finance auditor, finance head/admin), the system, and external elements (bank-file consumer, Prometheus/Grafana).
3. **Container/component diagram**: React SPA (nginx), Spring Boot API, PostgreSQL, receipt file storage, Prometheus, Grafana. Inside the backend, show modules — e.g. `claims`, `policy` (engine), `approval` (chain + delegation), `audit`, `payout`, `dashboard`, `users/org`, `security`, `common`. Each module owns its controller → service → repository layers.
4. **Responsibilities table**: for each requirement, which layer does what. Frontend: presentation, client-side validation for usability, state display. Backend: all business rules, validation, authorization, state transitions, audit, export. Database: integrity constraints, append-only audit permissions.
5. **Claim state machine** (Mermaid `stateDiagram-v2`): start from CLAUDE.md §4.1; every transition lists actor, guard and side effects (audit entry, recalculation). Add nothing that is not traceable to an FR or CL.
6. **Policy-as-configuration design**: how rules and limits are stored (table + seed), loaded, versioned, edited (finance head/admin only, audited), and how in-flight claims are protected from config changes.
7. **Approval-chain model** — write `architecture/adr/ADR-001-approval-chain.md`: data-driven ordered steps, chain snapshot at submit, skip rules (no approver, self-approval), delegation resolution, restart on resubmission.
8. **Data model** in `architecture/database-design.md`: entities, attributes with types, keys, relationships with cardinality, unique constraints (e.g. date + receipt number), indexes, money representation (no floating point), audit columns. Draw a Mermaid `erDiagram`. Only include fields required by a requirement; mark each with its requirement ID.
9. **Integrations and dependencies**: payout CSV export, receipt storage, metrics endpoint, seed generator, CI. List every library to be added with its reason; anything not in CLAUDE.md §8.1 needs approval.
10. **Security design**: authentication, role/object-level authorization, upload handling, CSV injection, and a walk through every bypass in CLAUDE.md §5 with the control that stops it. Write results to `architecture/security-review.md`. Review the design as a skeptical finance auditor and list further bypass ideas.
11. **ADRs** in `architecture/adr/ADR-###-<slug>.md` for each significant decision: Context, Options considered, Decision, Consequences, Status (Proposed/Accepted), Requirement IDs. ADR-000 records the stack actually in the repo.
12. **Update** `architecture/solution-design.md`, `architecture/architecture-diagram.md`, and the "Design Component" column of the traceability matrix.
13. **Present** a summary and the list of ADRs needing approval. Do not create code.

## Expected outputs
- `architecture/solution-design.md` (with "Current state" section)
- `architecture/architecture-diagram.md` (context, container/component, sequence for main flows, state machine, deployment)
- `architecture/database-design.md` (entities, ER diagram, constraints, indexes)
- `architecture/adr/ADR-000-technology-stack.md`, `ADR-001-approval-chain.md`, plus others as needed
- `architecture/security-review.md` (bypass analysis)
- Updated "Design Component" column in `deliverables/requirement-traceability.xlsx`

## Quality checklist
- [ ] Design reuses existing packages, base classes, error handler, API client and compose services
- [ ] Every component and entity traces to at least one requirement ID
- [ ] All diagrams are valid Mermaid and match the text
- [ ] Every state transition has actor, guard and audit side effect
- [ ] Every limit/threshold is configuration, not code
- [ ] Money is never a floating-point type
- [ ] Every CLAUDE.md §5 bypass has a named control
- [ ] Every new dependency has a reason and approval status
- [ ] Every code-vs-CLAUDE.md difference is covered by an ADR or a question

## Rules for avoiding assumptions
- Design from the repository as it is, not as a template says it should be. Read before you recommend.
- Do not change the approved stack (CLAUDE.md §8) or swap a library the team already uses without an approved ADR.
- Do not add entities, fields, roles, states or integrations that no requirement needs.
- If two valid designs exist and the choice affects behaviour, present both in an ADR marked *Proposed* and ask.
- Mark everything you infer as *Proposed* until approved; record approvals in the ADR and CLAUDE.md decision log (propose the diff; CLAUDE.md edits need approval).

## Hand-off
API design (api-design skill) starts when ADR-000 and ADR-001 are Accepted and the data model is approved.
