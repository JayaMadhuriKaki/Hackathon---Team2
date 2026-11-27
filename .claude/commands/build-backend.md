---
description: Implement backend endpoints and database layer against the API contract
argument-hint: "[optional endpoint IDs or feature to build, e.g. API-001..API-004]"
---

Use the **backend-development** skill.

1. Read `architecture/api-contracts.yaml`, `architecture/database-design.md` and the stack in `CLAUDE.md`.
2. If `backend/` is empty, scaffold the project with the target structure, `/health` endpoint, `.env.example` and README.
3. Create or update migrations and demo seed data in `database/`.
4. Implement the requested endpoints (or all Must-have endpoints, demo path first). Match the contract exactly.
5. Write tests in `tests/backend/` and run them.
6. Update the status column in `deliverables/api-inventory.xlsx`.

Scope: $ARGUMENTS

Finish with: what was built, how to run it, test results, and anything not done.
