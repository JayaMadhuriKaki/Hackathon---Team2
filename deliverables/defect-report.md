# Defect Report — ExpenseFlow

Date: 2026-11-27 · Source: static review of `main` (`cb8672d`) and the test runs listed in `qa-summary.md`. No runtime defects could be logged yet because the backend suites have not run.

| ID | Title | Severity | Requirement | Found by | Status |
|---|---|---|---|---|---|
| DEF-001 | CI runs no Vitest, pytest, Playwright or dependency vulnerability check | Medium | NFR-007 | Review of `.github/workflows/ci.yml` (F-04) | Open |
| DEF-002 | Prometheus and Grafana images use `:latest` (event rule: exact versions) | Medium | §8.2, NFR-006 | Review of `docker-compose.yml` (F-03) | Open |
| DEF-003 | nginx sends no security headers (clickjacking / MIME sniffing) | Low | NFR-009 | Review of `frontend/nginx.conf` (F-07) | Open |
| DEF-004 | `/actuator/prometheus` published on host port 8080 without auth | Low | NFR-009 | F-05 | Open |
| DEF-005 | Default credentials in `docker-compose.yml` (`postgres/postgres`, Grafana `admin/admin`, demo password) | Low | §13.6 | F-02 | Open — accepted for local demo |

Fixed since the first review (verified in code, runtime check pending): F-01 app uses least-privilege DB role; F-06 `ddl-auto=validate`; F-08 upload size limit.

**Critical defects remaining: 0 known.** This is not a release sign-off: the backend has not been exercised by QA.
