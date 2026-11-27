# Defect Report — ExpenseFlow

Date: 2026-11-27 · Code: `main` at `cb8672d` · Also in the Defects sheet of `deliverables/test-cases.xlsx`.

Source: QA static review and the test runs listed in `qa-summary.md`. Runtime backend defects cannot be logged yet because the backend suites have not been run.

| ID | Title | Severity | Requirement | How found | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|
| DEF-006 | Synthetic seed data not implemented | **High** | FR-027–FR-030, DATA-001–005 | `database/` has no `seeds/` folder or Faker script | 150 employees, 8 departments, hierarchy, 3,000 claims with planted duplicates and breaches, bank details | Only 8 demo users in 3 departments (`DemoUserSeeder`) | Open — blocks 3 test cases and the final acceptance checklist |
| DEF-001 | CI runs no Vitest, pytest, Playwright or dependency check | Medium | NFR-007 | Review of `.github/workflows/ci.yml` | Tests + dependency check on every push | Only `mvnw verify`, lint, build, `docker compose build` | Open |
| DEF-002 | Prometheus and Grafana images use `:latest` | Medium | NFR-014, CLAUDE.md §8.2 | Review of `docker-compose.yml` | Exact versions | `prom/prometheus:latest`, `grafana/grafana:latest` | Open |
| DEF-003 | nginx sends no security headers | Low | NFR-009 | Review of `frontend/nginx.conf` | `X-Frame-Options`, CSP, `nosniff` | Not set (only on file downloads) | Open |
| DEF-004 | `/actuator/prometheus` published on host port 8080 without auth | Low | NFR-009 | Review of config | Metrics on internal network only | Exposed on 8080 | Open |
| DEF-005 | Default credentials in `docker-compose.yml` | Low | NFR-017 | Review of config | No usable defaults outside local demo | `postgres/postgres`, Grafana `admin/admin`, demo password | Open — accepted for local demo |
| DEF-007 | Frontend lint warnings `react(only-export-components)` | Low | NFR-015 | `npm run lint` | Clean lint | 5 warnings, 0 errors | Open |

Fixed since the first security review (verified in code; runtime check written but not run): least-privilege DB role for the app (F-01), `ddl-auto=validate` (F-06), upload size limit (F-08).

**Critical defects remaining: 0 known. High: 1 (DEF-006).** This is not a release sign-off.
