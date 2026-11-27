# Demo Script

| Field | Value |
|---|---|
| Project | `<Project Name>` |
| Slot length | `<5>` minutes + `<3>` minutes Q&A |
| Presenter | `<Name>` |
| Driver (on keyboard) | `<Name>` |
| Demo URL | `<url or http://localhost:3000>` |
| Demo login | `demo@example.com / <password>` |
| Backup video | `demo/demo-video/<file>.mp4` |

## One-Sentence Pitch
`<For [user] who [problem], [product] is a [category] that [key benefit]. Unlike [alternative], we [differentiator].>`

## Running Order
| Time | Section | Speaker | On screen / action | Talking points | Judging criterion | Req IDs |
|---|---|---|---|---|---|---|
| 0:00–0:30 | Hook & problem | | Title slide | Persona + a striking number | Business impact | |
| 0:30–1:00 | Solution overview | | Landing screen | What it is, who it's for | Innovation | |
| 1:00–1:45 | Demo step 1 | | `<click path>` | | UX | FR-001 |
| 1:45–2:30 | Demo step 2 | | | | Technical | FR-002 |
| 2:30–3:15 | Demo step 3 (wow moment) | | | | Innovation | FR-003 |
| 3:15–3:30 | Result / outcome | | Dashboard / result | Measurable benefit | Business impact | |
| 3:30–4:15 | Under the hood | | Architecture diagram | Stack, AI/hard part, scale story | Technical | |
| 4:15–4:45 | Impact & roadmap | | Roadmap slide | Who pays, next 3 features | Business impact | |
| 4:45–5:00 | Close | | Team slide | Thank you + ask | Presentation | |

## Detailed Steps
### Step 1 — `<name>`
- **Action:** `<exact clicks / inputs>`
- **Expected screen:** `<what judges see>` (screenshot: `demo/screenshots/01-<name>.png`)
- **Say:** "<script line>"
- **If it fails:** `<fallback — switch to screenshot / video at mm:ss>`

### Step 2 — `<name>`
- **Action:**
- **Expected screen:**
- **Say:**
- **If it fails:**

### Step 3 — `<name>`
- **Action:**
- **Expected screen:**
- **Say:**
- **If it fails:**

## Demo Data
| Data | Value | Reset command |
|---|---|---|
| Demo user | demo@example.com | `<seed command>` |
| | | |

## Anticipated Judge Questions
| Question | Answer (≤ 30 s) | Who answers |
|---|---|---|
| How does this scale? | | |
| What about data privacy/security? | | |
| What was technically hardest? | | |
| What's real vs mocked? | | |
| What's the business model? | | |
| What would you build next? | | |

## Pre-Demo Checklist
- [ ] Laptop charged + charger; HDMI/USB-C adapter
- [ ] App running locally as fallback; seed data reset
- [ ] Browser: zoom 125%, bookmarks bar hidden, tabs pre-opened, logged in
- [ ] Notifications / chat apps off; Do Not Disturb on
- [ ] Backup video and screenshots open in another window
- [ ] Timer ready; rehearsed at least twice
- [ ] All demo steps marked Pass in `validation-matrix.xlsx`
