# Architecture Diagrams

All diagrams use [Mermaid](https://mermaid.js.org/) so they render in GitHub, GitLab, VS Code and most Markdown viewers. Replace the placeholder names with real components.

## 1. System Context
```mermaid
flowchart LR
    user([End User]) -->|uses| app[Hackathon Solution]
    admin([Admin]) -->|manages| app
    app -->|calls| ext1[(External API)]
    app -->|uses| ai[[AI / LLM Service]]
```

## 2. Component Diagram
```mermaid
flowchart TB
    subgraph Client
        FE[Web Frontend<br/>React SPA]
    end
    subgraph Server
        API[API Service<br/>REST /api/v1]
        SVC[Business Services]
        REPO[Repositories]
        AUTH[Auth Middleware]
    end
    DB[(Database)]
    EXT[External API<br/>mockable]
    LLM[AI Service]

    FE -->|HTTPS JSON| API
    API --> AUTH --> SVC
    SVC --> REPO --> DB
    SVC --> EXT
    SVC --> LLM
```

## 3. Sequence — Main Demo Flow
```mermaid
sequenceDiagram
    actor U as User
    participant FE as Frontend
    participant API as API Service
    participant DB as Database
    participant EXT as External/AI Service

    U->>FE: Performs main action
    FE->>API: POST /api/v1/<resource>
    API->>API: Validate request + auth
    API->>EXT: Enrich / process
    EXT-->>API: Result
    API->>DB: Save record
    DB-->>API: Saved
    API-->>FE: 201 Created + body
    FE-->>U: Shows result
```

## 4. Deployment
```mermaid
flowchart LR
    subgraph Host["Demo Host / Docker Compose"]
        FEc[frontend container<br/>:3000]
        BEc[backend container<br/>:8000]
        DBc[(db container<br/>:5432)]
    end
    Browser --> FEc --> BEc --> DBc
    BEc -.-> Ext[External services]
```

## 5. Notes
- Keep diagrams in sync with `solution-design.md`.
- Export PNGs to `demo/screenshots/` for slides if needed (`npx @mermaid-js/mermaid-cli -i architecture-diagram.md`).
