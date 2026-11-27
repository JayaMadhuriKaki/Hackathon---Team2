-- ExpenseFlow schema, part 4: the append-only audit trail (FR-018, NFR-004, B-11).
-- database/SPEC.md §2.13, §5.

CREATE TABLE audit_events (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor_id         UUID REFERENCES users (id),
    on_behalf_of_id  UUID REFERENCES users (id),
    action           VARCHAR(40) NOT NULL,
    entity_type      VARCHAR(20) NOT NULL,
    entity_id        UUID,
    claim_id         UUID REFERENCES claims (id),
    from_state       VARCHAR(25),
    to_state         VARCHAR(25),
    comment          VARCHAR(2000),
    details          JSONB,
    trace_id         VARCHAR(64),
    CONSTRAINT ck_audit_action CHECK (action IN ('CLAIM_CREATED', 'LINE_ADDED', 'LINE_UPDATED', 'LINE_DELETED',
        'RECEIPT_UPLOADED', 'CLAIM_SUBMITTED', 'POLICY_EVALUATED', 'CLAIM_AUTO_REJECTED', 'CLAIM_ROUTED',
        'CLAIM_AUTO_APPROVED', 'SAMPLE_AUDIT_SELECTED', 'SAMPLE_AUDIT_NOT_SELECTED', 'STEP_ACTIVATED', 'STEP_SKIPPED',
        'STEP_APPROVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED', 'CLAIM_SENT_BACK', 'COMMENT_ADDED', 'DELEGATION_CREATED',
        'DELEGATION_CANCELLED', 'PAYOUT_BATCH_CREATED', 'CLAIM_ADDED_TO_BATCH', 'PAYOUT_BATCH_EXPORTED', 'CLAIM_PAID',
        'POLICY_RULE_CHANGED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED')),
    CONSTRAINT ck_audit_entity_type CHECK (entity_type IN ('CLAIM', 'LINE_ITEM', 'DELEGATION', 'PAYOUT_BATCH',
        'POLICY_RULE', 'USER')),
    CONSTRAINT ck_audit_from_state CHECK (from_state IS NULL OR from_state IN ('DRAFT', 'SUBMITTED', 'AUTO_APPROVED',
        'AUTO_REJECTED', 'PENDING_MANAGER', 'PENDING_AUDITOR', 'PENDING_FINANCE_HEAD', 'SENT_BACK', 'REJECTED',
        'SAMPLE_AUDIT_PENDING', 'APPROVED', 'IN_PAYOUT_BATCH', 'PAID')),
    CONSTRAINT ck_audit_to_state CHECK (to_state IS NULL OR to_state IN ('DRAFT', 'SUBMITTED', 'AUTO_APPROVED',
        'AUTO_REJECTED', 'PENDING_MANAGER', 'PENDING_AUDITOR', 'PENDING_FINANCE_HEAD', 'SENT_BACK', 'REJECTED',
        'SAMPLE_AUDIT_PENDING', 'APPROVED', 'IN_PAYOUT_BATCH', 'PAID'))
);
CREATE INDEX ix_audit_claim ON audit_events (claim_id, id);
CREATE INDEX ix_audit_entity ON audit_events (entity_type, entity_id, id);
CREATE INDEX ix_audit_occurred ON audit_events (occurred_at);
CREATE INDEX ix_audit_actor ON audit_events (actor_id, occurred_at);
CREATE INDEX ix_audit_action ON audit_events (action, occurred_at);

-- Append-only for every role, including the owner (NFR-004).
CREATE FUNCTION fn_audit_events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;
CREATE TRIGGER trg_audit_events_no_update_delete BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION fn_audit_events_append_only();
CREATE TRIGGER trg_audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION fn_audit_events_append_only();
