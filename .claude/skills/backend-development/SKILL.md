---
name: backend-development
description: Build the backend service and database layer that implement the OpenAPI contract, with layered structure, validation, error handling, logging, seed data and tests. Use when writing or changing code in backend/ or database/.
---

# Backend Development

## Inputs
- `architecture/api-contracts.yaml` (the contract — do not drift from it)
- `architecture/database-design.md`
- Tech stack from `CLAUDE.md`

## Target structure (adapt names to the chosen framework)
```
backend/
  src/
    config/        env loading, constants
    routes/        HTTP route definitions (thin)
    controllers/   request/response mapping
    services/      business logic
    repositories/  data access
    models/        entities / DTOs / schemas
    middleware/    auth, error handler, request logging, validation
    utils/
  .env.example
  README.md
database/
  migrations/      ordered schema scripts
  seeds/           demo data that tells the demo story
  docker-compose.yml (optional)
```

## Steps
1. Scaffold the project and a `/health` endpoint; confirm it runs.
2. Create migrations from the DB design; add seed data that matches the demo script.
3. Implement endpoints in priority order (demo path first), layer by layer: route → controller → service → repository.
4. Validate every input against the contract schema; return the shared `Error` shape.
5. Add a central error handler, structured logging and CORS for the frontend origin.
6. Write unit tests for services and API tests for each endpoint in `tests/backend/`.
7. Document setup, env vars and run commands in `backend/README.md`.
8. Tick the endpoint's status in `deliverables/api-inventory.xlsx`.

## Rules
- Business logic lives in services, never in routes/controllers.
- Parameterised queries / ORM only — no string-built SQL.
- Hash passwords (bcrypt/argon2); never return them.
- Config via env vars; ship `.env.example`, never `.env`.
- Mocked integrations sit behind an interface with a `USE_MOCKS=true` switch.

## Quality checklist
- [ ] Responses match the contract (status codes, shapes, field names)
- [ ] App starts from a clean clone with the README steps
- [ ] Seed data loads and supports the full demo
- [ ] Tests pass
