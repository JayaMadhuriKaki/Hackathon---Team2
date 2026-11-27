# QA Summary — ExpenseFlow

| Field | Value |
|---|---|
| Date | 2026-11-27 |
| Code under test | `main` at `cb8672d` (backend PR #3 + frontend PR #4) |
| QA branch | `test/qa-automation` |
| Verdict | **NOT READY — acceptance not demonstrated.** Backend, API, database and full-stack E2E suites have not been executed yet, so no Must-have requirement is validated by a passing test. |

## 1. Execution results (actual runs only)

| Suite | Command | Executed | Passed | Failed | Evidence |
|---|---|---|---|---|---|
| Frontend unit / component (Vitest) | `cd frontend; npx vitest run` | Yes | **63** | 0 | Console: 4 files, 63 tests passed |
| Frontend UI E2E, mocked API (Playwright, Edge) | `PW_CHANNEL=msedge npx playwright test --project=ui` | Yes | **6** | 0 | TC-E2E-UI-01..06 passed (layout, phone width, side-by-side receipt, keyboard reject) |
| Backend unit (JUnit 5) | `cd backend; .\mvnw.cmd test` | **No** | – | – | QA laptop has JDK 17; JDK 21 required. Last team run: 131 run, 130 passed, 1 failed (temp-file issue, fixed, not re-run) — see `PROGRESS.md` |
| API integration (pytest) | `pytest tests -q` | **No** (180 collected, 180 skipped) | – | – | Docker daemon not running; API unreachable |
| DB consistency / append-only audit | `pytest tests/integration` | **No** | – | – | Needs compose `postgres` |
| Full-stack E2E (Playwright) | `npm run test:e2e:stack` | **No** | – | – | Needs the stack |
| Security checklist (NFR-009) | `architecture/security-review.md` | Static review only | – | – | 3 findings fixed in code, 5 open |

## 2. Test case workbook (`deliverables/test-cases.xlsx`)

| Metric | Count |
|---|---|
| Total written test cases | 131 (47 Unit, 66 API, 8 Integration, 10 E2E) |
| Passed | 0 |
| Failed | 0 |
| Blocked | 0 (environment-blocked, recorded as Not Run) |
| Not run | **131** |
| Automated tests outside the workbook that passed | 69 (63 Vitest + 6 UI Playwright) |

## 3. Requirement coverage

| Measure | Value |
|---|---|
| Must-have FR-001..FR-020 with at least one written test case | 20 / 20 (100 %) |
| POL-001..POL-009 with written cases and automated pytest tests | 9 / 9 (100 %) |
| Requirements validated by a **passing** test against the running system | **0 / 29** |
| Requirements with passing frontend-only evidence | NFR-001, NFR-002, NFR-003 (layout, mocked API) |

## 4. Defects

| Severity | Open |
|---|---|
| Critical | 0 known (backend not yet exercised — unknown, not zero risk) |
| High | 0 |
| Medium | 2 (DEF-001, DEF-002) |
| Low | 3 |

See `defect-report.md`.

## 5. What must happen before "ready"

1. Start Docker Desktop; `docker compose up -d --build`.
2. Run `pytest tests -q`, `npm run test:e2e:stack`, and JUnit (CI or JDK 21); record results in the workbook.
3. Fix or triage every failure; re-run the regression set.
4. Confirm ASM-021 and ASM-026 with the owner.
