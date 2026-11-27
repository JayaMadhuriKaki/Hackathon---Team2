-- ExpenseFlow schema, part 3: approval steps, comments, delegations, idempotency keys.
-- database/SPEC.md §2.9–§2.12.

CREATE TABLE approval_steps (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    claim_id              UUID NOT NULL REFERENCES claims (id),
    submission_no         INTEGER NOT NULL,
    step_order            SMALLINT NOT NULL,
    step_type             VARCHAR(20) NOT NULL,
    assigned_approver_id  UUID REFERENCES users (id),
    status                VARCHAR(12) NOT NULL DEFAULT 'WAITING',
    activated_at          TIMESTAMPTZ,
    skip_reason           VARCHAR(30),
    acted_by_id           UUID REFERENCES users (id),
    on_behalf_of_id       UUID REFERENCES users (id),
    acted_at              TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_steps_claim_sub_order UNIQUE (claim_id, submission_no, step_order),
    CONSTRAINT ck_steps_submission CHECK (submission_no >= 1),
    CONSTRAINT ck_steps_type CHECK (step_type IN ('MANAGER', 'FINANCE_AUDITOR', 'FINANCE_HEAD')),
    CONSTRAINT ck_steps_level CHECK ((step_type = 'MANAGER' AND step_order = 1)
        OR (step_type = 'FINANCE_AUDITOR' AND step_order = 2) OR (step_type = 'FINANCE_HEAD' AND step_order = 3)),
    CONSTRAINT ck_steps_status CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'SENT_BACK', 'SKIPPED',
        'SUPERSEDED')),
    CONSTRAINT ck_steps_skip_reason CHECK ((status = 'SKIPPED') = (skip_reason IS NOT NULL)),
    CONSTRAINT ck_steps_skip_values CHECK (skip_reason IS NULL OR skip_reason IN ('NO_APPROVER', 'CLAIMANT_IS_APPROVER')),
    CONSTRAINT ck_steps_acted CHECK ((status IN ('APPROVED', 'REJECTED', 'SENT_BACK'))
        = (acted_by_id IS NOT NULL AND acted_at IS NOT NULL) OR status = 'SUPERSEDED'),
    CONSTRAINT ck_steps_on_behalf CHECK (on_behalf_of_id IS NULL OR acted_by_id IS NOT NULL),
    CONSTRAINT ck_steps_activated CHECK (status IN ('WAITING', 'SKIPPED', 'SUPERSEDED') OR activated_at IS NOT NULL)
);
CREATE UNIQUE INDEX ux_steps_one_pending ON approval_steps (claim_id) WHERE status = 'PENDING';
CREATE INDEX ix_steps_assignee_pending ON approval_steps (assigned_approver_id) WHERE status = 'PENDING';
CREATE INDEX ix_steps_type_pending ON approval_steps (step_type, activated_at) WHERE status = 'PENDING';
CREATE INDEX ix_steps_acted_by ON approval_steps (acted_by_id);
CREATE INDEX ix_steps_claim ON approval_steps (claim_id, submission_no);

-- Defence in depth behind 422 SELF_APPROVAL_FORBIDDEN (B-02).
CREATE FUNCTION fn_steps_no_self_approval() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.acted_by_id IS NOT NULL AND NEW.acted_by_id = (SELECT claimant_id FROM claims WHERE id = NEW.claim_id) THEN
        RAISE EXCEPTION 'a claimant cannot act on their own claim';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_steps_no_self_approval BEFORE INSERT OR UPDATE ON approval_steps
    FOR EACH ROW EXECUTE FUNCTION fn_steps_no_self_approval();

CREATE TABLE claim_comments (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    claim_id    UUID NOT NULL REFERENCES claims (id),
    author_id   UUID NOT NULL REFERENCES users (id),
    body        VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_comments_body CHECK (btrim(body) <> '')
);
CREATE INDEX ix_comments_claim ON claim_comments (claim_id, created_at);

CREATE TABLE delegations (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    delegator_id  UUID NOT NULL REFERENCES users (id),
    delegate_id   UUID NOT NULL REFERENCES users (id),
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    cancelled_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_delegations_not_self CHECK (delegate_id <> delegator_id),
    CONSTRAINT ck_delegations_dates CHECK (end_date >= start_date),
    -- One active delegation per delegator, race-safe (ASM-007). SQLSTATE 23P01 -> 422 DELEGATION_OVERLAP.
    CONSTRAINT ex_delegations_no_overlap EXCLUDE USING gist (
        delegator_id WITH =, daterange(start_date, end_date, '[]') WITH &&) WHERE (cancelled_at IS NULL)
);
CREATE INDEX ix_delegations_delegate ON delegations (delegate_id, start_date, end_date) WHERE cancelled_at IS NULL;

-- CL-13 / B-03 defence in depth: the delegate must be a finance auditor or finance head.
CREATE FUNCTION fn_delegations_delegate_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT role FROM users WHERE id = NEW.delegate_id) NOT IN ('FINANCE_AUDITOR', 'FINANCE_HEAD') THEN
        RAISE EXCEPTION 'delegate must be a finance auditor or finance head';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_delegations_delegate_role BEFORE INSERT ON delegations
    FOR EACH ROW EXECUTE FUNCTION fn_delegations_delegate_role();

CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    idempotency_key  VARCHAR(100) NOT NULL,
    user_id          UUID NOT NULL REFERENCES users (id),
    operation        VARCHAR(7) NOT NULL,
    request_hash     VARCHAR(64) NOT NULL,
    response_status  SMALLINT,
    response_body    JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_idempotency_user_op_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_operation CHECK (operation IN ('API-012', 'API-015', 'API-016', 'API-017', 'API-023',
        'API-026', 'API-028')),
    CONSTRAINT ck_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_idempotency_created ON idempotency_keys (created_at);
