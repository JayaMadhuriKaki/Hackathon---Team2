---
description: Generate the OpenAPI contract and API inventory from use cases and the data model
argument-hint: "[optional resource or use case to focus on]"
---

Use the **api-design** skill.

1. Read the use cases, FRs and `architecture/database-design.md`.
2. Write or update `architecture/api-contracts.yaml` (OpenAPI 3.0) following the conventions in the skill. Tag each operation with `x-api-id` and `x-requirements`.
3. Validate the contract (e.g. `npx @redocly/cli lint architecture/api-contracts.yaml`) and fix all errors.
4. Update `deliverables/api-inventory.xlsx` with one row per operation.
5. Fill the "API" column in the traceability matrix.

Focus: $ARGUMENTS

Finish with an endpoint table (ID, method, path, purpose) and any gaps against the requirements.
