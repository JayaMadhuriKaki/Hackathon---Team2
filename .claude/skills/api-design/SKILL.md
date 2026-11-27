---
name: api-design
description: Design and maintain the REST API contract-first in OpenAPI 3 — identify every frontend-to-backend interaction, define endpoints, request/response schemas, status codes, the shared error contract, field-level validation, authentication/authorization per operation, and keep frontend, backend and the API inventory in sync. Use this skill before adding or changing any endpoint, DTO, API client call, or architecture/api-contracts.yaml, and whenever frontend and backend disagree about a field, status code or error.
---

# API Design

The OpenAPI contract is the agreement between frontend, backend and tests. If it is written first and kept true, the two halves can be built in parallel and pytest can test the API from the contract alone. If it drifts, bugs hide in the gap.

## Responsibilities
1. Identify all frontend-to-backend interactions.
2. Define REST endpoints where appropriate.
3. Define request and response schemas.
4. Define status codes and the error contract.
5. Define field-level validation.
6. Generate the OpenAPI specification.
7. Identify authentication and authorization requirements.
8. Ensure consistency between frontend and backend.

## Inputs
| Input | Location |
|---|---|
| API standards, naming, error shape, security rules | `CLAUDE.md` §9.4, §11, §12, §13 |
| Requirements, user stories, journeys | `requirements/requirement-analysis.md`, `deliverables/user-stories.xlsx` |
| Data model, state machine, ADRs | `architecture/database-design.md`, `architecture/architecture-diagram.md`, `architecture/adr/` |
| Current contract | `architecture/api-contracts.yaml` |
| Existing endpoints and client | `backend/src/main/java/**/` controllers and DTOs; `frontend/src/api/` |
| API inventory | `deliverables/api-inventory.xlsx` |

## Inspect the repository first
1. `git status` / `git log --oneline -10`.
2. Read `architecture/api-contracts.yaml`. If it still contains the template placeholder (`<Project Name>`, `/items`), treat it as a template to **replace**, not extend.
3. List every existing controller (`grep -rn "@RestController\|@RequestMapping\|@GetMapping\|@PostMapping" backend/src/main`) and every frontend call (`grep -rn "api\.\(get\|post\|put\|patch\|delete\)" frontend/src`). Existing endpoints (e.g. health) must appear in the contract.
4. Read the error handler (`GlobalExceptionHandler`) and `application.properties` to learn the **actual** error format (currently RFC 9457 `ProblemDetail`: `status`, `title`, `detail`, plus extension properties such as `errors`). The contract must describe what the code returns. If CLAUDE.md specifies a different shape, use the shape agreed in the error-format ADR; if no ADR exists, raise it — recommended resolution: keep RFC 9457 and add `code` and `traceId` as extension properties.
5. Note the base path used by the frontend proxy (`/api` → backend) and the backend's own mapping, so paths in the contract match reality.

## Step-by-step execution
1. **List interactions.** Walk every user journey screen by screen and list each data read and action (e.g. save draft, upload receipt, submit, approve, send back, reject with comment, delegate, bulk approve, create payout batch, export CSV, view dashboard, view audit trail). Each gets an `API-###` ID.
2. **Design resources and operations.** Plural kebab-case nouns, versioned base path (`/api/v1`). State changes on claims are explicit action sub-resources where a plain PATCH would hide business meaning (e.g. `POST /claims/{claimId}/submit`, `/approve`, `/send-back`, `/reject`). Keep it REST where it fits; do not force it where it doesn't.
3. **Define schemas** in `components/schemas`: separate create/update request DTOs from response DTOs; never expose internal fields (password hashes, storage paths, internal flags). Use camelCase, ISO-8601 dates, money as a string or integer minor units — pick one per ADR and use it everywhere.
4. **Field-level validation** on every request property: `type`, `format`, `required`, `minLength`/`maxLength`, `minimum`/`exclusiveMinimum`, `pattern`, `enum`. For uploads: allowed media types and maximum size. These must map 1:1 to Bean Validation annotations on the backend DTOs and to frontend form rules.
5. **Status codes and errors** for every operation: success code(s) and every applicable error — 400, 401, 403, 404, 409 (state conflict), 413, 415, 422 (business rule), 429 — each referencing the shared error schema with an example.
6. **Security**: declare the security scheme; per operation add `security` (or `security: []` for public), `x-roles` (allowed roles), object-level rule in the description (e.g. "employee: own claims only; approver: assigned claims"), and `x-rate-limit` where applicable (login, upload).
7. **Traceability extensions** on every operation: `operationId`, `tags`, `x-api-id`, `x-requirements` (FR/POL/CL IDs).
8. **Idempotency/duplicate submits**: document how repeated submit/approve/export requests behave (409 on wrong state, or an idempotency key header) — consistent with CLAUDE.md §10.5.
9. **Lint** the contract (e.g. `npx @redocly/cli lint architecture/api-contracts.yaml`) and fix all errors.
10. **Inventory**: update `deliverables/api-inventory.xlsx` (sheet `APIs`) — one row per operation; row count must equal operation count.
11. **Consistency check** (repeat whenever code changes): compare contract ↔ backend controllers/DTOs ↔ frontend `src/api` calls. List every mismatch and fix the contract first, then the code.
12. **Traceability**: fill the "API ID(s)" column in the RTM.

## Expected outputs
- `architecture/api-contracts.yaml` — complete, linted OpenAPI 3 contract
- `deliverables/api-inventory.xlsx` — one row per operation
- Updated "API ID(s)" column in `deliverables/requirement-traceability.xlsx`
- A consistency report (contract vs backend vs frontend) in the chat summary

## Quality checklist
- [ ] Every user-journey action has an operation; no operation exists without a requirement
- [ ] Every operation has operationId, tags, x-api-id, x-requirements, x-roles, examples
- [ ] Every request field has type and validation; required fields listed
- [ ] Every operation lists all applicable error codes using the one shared error schema
- [ ] Error schema matches what the backend actually returns
- [ ] Paths match the frontend proxy and backend mapping
- [ ] Lint passes; inventory row count = operation count
- [ ] No sensitive field in any response schema

## Rules for avoiding assumptions
- Do not invent endpoints, fields, enum values or business behaviour that no requirement or CL decision supports.
- Do not invent a field because the UI "would be nicer" with it — raise a question.
- Describe the error format the code really uses unless an approved ADR changes it.
- If a rule's boundary or permission is unclear, leave the operation marked `x-status: blocked` with the Q-## reference rather than guessing.
- Changing an existing operation's contract is a breaking change: list the affected frontend calls and tests in the plan and get approval.

## Hand-off
Backend and frontend development use this contract as the source of truth; pytest API tests are derived from it.
