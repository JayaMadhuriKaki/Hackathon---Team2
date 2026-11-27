---
name: demo-preparation
description: Prepare and verify the hackathon demo from the completed implementation — pick the most valuable end-to-end journeys, write a timed demo script, prepare realistic non-sensitive demo data, outline the presentation, list screenshots and visual evidence, plan the narrated walkthrough and backup video, and rehearse every step against the actually running application. Use this skill whenever the demo, pitch, presentation, walkthrough, screenshots, demo video, demo data or judging criteria come up.
---

# Demo Preparation

Show judges, in a few minutes, that ExpenseFlow solves the real problem — policy breaches, duplicates and slow approvals — using only features that actually work. A demo built from verified behaviour survives live; a demo built from intentions fails in front of the panel.

## Responsibilities
1. Analyse the completed implementation.
2. Identify the most valuable end-to-end user journeys.
3. Create a demo script.
4. Prepare realistic, non-sensitive demo data.
5. Create a presentation outline.
6. Identify screenshots and visual evidence.
7. Prepare a narrated walkthrough and video production plan.
8. Verify the demo against the actual running application.

## Inputs
| Input | Location |
|---|---|
| Problem, personas, must-haves, phase deliverables, acceptance checklist | `CLAUDE.md` §1, §2, §19; `requirements/requirements.docx` |
| Event rules (synthetic data only) | `CLAUDE.md` §8.2 |
| Validation status | `deliverables/validation-matrix.xlsx`, `deliverables/test-cases.xlsx` |
| Progress and known issues | `PROGRESS.md` |
| Existing demo material | `deliverables/demo-script.md`, `demo/screenshots/`, `demo/demo-video/` |
| Slot length and judging criteria | Ask the team if not in CLAUDE.md |

## Inspect the repository first
1. `git status`, `git log --oneline -20` — know exactly which build you are demoing; tag it once frozen.
2. Read `PROGRESS.md`, the validation matrix and the defect log. Only features with **Pass** status and no open Critical/High defect may be shown live.
3. Start the stack exactly as documented (`docker compose up -d --build`), confirm health (`/actuator/health`), frontend loads, seed data present (150 employees, 8 departments, 3,000 historical claims with planted duplicates and breaches).
4. Read the existing `demo-script.md` template and keep its structure.

## Step-by-step execution
1. **Inventory what works**: list implemented, validated features with their requirement IDs and screens.
2. **Select journeys** by value to the problem statement and judging criteria. Typical strong set:
   - employee captures a multi-line claim on a phone-width screen;
   - policy engine outcomes: auto-approve (≤ 25,000, clean), auto-reject on a limit breach with reasons, duplicate flagged;
   - manager → finance auditor → finance head chain on a large claim, with side-by-side receipt viewer, send-back and reject-with-comment;
   - auditor review queue with filters, bulk approve, and the 10% sample-audit hold;
   - payout batch export as CSV, claims move to Paid;
   - dashboard (spend by category/department/month, violations, average approval time) and the append-only audit trail;
   - Grafana panel with the oldest-pending-approval alert.
   Keep the live path to 3–5 journeys that fit the slot.
3. **Demo data**: synthetic, realistic Indian names, departments, cities (including tier-1 vs other), INR amounts. Prepare named demo users per role, specific claims that trigger each outcome, and a **reset script/command** to restore the starting state. No real people, accounts or receipts.
4. **Demo script** in `deliverables/demo-script.md`: timed running order, speaker and driver, exact clicks and inputs, expected screen per step, talking point, judging criterion and requirement IDs per step, and a fallback (screenshot or video timestamp) for every step.
5. **Presentation outline**: problem (with the pain the source describes), solution in one sentence, architecture (context/container diagram, policy-as-config, approval-chain ADR), live demo, quality evidence (tests run, E2E count, security checklist), SRE (metrics, alert, runbook), impact and next steps, team. Keep it in `deliverables/` (format per team choice).
6. **Visual evidence list**: screenshots named `demo/screenshots/NN-<step>.png` for every demo step, plus test-report, CI run, Grafana and architecture-diagram images.
7. **Narrated walkthrough and video plan**: scene list with duration, narration text, screen action, and resolution (1080p, ≤ 3 minutes unless told otherwise); recording tool must be free/open-source (event rule); store output in `demo/demo-video/`.
8. **Q&A prep**: likely judge questions (bypass prevention, scaling, data privacy, what is mocked, testing depth) with short answers backed by evidence.
9. **Verify against the running app**: execute the script end to end at least twice on the demo machine. Record any step that deviates; fix the script (not the facts) or drop the step. Re-capture screenshots from the verified run.
10. **Pre-demo checklist**: stack up, data reset, browser zoom and windows prepared, notifications off, backup video and screenshots ready, timer.

## Expected outputs
- `deliverables/demo-script.md` — timed, verified script with fallbacks and Q&A
- Presentation outline in `deliverables/`
- Demo data plan and reset command (documented in the script)
- Screenshot list and captured images in `demo/screenshots/`
- Video production plan, and the recorded backup video in `demo/demo-video/`
- Verification notes (date, build/commit, run results) in `PROGRESS.md`

## Quality checklist
- [ ] Every live step uses a feature validated as Pass with no open Critical/High defect
- [ ] Script verified twice against the running app on the demo build; commit recorded
- [ ] Every step has expected screen, talking point, requirement ID and fallback
- [ ] All data synthetic and reset-able; no confidential or real personal data
- [ ] Timing fits the slot with buffer
- [ ] Screenshots and video come from the verified run
- [ ] Each judging criterion is shown at least once

## Rules for avoiding assumptions
- Never script a feature because it "should work" — run it first. If it fails, drop it or fix it through the normal process.
- Do not describe capabilities that are not implemented; label mocks and simplifications honestly.
- Do not invent metrics or business impact figures; use measured numbers (e.g. approval time from the dashboard) or clearly labelled estimates the team agrees.
- If the slot length, judging criteria or presentation format is unknown, ask before building the outline.
