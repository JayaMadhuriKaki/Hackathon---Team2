# Hackathon App

Monorepo with a React frontend, a Spring Boot backend, PostgreSQL, and Prometheus + Grafana, all runnable with one Docker Compose command.

## Architecture

```
 Browser
    │
    ▼
 frontend  (React 19 + Vite, served by nginx)      :3000
    │  /api/*  proxied by nginx (Vite proxy in dev)
    ▼
 backend   (Spring Boot 4.1, Java 21)              :8080
    │  JPA / JDBC                 ▲ /actuator/prometheus
    ▼                             │
 postgres  (PostgreSQL 18)  :5433   prometheus :9090 ──► grafana :3001
```

The browser only ever talks to the frontend origin; `/api` is forwarded to the backend, so there is no CORS to deal with.

## Repo layout

```
backend/                Spring Boot (Maven wrapper, no Maven install needed)
  src/main/java/com/hackathon/backend/
    config/             cross-cutting config (CORS, etc.)
    common/entity/      BaseEntity (id, createdAt, updatedAt)
    common/exception/   GlobalExceptionHandler, ResourceNotFoundException
    health/             /api/health (example feature package)
frontend/               React + Vite
  src/api/              client.js (fetch wrapper) + one file per resource
  src/components/       shared UI (Layout)
  src/pages/            one component per route
infra/                  Prometheus scrape config, Grafana datasource
docker-compose.yml      whole stack
.github/workflows/      CI: backend tests, frontend lint + build, docker build
```

## Run everything (Docker)

```powershell
copy .env.example .env        # first time only
docker compose up -d --build
```

| Service    | URL                                   | Login         |
|------------|---------------------------------------|---------------|
| Frontend   | http://localhost:3000                 |               |
| Backend    | http://localhost:8080/api/health      |               |
| Metrics    | http://localhost:8080/actuator/prometheus |           |
| Prometheus | http://localhost:9090                 |               |
| Grafana    | http://localhost:3001                 | admin / admin |
| Postgres   | localhost:5433, db `hackathon`        | postgres / postgres |

Useful commands:

```powershell
docker compose logs -f backend                  # follow logs
docker compose up -d --build backend            # rebuild one service after changes
docker compose exec postgres psql -U postgres -d hackathon
docker compose down                             # stop (keeps data)
docker compose down -v                          # stop and wipe DB data
```

## Day-to-day development (fast reload)

Run only the database in Docker and the apps natively:

```powershell
docker compose up -d postgres

# terminal 1 – backend (http://localhost:8080)
cd backend
.\mvnw.cmd spring-boot:run

# terminal 2 – frontend (http://localhost:5173, proxies /api to :8080)
cd frontend
npm install
npm run dev
```

If the backend is also running in Docker, stop it first (`docker compose stop backend`) so port 8080 is free.

## Adding a feature

**Backend** – create a package per feature, e.g. `com.hackathon.backend.task`:

| File | Purpose |
|------|---------|
| `Task.java` | `@Entity`, extends `BaseEntity` |
| `TaskRepository.java` | `interface TaskRepository extends JpaRepository<Task, Long>` |
| `TaskService.java` | business logic; throw `ResourceNotFoundException` for missing items |
| `TaskController.java` | `@RestController @RequestMapping("/api/tasks")` |
| `TaskRequest.java` / `TaskResponse.java` | records; use `@Valid` + Jakarta validation on requests |

Tables are created automatically from entities (`ddl-auto=update`). Errors come back as RFC 9457 problem JSON: `{ "status", "title", "detail", "errors"? }`.

**Frontend** – add `src/api/tasks.js` using `api.get/post/...` from `client.js`, a page in `src/pages/`, and a `<Route>` in `App.jsx`.

## Tests

```powershell
cd backend;  .\mvnw.cmd test        # uses in-memory H2, no Postgres needed
cd frontend; npm run lint; npm run build
```

## Configuration

Backend settings live in `backend/src/main/resources/application.properties` and read env vars with local defaults: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `JPA_SHOW_SQL`. Compose values come from `.env` (not committed; see `.env.example`).

## Troubleshooting

- **Password authentication failed on port 5432** – a locally installed PostgreSQL may own 5432. This stack uses **5433**.
- **`invalid value for parameter "TimeZone": "Asia/Calcutta"`** – Postgres 18 rejects that legacy zone name; `BackendApplication` maps it to `Asia/Kolkata` at startup.
- **Port already in use** – change `BACKEND_PORT`, `FRONTEND_PORT` or `DB_PORT` in `.env`.
