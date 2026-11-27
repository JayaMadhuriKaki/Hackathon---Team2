---
name: api-design
description: Design REST APIs contract-first in OpenAPI 3.0 with consistent naming, error handling, pagination and security, and keep the API inventory in sync. Use when creating or changing architecture/api-contracts.yaml or deliverables/api-inventory.xlsx, or before adding or changing any endpoint.
---

# API Design

## Inputs
- Use cases and FRs from `requirements/requirement-analysis.md`
- Data model from `architecture/database-design.md`

## Outputs
- `architecture/api-contracts.yaml` (OpenAPI 3.0)
- `deliverables/api-inventory.xlsx` — one row per endpoint

## Conventions
| Topic | Rule |
|---|---|
| Base path | `/api/v1` |
| Resources | Plural nouns, kebab-case: `/api/v1/order-items` |
| Methods | GET read, POST create, PUT replace, PATCH partial update, DELETE remove |
| IDs | Path params: `/users/{userId}` |
| JSON fields | camelCase |
| Dates | ISO-8601 UTC strings |
| Pagination | `?page=1&pageSize=20`; response has `items`, `page`, `pageSize`, `total` |
| Filtering/sorting | `?status=active&sort=-createdAt` |
| Errors | Always `{ "code", "message", "details" }` using the shared `Error` schema |
| Auth | Bearer JWT via `securitySchemes`; mark public endpoints with `security: []` |
| Status codes | 200, 201 (+Location), 204, 400, 401, 403, 404, 409, 422, 500 |

## Steps
1. For each use case, list the resources and operations it needs.
2. Define reusable schemas in `components/schemas` (request and response models, `Error`).
3. Write each path with `operationId`, summary, tags, parameters, request body, all response codes and an example.
4. Give each endpoint an `API-###` ID in `x-api-id` and list the FRs it serves in `x-requirements`.
5. Validate the file (e.g. `npx @redocly/cli lint architecture/api-contracts.yaml`).
6. Update `deliverables/api-inventory.xlsx` and the traceability matrix "API" column.

## Quality checklist
- [ ] Every endpoint has an operationId, examples and error responses
- [ ] No verbs in paths (`/getUsers` ✗)
- [ ] Lint passes with no errors
- [ ] Inventory row count equals the number of operations in the contract
