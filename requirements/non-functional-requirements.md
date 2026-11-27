# Non-Functional Requirements

| Field | Value |
|---|---|
| Project | ExpenseFlow — Expense Claims and Policy-Driven Approvals |
| Version | 1.1 — **approved baseline** (project owner, 2026-11-27) |
| Date | 2026-11-27 |
| Produced with | `requirement-analysis` skill |
| Companion files | requirement-analysis.md · functional-requirements.md · non-functional-requirements.md · business-rules.md · user-stories.md · open-questions.md |

Only qualities stated in the source documents or mandated by the project owner are listed. No numeric performance target is documented; that gap is OQ-042.

| ID | Category | Requirement | Source | Priority | User stories / verification |
|---|---|---|---|---|---|
| NFR-001 | Usability | Claim capture is mobile-friendly. | requirements.docx — UI and UX phase | Must | US-009 |
| NFR-002 | Usability | Approvers see each receipt side by side with the claim lines. | requirements.docx — UI and UX phase | Must | US-025 |
| NFR-003 | Usability | Data tables are dense but scannable and support bulk actions. | requirements.docx — UI and UX phase | Must | US-026 |
| NFR-004 | Integrity | The audit trail is append-only: no update or delete is possible through the application or the database account it uses. | requirements.docx — Must-have 7 | Must | US-033 |
| NFR-005 | Security | Data isolation: no user can read another user's claim through the UI or the API unless an approval rule grants it. | requirements.docx — Must-have 7; CL-15 | Must | US-002 |
| NFR-006 | Deployability | The whole system runs with Docker Compose, and a seeded demo environment starts with one command. | requirements.docx — Deployment phase | Must | US-039 |
| NFR-007 | Maintainability | CI runs the automated tests and a dependency vulnerability check. | requirements.docx — Deployment phase | Must | US-038 |
| NFR-008 | Observability | Metrics exist for approval queue size, approval age and page latency, with a dashboard and an alert on the oldest pending approval. | requirements.docx — Support (SRE) phase | Must | US-035, US-036 |
| NFR-009 | Security | A basic web security checklist is run against the code, and each finding is fixed or recorded as a false alarm. | requirements.docx — Testing phase | Must | US-046 |
| NFR-010 | Operability | A runbook covers start-up, health checks, the alert and recovery. | requirements.docx — Support (SRE) phase | Must | US-037 |
| NFR-011 | Configurability | Policy limits are seeded as configuration, not hard-coded. | requirements.docx — Policy rules | Must | US-017 |
| NFR-012 | Compliance | Only synthetic or public data is used; no client or confidential data on laptops or in prompts. | Prerequisites Guide — fixed rules; requirements.docx — Synthetic data | Must | US-034 |
| NFR-013 | Compliance | Only open-source or free tools are used; Jira and company AWS are not used; code stays on the laptop or in the company Git repository. | Prerequisites Guide — fixed rules | Must | Verified by review (constraint) |
| NFR-014 | Technology | The stack and versions are: React 19 + Vite, Spring Boot 4.1.x on JDK 21, PostgreSQL 18 in Docker, JUnit 5 and pytest, Docker Compose, Prometheus + Grafana. | CL-17, CL-18; Prerequisites Guide §3–4 | Must | Verified by review (constraint) |
| NFR-015 | Quality | Tests include a unit test for every policy rule, tests for approval thresholds and delegation, 10 written end-to-end cases and a defect log. | requirements.docx — Testing phase | Must | US-038, US-045 |
| NFR-016 | Security | Every external input is validated and authorised on the server; frontend validation is never relied on for security. | CLAUDE.md §11, §13 | Must | US-001, US-046 |
| NFR-017 | Security | Secrets are never committed; configuration comes from environment variables with a placeholder-only example file. | CLAUDE.md §13; requirements.docx — Deployment phase ('confirm secrets are not committed') | Must | US-046 |
| NFR-018 | Security | Receipt uploads are limited by size and allowed file type, checked by content, stored outside the web root and served only to authorised users. | CLAUDE.md §13.5; limits open (OQ-021) | Must | US-005 |
| NFR-019 | Security | Values in the payout CSV are protected against spreadsheet formula injection. | CLAUDE.md §13.4 | Must | US-030 |
| NFR-020 | Accessibility | Forms and tables are keyboard-accessible, labelled and meet basic contrast requirements. | CLAUDE.md §10 | Should | US-003, US-027 |
| NFR-021 | Accuracy | Money is stored and calculated without floating-point error, and shown in INR with Indian digit grouping. | CLAUDE.md §4.3 | Must | US-003, US-030 |

**Totals:** 21 non-functional requirements.
