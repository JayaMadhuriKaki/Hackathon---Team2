---
name: requirement-analysis
description: Analyse project requirements and use cases into numbered functional and non-functional requirements, actors, user journeys, business rules, assumptions, ambiguities, user stories with acceptance criteria, and a requirement traceability matrix. Use this skill whenever anyone reads, interprets, clarifies, prioritises or changes requirements — including requirements.docx, use-cases.docx, a new answer from the project owner, a scope question, writing user stories or acceptance criteria, or updating requirement-traceability.xlsx or user-stories.xlsx — even if the word "requirements" is not used.
---

# Requirement Analysis

Turn the supplied documents into a requirement baseline that every later phase (architecture, API, build, test, demo) can trace back to. The value of this skill is precision: a wrong or invented requirement here silently propagates into code, tests and the demo, so it is far cheaper to ask now than to fix later.

## Responsibilities
1. Read every requirement and use-case document.
2. Extract functional (FR) and non-functional (NFR) requirements.
3. Identify actors and their user journeys.
4. Identify business rules (policy limits, thresholds, state rules, visibility rules).
5. Identify assumptions, dependencies and ambiguities.
6. Write user stories with acceptance criteria.
7. Create and maintain the requirement traceability matrix.

## Inputs
| Input | Location | Required? |
|---|---|---|
| Engineering standards, confirmed decisions (CL-xx), open questions (Q-xx), assumptions (ASM-xxx) | `CLAUDE.md` (root) — §1, §2, §3, §6 | Yes |
| Problem statement and must-have scope | `requirements/requirements.docx` | Yes |
| Use cases | `requirements/use-cases.docx` | If present |
| Event rules and tech constraints | `requirements/AI_Manthan_Hackathon___Prerequisites_Guide.docx` | If present |
| Existing analysis | `requirements/requirement-analysis.md` | If present |
| Existing workbooks | `deliverables/requirement-traceability.xlsx`, `deliverables/user-stories.xlsx` | If present |
| New answers from the project owner | The current conversation | When given |

Read `.docx` files with `pandoc -t markdown <file>` (or python-docx). Read `.xlsx` files with openpyxl before writing to them.

## Inspect the repository first
Before writing anything:
1. Run `git status` and `git log --oneline -10`. Do not touch uncommitted user changes; ask how to proceed if they affect your files.
2. List `requirements/` and `deliverables/` and open every file you intend to change. Note who last changed it (`git log -1 -- <file>`).
3. Read `CLAUDE.md` §2 (documented requirements), §2.6 (confirmed clarifications), §6 (open questions and assumptions). These already contain assigned IDs — **reuse them; never renumber existing IDs**, because code, tests and workbooks reference them.
4. Open the existing workbooks and record their sheet names and column headers. Preserve them exactly; add rows, do not restructure.

## Step-by-step execution
1. **Read all sources completely.** List each document with its date/version in the analysis header.
2. **Extract requirements.** For each statement in the source, write one atomic requirement: `FR-###: The system shall <verb> <object> <condition>.` Quote or cite the source section for each. One behaviour per ID.
3. **Extract NFRs** only where the source states or clearly implies a quality (mobile-friendly, append-only, one-command deploy, metrics). Do not invent numeric targets; if a target is needed, raise a question.
4. **Identify actors.** For each persona, list goals, permissions and what they must never see or do.
5. **Map user journeys.** For each actor, write the end-to-end journey as numbered steps (e.g. employee: create draft → add lines → upload receipts → submit → see outcome → respond to send-back). Mark which steps are in the demo storyline.
6. **Catalogue business rules** separately from FRs (`POL-###` for policy rules, or reference the CL-xx that defines them). Record the exact value, unit and boundary (">" vs "≥") as written in the source. Rules that are configuration (limits, thresholds, rates) must be marked as such.
7. **Find gaps.** For every requirement ask: what is the trigger, the actor, the data, the boundary, the failure case, the visibility? Anything unanswered becomes:
   - a **question** (`Q-##`) if it affects money, approval routing, access control, audit or payout — these are business-critical; stop work that depends on them;
   - an **assumption** (`ASM-###`) if it is low-risk, clearly flagged for confirmation.
   Also record **conflicts** between statements (e.g. "auto-approve" vs "always route to manager").
8. **Record dependencies** — other requirements, data (seed data, bank details), external systems, and event constraints.
9. **Write user stories** (`US-###`): *As a <actor>, I want <capability>, so that <benefit>.* Each story links to one or more FR/POL IDs and has acceptance criteria in **Given / When / Then** form covering the happy path, at least one negative case and every documented boundary.
10. **Prioritise** with MoSCoW. Everything in the source's must-have scope is *Must*. Do not downgrade a documented must-have without owner approval.
11. **Build the traceability matrix** in `deliverables/requirement-traceability.xlsx` (sheet `RTM`): one row per FR/NFR/POL with ID, type, description, source, priority, use case, user story IDs. Leave design/API/code/test columns for later phases, but never delete what later phases have filled in.
12. **Update user stories** in `deliverables/user-stories.xlsx` (sheet `Stories`), keeping its existing columns.
13. **Write up** `requirements/requirement-analysis.md` (all sections) and add new questions/assumptions to `CLAUDE.md` §6 — editing CLAUDE.md needs user approval (it is in the `ask` list), so propose the exact diff.
14. **Report** a summary: counts by type and priority, new questions (critical first), new assumptions, and what now blocks architecture.

## Expected outputs
- `requirements/requirement-analysis.md` — complete, every requirement sourced.
- `deliverables/requirement-traceability.xlsx` — RTM rows for every requirement; Summary formulas intact.
- `deliverables/user-stories.xlsx` — stories with Given/When/Then acceptance criteria.
- Proposed additions to `CLAUDE.md` §6 (questions and assumptions).
- A short chat summary with the open questions to send to the project owner.

## Quality checklist
- [ ] Every FR/NFR/POL cites its source (document + section) or a CL-xx decision
- [ ] Existing IDs unchanged; new IDs continue the sequence
- [ ] Each requirement is atomic, testable and free of vague words ("fast", "easy") without a measure
- [ ] Every boundary is explicit (">" vs "≥", per line vs per claim vs per day)
- [ ] Every Must-have has Given/When/Then criteria including a negative and a boundary case
- [ ] Every actor has a journey; every journey step maps to an FR
- [ ] Every open question is classified critical / non-critical
- [ ] Workbook sheet names, headers, dropdowns and formulas preserved; example row still marked as example
- [ ] No requirement exists only in the workbook or only in the markdown — they match

## Rules for avoiding assumptions
- **If it is not in the source documents or a confirmed CL-xx decision, it is not a requirement.** Do not "complete" a feature with behaviour that seems sensible.
- Never infer a numeric value, role permission, state, or field that the source does not state. Raise `Q-##` instead.
- When an owner's answer is ambiguous, record your interpretation explicitly ("Interpreted as …") and ask for confirmation rather than silently choosing.
- Do not resolve a conflict between two sources yourself; present both readings.
- Do not mark an assumption as confirmed until the owner confirms it in writing.
- Use the project's own terms (claim, line item, payout batch) — do not rename domain concepts.

## Hand-off
Architecture work (solution-architecture skill) starts only when every **critical** question affecting the scope being designed is answered.
