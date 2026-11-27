---
name: requirement-analysis
description: Turn a hackathon problem statement into clear, testable, prioritised requirements with IDs, use cases, assumptions and a traceability matrix. Use when reading requirements/requirements.docx or use-cases.docx, or when scope, priorities or acceptance criteria are being defined or changed.
---

# Requirement Analysis

## Inputs
- `requirements/requirements.docx` — problem statement and requirements
- `requirements/use-cases.docx` — actors and use cases
- Judging criteria and constraints from `CLAUDE.md`

## Outputs
- `requirements/requirement-analysis.md` (fully filled in)
- `deliverables/requirement-traceability.xlsx` — one row per requirement

## Steps
1. **Read everything first.** Extract the goal, target users, pain points, constraints (time, tech, data) and judging criteria.
2. **Identify actors** (people and external systems) and what each wants.
3. **Write functional requirements** as `FR-###: The system shall <verb> <object> <condition>.` One behaviour per requirement.
4. **Write non-functional requirements** (`NFR-###`) for performance, security, usability, accessibility, availability and data privacy. Make each measurable (e.g. "API p95 response < 500 ms").
5. **Write acceptance criteria** in Given / When / Then form for every Must-have FR.
6. **Prioritise with MoSCoW.** Only items needed for the demo story are *Must*. Aim for no more than ~40% Musts.
7. **Log assumptions, open questions and risks.** Every ambiguity becomes either a question for the organisers or a written assumption.
8. **Define the demo storyline** — the 3–5 steps judges will see. Check every Must supports it.
9. **Fill the traceability matrix** with FR/NFR ID, description, priority, use case, and leave design/API/test columns for later phases.

## Quality checklist
- [ ] Every requirement is atomic, testable and unambiguous (no "fast", "easy", "user-friendly" without a number)
- [ ] Every Must-have has acceptance criteria
- [ ] Every use case maps to at least one FR
- [ ] Out-of-scope list is explicit
- [ ] Open questions have an owner

## Anti-patterns
- Writing solutions instead of needs ("use Redis") — move to architecture
- Gold-plating: adding features judges won't see
- Silent assumptions
