# CLAUDE.md — Hackathon Project Guide

> Claude Code reads this file at the start of every session. Keep it current.
> Anything in `<angle brackets>` is a placeholder to fill in once the problem statement is known.

## 1. Project Snapshot

| Item | Value |
|---|---|
| Project name | `<Project Name>` |
| Hackathon | `<Event name, organiser>` |
| Problem statement | `<One-paragraph summary — full text in requirements/requirements.docx>` |
| Team | `<Name — role>`, `<Name — role>`, `<Name — role>` |
| Submission deadline | `<YYYY-MM-DD HH:MM TZ>` |
| Demo slot | `<YYYY-MM-DD HH:MM TZ, duration>` |
| Repository | `<git URL>` |
| Judging criteria | `<e.g. Innovation 25%, Technical depth 25%, Business impact 25%, Presentation 25%>` |

## 2. Tech Stack (update once decided)

| Layer | Choice | Notes |
|---|---|---|
| Frontend | `<React + Vite + TypeScript>` | UI library: `<Tailwind / MUI>` |
| Backend | `<Node.js Express / Python FastAPI / Java Spring Boot>` | |
| Database | `<PostgreSQL / MongoDB / SQLite>` | Migrations in `database/` |
| API style | REST, OpenAPI 3.0 | Contract in `architecture/api-contracts.yaml` |
| Auth | `<JWT / OAuth2 / none for demo>` | |
| AI / ML | `<LLM provider, model>` | Keys in `.env`, never committed |
| Hosting | `<Vercel / Render / Azure / local Docker>` | |
| Testing | `<Jest / Pytest / Playwright>` | |

## 3. Repository Map

```
CLAUDE.md                       ← you are here
.claude/skills/                 ← playbooks Claude loads for each phase
.claude/commands/               ← slash commands that run each phase
requirements/                   ← problem statement, use cases, analysis
architecture/                   ← solution design, diagrams, DB design, OpenAPI contract
frontend/ backend/ database/    ← source code
tests/                          ← frontend, backend, integration tests + test data
deliverables/                   ← traceability, API inventory, test cases, validation matrix, demo script
demo/                           ← screenshots and demo video
```

## 4. Delivery Workflow

Run the phases in order. Each phase has a slash command and a skill.

| # | Phase | Command | Skill | Main outputs |
|---|---|---|---|---|
| 1 | Requirement analysis | `/analyse-requirements` | requirement-analysis | `requirements/requirement-analysis.md`, `deliverables/requirement-traceability.xlsx` |
| 2 | Architecture | `/design-architecture` | solution-architecture | `architecture/solution-design.md`, `architecture-diagram.md`, `database-design.md` |
| 3 | API design | `/generate-apis` | api-design | `architecture/api-contracts.yaml`, `deliverables/api-inventory.xlsx` |
| 4 | Backend | `/build-backend` | backend-development | `backend/`, `database/` |
| 5 | Frontend | `/build-frontend` | frontend-development | `frontend/` |
| 6 | Validation | `/run-validation` | quality-engineering | `tests/`, `deliverables/test-cases.xlsx`, `validation-matrix.xlsx` |
| 7 | Demo | `/prepare-demo` | demo-preparation | `deliverables/demo-script.md`, `demo/` |

## 5. Working Rules for Claude

1. **Requirements are the source of truth.** Every feature traces to a requirement ID (`FR-xxx` / `NFR-xxx`). If something isn't in the requirements, ask before building it.
2. **Contract first.** Update `architecture/api-contracts.yaml` before changing any endpoint. Frontend and backend both follow the contract.
3. **Demo-driven scope.** Build the demo happy path first. Everything else is MoSCoW "Should/Could".
4. **Small, working increments.** After every change the app must still start. Commit often with clear messages.
5. **Docs move with code.** When behaviour changes, update the matching doc and the traceability matrix in the same task.
6. **No secrets in the repo.** Use `.env` (git-ignored) and document variables in `.env.example`.
7. **Ask, don't assume,** when a requirement is ambiguous. If you must proceed, log the assumption in `requirements/requirement-analysis.md` §Assumptions.

## 6. ID Conventions

| Artefact | Format | Example |
|---|---|---|
| Functional requirement | `FR-###` | FR-001 |
| Non-functional requirement | `NFR-###` | NFR-003 |
| Use case | `UC-###` | UC-002 |
| User story | `US-###` | US-010 |
| API endpoint | `API-###` | API-004 |
| Test case | `TC-<FE/BE/INT>-###` | TC-BE-012 |
| Defect | `BUG-###` | BUG-007 |

## 7. Coding Standards

- Standard formatter and linter on every file (Prettier/ESLint, Black/Ruff, etc.).
- Meaningful names; functions under ~40 lines; no dead code or commented-out blocks.
- Errors use the standard error shape in `api-contracts.yaml` (`code`, `message`, `details`).
- Log with levels (info/warn/error); never log passwords, tokens or personal data.
- Every endpoint and every key UI flow has at least one automated test.

## 8. Definition of Done

- [ ] Runs locally from a clean clone using the README steps
- [ ] Linked to at least one requirement ID in the traceability matrix
- [ ] Tests written and passing
- [ ] API contract and API inventory updated (if endpoints changed)
- [ ] No secrets, no console errors, no TODOs on the demo path

## 9. Common Commands (fill in)

```bash
# Frontend
cd frontend && <npm install> && <npm run dev>
# Backend
cd backend && <install command> && <run command>
# Database
<docker compose up -d db> && <migration command>
# Tests
<test command>
```

## 10. Decision Log

| Date | Decision | Reason | Owner |
|---|---|---|---|
| `<YYYY-MM-DD>` | `<e.g. Use PostgreSQL>` | `<Relational data, team familiarity>` | `<Name>` |
