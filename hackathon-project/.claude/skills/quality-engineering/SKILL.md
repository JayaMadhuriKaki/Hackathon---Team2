---
name: quality-engineering
description: Plan and run testing — unit, API, integration and E2E — derive test cases from requirements, log results and defects, and maintain the test-case and validation-matrix deliverables. Use when writing tests, running validation, or updating tests/ or deliverables/test-cases.xlsx and validation-matrix.xlsx.
---

# Quality Engineering

## Inputs
- Acceptance criteria in `requirements/requirement-analysis.md`
- `architecture/api-contracts.yaml`
- Running frontend and backend

## Outputs
- Automated tests in `tests/frontend`, `tests/backend`, `tests/integration`
- Test data in `tests/test-data`
- `deliverables/test-cases.xlsx` (cases + execution results)
- `deliverables/validation-matrix.xlsx` (requirement → test → result)

## Test strategy (hackathon-sized)
| Level | What | Target |
|---|---|---|
| Unit | Services, utilities, components | Key logic covered |
| API | Each endpoint: happy path + main errors (400/401/404) | 100% of endpoints |
| Integration | Backend + DB, external mocks | Demo flows |
| E2E | Full demo storyline in a browser | 100% of demo steps |
| Non-functional | Response time, basic security checks, accessibility scan | Each NFR |

## Steps
1. Derive test cases from each acceptance criterion: positive, negative, boundary.
2. Give each case an ID (`TC-FE-###`, `TC-BE-###`, `TC-INT-###`) and link it to FR/NFR IDs.
3. Prepare test data files in `tests/test-data/` (valid, invalid, edge cases). No real personal data.
4. Automate the highest-value cases first: the demo path.
5. Run the suite; record Pass/Fail, date and tester in `test-cases.xlsx`.
6. Log failures as `BUG-###` with steps to reproduce, severity and status.
7. Update `validation-matrix.xlsx` so every Must-have requirement shows a passing test.
8. Do a full demo dry run on the demo machine and network.

## Exit criteria
- [ ] All Must-have requirements validated (Pass)
- [ ] No open Critical/High defects on the demo path
- [ ] Demo dry run completed twice without intervention
