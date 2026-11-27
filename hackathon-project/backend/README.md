# Backend

API service implementing `../architecture/api-contracts.yaml`. Built with `/build-backend` using the **backend-development** skill.

## Setup
```bash
<install command>
cp .env.example .env   # set DATABASE_URL, JWT_SECRET, USE_MOCKS
<run command>          # health check: GET /api/v1/health
```

## Structure
`src/config` · `src/routes` · `src/controllers` · `src/services` · `src/repositories` · `src/models` · `src/middleware` · `src/utils`

## Notes
- Tests live in `../tests/backend/`.
