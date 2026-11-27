---
name: frontend-development
description: Implement the React 19 + Vite frontend for approved requirements — reusable components, routing and workflows, form validation, loading/empty/success/error states, duplicate-submit prevention, API integration through the existing client, responsive and accessible layouts, and frontend tests. Use this skill for any work under frontend/, any screen, form, table, navigation or styling change, wiring the UI to an API, or writing component or Playwright tests — even for a "small UI tweak".
---

# Frontend Development

Build screens that let each persona complete their journey quickly and safely: mobile-friendly claim capture, a side-by-side receipt viewer for approvers, and dense, scannable tables with bulk actions. The frontend improves usability; it is never the security boundary — the backend enforces every rule.

## Responsibilities
1. Implement UI based on approved requirements.
2. Create reusable components.
3. Implement navigation and workflows.
4. Implement form validation.
5. Implement loading, success, empty and error states.
6. Integrate with backend APIs.
7. Create responsive layouts.
8. Implement frontend tests.

## Inputs
| Input | Location |
|---|---|
| Frontend standards, accessibility, required screens | `CLAUDE.md` §10, §2.4 (UI and UX phase), NFR-001–003 |
| User stories and acceptance criteria | `deliverables/user-stories.xlsx`, `requirements/requirement-analysis.md` |
| API contract | `architecture/api-contracts.yaml` |
| Approved plan for the task | The current conversation |

## Inspect the repository first
1. `git status` / `git log --oneline -10`; do not overwrite uncommitted work.
2. Read `frontend/package.json` (React, Vite, router, linter — currently **oxlint**; use the project's linter, do not add ESLint unless approved), `vite.config.js` (dev proxy for `/api`), `nginx.conf`, `index.html`.
3. Read the existing structure and reuse it: `src/api/client.js` (the single fetch wrapper and `ApiError`, which reads RFC 9457 `detail`/`title`), `src/components/Layout.jsx`, `src/pages/*`, `src/App.jsx` (routes), `src/index.css` (styles/tokens).
4. Search before creating: `grep -rn "<ComponentName>\|function <name>" frontend/src` — extend an existing component rather than building a near-duplicate.
5. Check whether JavaScript or TypeScript is in use and stay with it.
6. Check whether a component-test setup exists (Vitest + React Testing Library per CLAUDE.md §8.1 — "Proposed"). If not, propose adding it in your plan; do not install it unapproved.

## Step-by-step execution
1. **Plan first** (CLAUDE.md §14.2): requirement/story IDs, screens and components to add or change, API operations used, new dependencies (or "none"), tests. Wait for approval.
2. **Screen map**: for each story, define route, page component, child components, data needed (operation IDs) and actions. Add routes in `App.jsx`; protect role-specific routes (UI only — backend still enforces).
3. **Reusable components first**: e.g. `FormField`, `MoneyInput`/`MoneyDisplay` (INR, Indian grouping), `DateInput`, `StatusBadge`, `DataTable` (sorting, filtering, row selection, bulk actions), `ReceiptViewer` (side-by-side), `ConfirmDialog` (with mandatory comment for reject/send-back), `EmptyState`, `ErrorState`, `LoadingState`, `Toast`. Place them in `src/components/`.
4. **API integration**: add one module per resource in `src/api/` (e.g. `claims.js`) that calls the existing `api` client. Function names and payload field names match the contract exactly. Handle `ApiError` centrally; show field errors from the problem-detail `errors` map next to the fields.
5. **Forms and validation**: mirror the contract's rules (required, types, min/max, lengths, file type/size). Validate on blur and on submit; show messages linked to fields (`aria-describedby`). Keep the user's input on error.
6. **States** on every data screen: loading (skeleton or spinner), empty (helpful message + next action), error (meaningful message + retry), success (confirmation). Never show raw stack traces or server internals.
7. **Duplicate-submit prevention**: disable the triggering button and show progress while a request is in flight; for submit/approve/bulk approve/export, also guard against double-clicks in the handler.
8. **Responsive layout**: claim capture must work at phone width (~375px) and on a 1280×720 projector; tables scroll horizontally inside their container, not the page.
9. **Accessibility**: semantic HTML, labels for every input, keyboard operation for tables, dialogs and bulk actions, visible focus, contrast ≥ 4.5:1, focus moved into dialogs and back.
10. **Tests** (in `frontend/` test setup or `tests/frontend/` as the repo convention dictates):
    - component tests for forms (validation messages, disabled submit while pending), state rendering, tables and bulk selection;
    - Playwright E2E for demo journeys (`tests/frontend/e2e/` or the agreed path), using Edge/Chrome channel if browser download is blocked.
    Include positive, negative, boundary and edge cases (e.g. amount 0, exactly 500 INR receipt boundary in the UI hint, very long vendor name).
11. **Run** `npm run lint`, `npm run build`, and the tests. Report the actual commands and results.
12. **Update** traceability ("Code Module / File" column), and `PROGRESS.md`; commit a checkpoint.

## Expected outputs
- Pages, components and API modules under `frontend/src/`
- Component tests and Playwright specs
- Updated RTM rows and `PROGRESS.md`
- Lint, build and test results in the task summary

## Quality checklist
- [ ] Reused existing client, layout and components; no duplicate components
- [ ] Field names and validation match the API contract exactly
- [ ] Every data screen has loading, empty, success and error states
- [ ] Buttons disabled during in-flight requests; no double submits possible
- [ ] Works at phone width and projector resolution
- [ ] Keyboard-only walkthrough of the journey succeeds; labels and focus correct
- [ ] No secrets, tokens or API URLs hard-coded; base URL from env/proxy
- [ ] No console errors or warnings on the demo path
- [ ] Lint, build and tests actually run and pass; results reported

## Rules for avoiding assumptions
- Build only screens and fields that trace to an approved requirement or story; do not add "nice to have" features.
- Never invent API fields, endpoints or response shapes — if the contract lacks something, stop and use the api-design skill.
- Do not encode business rules (limits, thresholds, routing) in the UI as the source of truth; at most show hints that come from the API or config.
- Do not add libraries (UI kits, state managers, form libraries) without approval and a stated reason.
- If a UX behaviour is unspecified (e.g. what happens after submit), propose it in the plan and wait for approval.
