---
name: solution-architecture
description: Design a simple, demo-ready solution architecture — components, tech stack, data model, integrations and deployment — traced to requirements. Use when creating or updating architecture/solution-design.md, architecture-diagram.md or database-design.md, or when making technology decisions.
---

# Solution Architecture

## Inputs
- `requirements/requirement-analysis.md` and the traceability matrix
- Team skills and the time available (from `CLAUDE.md`)

## Outputs
- `architecture/solution-design.md`
- `architecture/architecture-diagram.md` (Mermaid diagrams)
- `architecture/database-design.md`
- Decision Log rows in `CLAUDE.md`

## Principles for a hackathon
1. **Boring tech wins.** Pick what the team already knows. A working demo beats an elegant plan.
2. **Fewest moving parts.** Prefer a monolith backend + SPA frontend + one database unless a requirement forces otherwise.
3. **Mock what's expensive.** External APIs, payments and ML training can be stubbed behind an interface with a clear "mock" flag.
4. **Design for the demo path first**, then extend.

## Steps
1. List components (frontend, backend, DB, external services, AI/ML) and each one's responsibility.
2. Choose the tech stack with a one-line reason per choice; record in the Decision Log.
3. Draw a context diagram, a component diagram and a sequence diagram for the main demo flow (Mermaid).
4. Design the data model: entities, attributes, keys, relationships, indexes. Draw an ER diagram.
5. Define integration points and how each is mocked for the demo.
6. Address NFRs: security (auth, input validation, secrets), performance, scalability story, observability.
7. Describe deployment: local run (Docker Compose), demo hosting, environment variables.
8. Record risks and mitigations.
9. Update the traceability matrix "Design Component" column.

## Quality checklist
- [ ] Every Must-have FR is handled by a named component
- [ ] Diagrams render (valid Mermaid)
- [ ] Every entity has a primary key; relationships have cardinality
- [ ] Secrets handling and auth approach are stated
- [ ] A new teammate could run the stack from the deployment section
