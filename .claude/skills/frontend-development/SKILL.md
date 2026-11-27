---
name: frontend-development
description: Build a clean, responsive, accessible frontend that consumes the API contract and showcases the demo flow. Use when writing or changing code in frontend/, designing screens, or wiring the UI to the backend.
---

# Frontend Development

## Inputs
- Use cases and demo storyline (`requirements/requirement-analysis.md`)
- `architecture/api-contracts.yaml`
- Stack from `CLAUDE.md`

## Target structure (React example — adapt as needed)
```
frontend/
  src/
    api/           API client generated from or matching the contract
    components/    reusable UI pieces
    pages/         one per screen/route
    hooks/         data fetching, shared state
    context/       auth, theme
    styles/
    utils/
  public/
  .env.example     VITE_API_BASE_URL etc.
  README.md
```

## Steps
1. Sketch the screen list and navigation from the use cases; map each screen to FRs.
2. Scaffold the app, routing, layout shell and a design system (colours, type scale, spacing).
3. Build one API client module; base URL from env; shared error handling.
4. Build screens in demo order. Each screen handles **loading, empty, error and success** states.
5. Add form validation that mirrors the API schema.
6. Make it responsive (works on a laptop projector at 1280×720 and on mobile).
7. Accessibility basics: semantic HTML, labels, keyboard navigation, contrast ≥ 4.5:1, alt text.
8. Write component and E2E tests in `tests/frontend/` for the demo path.
9. Polish the demo path: realistic seed data, no placeholder text, no console errors.

## Rules
- No hard-coded API URLs or secrets.
- Keep components small; lift state only as far as needed.
- Use the same field names as the API contract.

## Quality checklist
- [ ] Every demo step works end-to-end against the real (or mocked) backend
- [ ] Loading/empty/error states exist on every data screen
- [ ] No console errors or warnings on the demo path
- [ ] Looks good at projector resolution
