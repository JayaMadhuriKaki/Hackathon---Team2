# QA Summary — ExpenseFlow

| Field | Value |
|---|---|
| Date | 2026-11-27 |
| Code under test | `main` at `cb8672d` (backend PR #3, frontend PR #4) |
| QA branch | `test/qa-automation` |
| Verdict | **NOT READY.** Backend, API, database and full-stack E2E suites have not been executed by QA (Docker unavailable on the QA laptop; JDK 17 instead of 21). One High defect is open (DEF-006: synthetic seed missing). No Must-have requirement is fully validated. |

## 1. Execution results (actual runs only)

| Suite | Command | Executed | Passed | Failed | Evidence |
|---|---|---|---|---|---|
| Frontend unit / component (Vitest) | `cd frontend; npx vitest run` | Yes | **63** | 0 | `demo/test-evidence/vitest-2026-11-27.txt` |
| UI E2E with mocked API (Playwright, Edge) | `PW_CHANNEL=msedge npx playwright test --project=ui` | Yes | **6** | 0 | `demo/test-evidence/playwright-ui-2026-11-27.txt` |
| Frontend lint + build | `npm run lint; npm run build` | Yes | Build OK | 0 errors | `demo/test-evidence/frontend-lint-build-2026-11-27.txt` (5 warnings → DEF-007) |
| Dependency check (frontend) | `npm audit` | Yes | **0 vulnerabilities** | – | `demo/test-evidence/npm-audit-2026-11-27.txt` |
| Backend unit (JUnit 5) | `cd backend; .\mvnw.cmd test` | **No** | – | – | QA laptop has JDK 17. Runs in CI on the PR. Last team run: 131 run, 130 passed, 1 failed (fixed, not re-run) |
| Dependency check (backend, OWASP) | `mvnw dependency-check:check` | **No** | – | – | Needs JDK 21 and not in CI (DEF-001) |
| API integration, business rules, negative, boundary, authN/authZ (pytest) | `pytest tests/backend -q` | **No** | – | – | 169 tests written; `demo/test-evidence/pytest-collect-2026-11-27.txt` shows they collect |
| Database consistency / append-only audit (pytest) | `pytest tests/integration -q` | **No** | – | – | 11 tests written |
| Full-stack E2E (Playwright) | `npm run test:e2e:stack` | **No** | – | – | 11 tests written by the frontend team |
| Security checks | `architecture/security-review.md` | Static review + npm audit | – | – | 3 earlier findings fixed in code; 5 open (DEF-001..005) |
| Regression | All of the above | Frontend only | 69 | 0 | Backend regression not run |

## 2. Test case workbook (`deliverables/test-cases.xlsx`)

| Metric | Count |
|---|---|
| Total test cases | **131** (47 Unit, 66 API, 8 Integration, 10 E2E) |
| Automated (test code exists) | 118 |
| Passed | **0** |
| Failed | **0** |
| Blocked | **3** (seed-data cases, DEF-006) |
| Not run | **128** |

Separately executed automated tests (not workbook rows): 69 passed, 0 failed (63 Vitest + 6 Playwright UI).

## 3. Requirement coverage (`deliverables/validation-matrix.xlsx`, 84 requirements: FR-001–032, NFR-001–021, BR-001–031)

| Measure | Value |
|---|---|
| Requirements with at least one written test | 71 / 84 (85 %) |
| Validated (passing test against the running system) | **0 / 84 (0 %)** |
| Partially validated (frontend tests passed, backend not run) | 29 / 84 |
| Not validated | 55 / 84 |
| No test at all | 13: FR-027, FR-028, FR-030 (seed missing), NFR-006–010, NFR-012–015, NFR-017 (checked by inspection or later phases) |

## 4. Defects (`deliverables/defect-report.md`, Defects sheet in `test-cases.xlsx`)

| Severity | Open |
|---|---|
| Critical | **0 known** (backend not yet exercised, so this is not evidence of zero) |
| High | **1** (DEF-006 synthetic seed missing) |
| Medium | 2 (DEF-001, DEF-002) |
| Low | 4 (DEF-003, DEF-004, DEF-005, DEF-007) |

## 5. Before the application can be declared ready

1. Run the backend suites on a machine with Docker (pytest API + DB, full-stack E2E) and record results.
2. Confirm JUnit passes in CI on this PR.
3. Implement the synthetic seed (DEF-006) and run the seed-data cases.
4. Fix or accept DEF-001 and DEF-002; re-run the full regression.
5. Owner confirms ASM-021 and ASM-026.
