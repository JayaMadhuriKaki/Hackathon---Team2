# Architecture Diagrams — ExpenseFlow

| Field | Value |
|---|---|
| Version | 2.0 — **proposed, for review and approval** (replaces the template v0.1) |
| Date | 2026-11-27 |
| Basis | Requirement baseline v1.3; repository snapshot `main` @ `fe7ee73` |

All diagrams use Mermaid and render in GitHub, VS Code and most Markdown viewers. The system context and security-boundary diagrams are in `system-context.md`; this file holds the container, component, state, sequence and deployment views.

## 1. Container diagram

```mermaid
flowchart TB
    U(["Users: employee, manager,<br/>finance auditor, finance head"])
    subgraph Host["Demo laptop — Docker Compose"]
        FE["frontend<br/>nginx + React 19 SPA<br/>:3000"]
        BE["backend<br/>Spring Boot 4.1 / JDK 21<br/>:8080"]
        DB[("postgres<br/>PostgreSQL 18<br/>:5433")]
        RV[["receipts volume"]]
        EV[["exports volume"]]
        SD["seed (one-shot)<br/>Python 3.13 + Faker"]
        PR["prometheus :9090"]
        GF["grafana :3001"]
    end
    BANK[["Bank (mock)"]]

    U -->|"HTTPS / HTTP"| FE
    FE -->|"/api/v1 proxy"| BE
    BE -->|"JPA, owner/app roles"| DB
    BE --> RV
    BE --> EV
    SD -->|"owner role"| DB
    SD --> RV
    PR -->|"scrape /actuator/prometheus"| BE
    GF --> PR
    EV -.->|"CSV downloaded by finance head"| BANK
```

## 2. Backend component diagram

```mermaid
flowchart TB
    subgraph Web["Web layer"]
        TF["TraceIdFilter"] --> SF["Security filter<br/>(token, CurrentUser, rate limit)"]
        SF --> CT["Controllers<br/>Claim, LineItem, Approval, Delegation,<br/>ReviewQueue, PayoutBatch, Dashboard,<br/>AuditEvent, PolicyRule, Me, Auth, Health"]
        GEH["GlobalExceptionHandler"]
    end
    subgraph Svc["Service layer (transactions)"]
        CS["ClaimService / LineItemService / ReceiptService"]
        SS["SubmitService"]
        AS["ApprovalService / CommentService"]
        DS["DelegationService"]
        RQ["ReviewQueueService"]
        PS["PayoutService + PayoutCsvWriter"]
        DBS["DashboardService"]
        AU["AuditService"]
        PRS["PolicyRuleService + PolicyConfigLoader"]
        ACC["ClaimAccessPolicy / CurrentStepAuthorizer / CapabilityService"]
        IDS["IdempotencyService"]
        SAS["SampleAuditSelector"]
    end
    subgraph Pure["Pure engines (no Spring, no SQL)"]
        PE["policy: PolicyEngine + 9 rules + RoutingDecider"]
        CE["chain: ApprovalChainEngine + coverage + delegation rules"]
    end
    subgraph Data["Data layer"]
        RP["Spring Data repositories<br/>(audit and policy rules insert-only)"]
        FS["Receipt and export file stores"]
    end

    CT --> CS & SS & AS & DS & RQ & PS & DBS & AU & PRS
    SS --> PE
    SS --> CE
    SS --> SAS
    AS --> CE
    AS --> ACC
    CS --> ACC
    CS & SS & AS & DS & RQ & PS & PRS --> AU
    SS & AS & PS & RQ --> IDS
    CS & SS & AS & DS & RQ & PS & DBS & AU & PRS --> RP
    CS & PS --> FS
    CT -.->|"exceptions"| GEH
```

## 3. Frontend component diagram

```mermaid
flowchart LR
    subgraph Shell
        APP["App.jsx routes"] --> RA["RequireAuth"] --> RG["RoleGate"] --> AL["AppLayout"]
    end
    subgraph Pages
        S1["S-01 Login"]
        S2["S-02 My claims"]
        S3["S-03 Claim editor"]
        S4["S-04 Claim detail"]
        S5["S-05 Approvals"]
        S6["S-06 Approver review"]
        S7["S-07 Review queue"]
        S8["S-08 Delegations"]
        S9["S-09/10 Payout"]
        S11["S-11 Dashboard"]
        S12["S-12 Audit trail"]
        S13["S-13 Policy rules"]
    end
    subgraph Shared["Reusable components"]
        DT["DataTable + FilterBar + BulkActionBar"]
        FF["FormField + ErrorSummary + MoneyInput"]
        RV["ReceiptViewer + ReceiptUpload"]
        PRL["PolicyResultList + ApprovalSteps"]
        CD["ConfirmDialog"]
        ST["Loading / Empty / Error / Forbidden states"]
    end
    subgraph Data["Data access"]
        HK["hooks: useQuery, useMutation"] --> MOD["api modules"] --> CL["client.js"]
        CTX["AuthContext / ClientConfigContext"]
    end
    AL --> Pages
    Pages --> Shared
    Pages --> HK
    Pages --> CTX
    CL -->|"/api/v1"| BE[("backend")]
```

## 4. Claim state machine (baseline v1.3)

```mermaid
stateDiagram-v2
    [*] --> DRAFT : claimant creates
    DRAFT --> SUBMITTED : claimant submits, receipts present (CL-21)
    SENT_BACK --> SUBMITTED : claimant resubmits, chain restarts (CL-12)
    SUBMITTED --> AUTO_REJECTED : limit breach or duplicate (CL-07, CL-23)
    SUBMITTED --> PENDING_MANAGER : total above 25,000 or weekend line (CL-02)
    SUBMITTED --> PENDING_AUDITOR : manager skipped or finance head claimant (CL-09, CL-16)
    SUBMITTED --> AUTO_APPROVED : clean and total up to 25,000, any claimant (CL-25)
    AUTO_APPROVED --> SAMPLE_AUDIT_PENDING : selected in 10 percent draw (FR-011)
    AUTO_APPROVED --> APPROVED : not selected
    SAMPLE_AUDIT_PENDING --> APPROVED : auditor approves (CL-14)
    SAMPLE_AUDIT_PENDING --> REJECTED : auditor rejects with comment
    PENDING_MANAGER --> PENDING_AUDITOR : approved, total above 25,000
    PENDING_MANAGER --> PENDING_FINANCE_HEAD : approved, auditor step covered (CL-27)
    PENDING_AUDITOR --> PENDING_FINANCE_HEAD : approved, total above 1,00,000
    PENDING_MANAGER --> APPROVED : approved, last or covered steps (CL-26, CL-27)
    PENDING_AUDITOR --> APPROVED : approved, last step
    PENDING_FINANCE_HEAD --> APPROVED : approved
    PENDING_MANAGER --> SENT_BACK : send back with comment
    PENDING_AUDITOR --> SENT_BACK : send back with comment
    PENDING_FINANCE_HEAD --> SENT_BACK : send back with comment
    PENDING_MANAGER --> REJECTED : reject with comment (CL-04)
    PENDING_AUDITOR --> REJECTED : reject with comment
    PENDING_FINANCE_HEAD --> REJECTED : reject with comment
    APPROVED --> IN_PAYOUT_BATCH : finance head or admin adds to batch
    IN_PAYOUT_BATCH --> PAID : batch exported (CL-06)
    AUTO_REJECTED --> [*]
    REJECTED --> [*]
    PAID --> [*]
```

`SUBMITTED` and `AUTO_APPROVED` are transient (recorded in the audit trail only). Every transition is enforced on the server, checks the actor and writes an audit entry in the same transaction; anything else returns `409 STATE_CONFLICT`.

## 5. Sequence — submit a claim (J1)

```mermaid
sequenceDiagram
    actor E as Employee
    participant FE as Claim editor
    participant API as ClaimController
    participant SS as SubmitService
    participant PE as PolicyEngine
    participant RD as RoutingDecider
    participant CE as ApprovalChainEngine
    participant DB as PostgreSQL

    E->>FE: Submit
    FE->>API: POST /claims/{id}/submit + Idempotency-Key
    API->>SS: submit(claimId, user)
    SS->>DB: Lock claim row, check claimant and state
    SS->>SS: Every line above 500 has a receipt?
    alt Receipt missing
        SS-->>FE: 422 RECEIPT_REQUIRED (state unchanged)
    else Receipts present
        SS->>DB: Load config version, earlier lines for duplicates and meals
        SS->>PE: evaluate(lines, earlier lines, config)
        PE-->>SS: results per line
        SS->>RD: decide(results, total, claimant)
        alt Limit breach or duplicate
            RD-->>SS: AUTO_REJECTED
        else Above 25,000 or weekend line
            RD-->>SS: ROUTE
            SS->>CE: compute chain
            CE-->>SS: steps (skips applied)
        else Clean and up to 25,000
            RD-->>SS: AUTO_APPROVE
            SS->>SS: SecureRandom sample draw
        end
        SS->>DB: Save results, steps, status, audit entries (one transaction)
        SS-->>FE: 200 outcome with reasons
    end
```

## 6. Sequence — approve with same-person coverage (J2, CL-27)

```mermaid
sequenceDiagram
    actor M as Manager who is also a finance auditor
    participant FE as Approver review
    participant AS as ApprovalService
    participant AZ as CurrentStepAuthorizer
    participant CE as Chain engine
    participant DB as PostgreSQL

    M->>FE: Approve claim of 60,000 INR
    FE->>AS: POST /claims/{id}/approve + Idempotency-Key
    AS->>DB: Lock claim, load pending step
    AS->>AZ: check(actor, claim, step)
    AZ-->>AS: allowed (not claimant, assigned manager)
    AS->>DB: Manager step APPROVED
    AS->>CE: nextSteps(actor)
    CE-->>AS: Auditor step coverable by same person in own right
    AS->>DB: Auditor step APPROVED, covered_by = manager step, audit STEP_COVERED
    AS->>DB: No further step, claim APPROVED, audit CLAIM_APPROVED
    AS-->>FE: 200 APPROVED
```

## 7. Sequence — export a payout batch (J7)

```mermaid
sequenceDiagram
    actor F as Finance head
    participant FE as Payout batch page
    participant PS as PayoutService
    participant CW as PayoutCsvWriter
    participant EV as exports volume
    participant DB as PostgreSQL

    F->>FE: Export batch
    FE->>PS: POST /payout-batches/{id}/export + Idempotency-Key
    PS->>DB: Lock batch, check CREATED and isAdmin
    PS->>CW: rows for each claim (13 columns, neutralise formula characters)
    CW-->>PS: CSV bytes
    PS->>EV: Write file under generated name
    PS->>DB: Batch EXPORTED with SHA-256, claims PAID, audit entries (one transaction)
    PS-->>FE: 200 rows, total, file hash
    F->>FE: Download
    FE->>PS: GET /payout-batches/{id}/file
    PS-->>FE: Same stored bytes
```

## 8. Deployment view

```mermaid
flowchart LR
    subgraph Laptop["Windows laptop, Docker Desktop"]
        subgraph Net["Compose network (internal)"]
            FE["frontend<br/>127.0.0.1:3000"]
            BE["backend<br/>127.0.0.1:8080"]
            DB[("postgres<br/>127.0.0.1:5433")]
            SD["seed (runs once)"]
            PR["prometheus<br/>127.0.0.1:9090"]
            GF["grafana<br/>127.0.0.1:3001"]
        end
        V1[["pgdata"]]
        V2[["receipts"]]
        V3[["exports"]]
        V4[["prometheusdata / grafanadata"]]
    end
    DB --- V1
    BE --- V2
    BE --- V3
    SD --- V2
    PR --- V4
    GF --- V4
    CI["GitHub Actions CI"] -.->|"build, test, dependency check"| Laptop
```

Start-up order: `postgres` healthy → `backend` runs Flyway and becomes healthy → `seed` runs once → `frontend`, `prometheus`, `grafana`. Reset: `docker compose down -v` then `up` (the audit trigger blocks deletes by design).
