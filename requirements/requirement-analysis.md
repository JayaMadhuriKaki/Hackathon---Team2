# Requirement Analysis

| Field | Value |
|---|---|
| Project | `<Project Name>` |
| Version | 0.1 (draft) |
| Last updated | `<YYYY-MM-DD>` |
| Author(s) | `<Names>` |
| Sources | `requirements.docx`, `use-cases.docx`, organiser briefing |

## 1. Problem Summary
`<In 3–5 sentences: who has the problem, what the problem is, why it matters, and how it is handled today.>`

## 2. Goals and Success Metrics
| # | Goal | Success metric | Target |
|---|---|---|---|
| G1 | `<e.g. Reduce time to X>` | `<Average minutes per task>` | `<from 30 to 5>` |
| G2 | | | |

## 3. Stakeholders and Actors
| Actor | Type (Primary / Secondary / System) | Needs / goals |
|---|---|---|
| `<End user>` | Primary | |
| `<Admin>` | Secondary | |
| `<External API>` | System | |

## 4. Scope
**In scope:** `<features we will build>`

**Out of scope:** `<features we will explicitly NOT build during the hackathon>`

## 5. Functional Requirements
| ID | Requirement (The system shall…) | Actor | Use case | Priority (MoSCoW) | Source |
|---|---|---|---|---|---|
| FR-001 | | | UC-001 | Must | requirements.docx §x |
| FR-002 | | | | Should | |
| FR-003 | | | | Could | |

## 6. Non-Functional Requirements
| ID | Category | Requirement | Measure / target | Priority |
|---|---|---|---|---|
| NFR-001 | Performance | API responses are fast under demo load | p95 < 500 ms | Must |
| NFR-002 | Security | All non-public endpoints require authentication | 401 on missing token | Must |
| NFR-003 | Usability | Core task completed without guidance | ≤ 3 clicks per step | Should |
| NFR-004 | Accessibility | UI meets WCAG 2.1 AA basics | Contrast ≥ 4.5:1, keyboard navigable | Should |
| NFR-005 | Privacy | No real personal data used in demo | Synthetic data only | Must |
| NFR-006 | Availability | Demo runs offline / locally if network fails | Local fallback available | Must |

## 7. Acceptance Criteria (Must-haves)
### FR-001 — `<title>`
- **Given** `<context>` **When** `<action>` **Then** `<expected result>`
- **Given** `<invalid input>` **When** `<action>` **Then** `<error shown>`

### FR-002 — `<title>`
- **Given** … **When** … **Then** …

## 8. User Stories (optional)
| ID | As a… | I want to… | So that… | Linked FR |
|---|---|---|---|---|
| US-001 | | | | FR-001 |

## 9. Data Requirements
| Data item | Source | Volume (demo) | Sensitivity | Notes |
|---|---|---|---|---|
| | `<Provided dataset / generated / API>` | | Low / Medium / High | |

## 10. Integrations and External Dependencies
| System | Purpose | Access available? | Mock for demo? |
|---|---|---|---|
| | | Yes / No | Yes / No |

## 11. Assumptions
| # | Assumption | Impact if wrong | Confirmed by |
|---|---|---|---|
| A1 | | | |

## 12. Open Questions
| # | Question | Asked to | Status | Answer |
|---|---|---|---|---|
| Q1 | | Organisers / mentor | Open | |

## 13. Risks
| # | Risk | Likelihood (H/M/L) | Impact (H/M/L) | Mitigation |
|---|---|---|---|---|
| R1 | External API unavailable | M | H | Mock behind interface + cached responses |
| R2 | Scope too large for time box | H | H | Strict MoSCoW; demo path first |

## 14. Demo Storyline
1. `<Persona>` opens the app and `<does first thing>` — covers FR-…
2. …
3. …
4. Outcome shown: `<measurable benefit>`

## 15. Judging Criteria Mapping
| Criterion | Weight | How we address it | Evidence in demo |
|---|---|---|---|
| Innovation | | | |
| Technical implementation | | | |
| Business impact | | | |
| Presentation / UX | | | |
