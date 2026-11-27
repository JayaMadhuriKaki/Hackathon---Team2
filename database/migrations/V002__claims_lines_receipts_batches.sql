-- ExpenseFlow schema, part 2: payout batches, claims, line items, receipts, policy results.
-- database/SPEC.md §2.4–§2.8.

CREATE TABLE payout_batches (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    batch_number  VARCHAR(12) NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'CREATED',
    created_by    UUID NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    exported_by   UUID REFERENCES users (id),
    exported_at   TIMESTAMPTZ,
    row_count     INTEGER,
    total_amount  NUMERIC(12, 2),
    file_sha256   VARCHAR(64),
    file_path     VARCHAR(120),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_payout_batches_number UNIQUE (batch_number),
    CONSTRAINT ck_payout_batches_number CHECK (batch_number ~ '^PB-[0-9]{4}-[0-9]{4}$'),
    CONSTRAINT ck_payout_batches_status CHECK (status IN ('CREATED', 'EXPORTED')),
    CONSTRAINT ck_payout_batches_rows CHECK (row_count IS NULL OR row_count >= 1),
    CONSTRAINT ck_payout_batches_total CHECK (total_amount IS NULL OR total_amount > 0),
    CONSTRAINT ck_payout_batches_sha CHECK (file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payout_batches_path CHECK (file_path IS NULL OR file_path ~ '^[A-Za-z0-9_.-]+$'),
    CONSTRAINT ck_payout_batches_exported CHECK ((status = 'EXPORTED') = (exported_by IS NOT NULL
        AND exported_at IS NOT NULL AND row_count IS NOT NULL AND total_amount IS NOT NULL
        AND file_sha256 IS NOT NULL AND file_path IS NOT NULL))
);

-- An exported batch is never edited (CLAUDE.md §2.8).
CREATE FUNCTION fn_payout_batches_locked() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payout batch % is exported and cannot be changed', OLD.batch_number;
END;
$$;
CREATE TRIGGER trg_payout_batches_locked BEFORE UPDATE OR DELETE ON payout_batches
    FOR EACH ROW WHEN (OLD.status = 'EXPORTED') EXECUTE FUNCTION fn_payout_batches_locked();

CREATE TABLE claims (
    id                        UUID PRIMARY KEY DEFAULT uuidv7(),
    claim_number              VARCHAR(14) NOT NULL,
    claimant_id               UUID NOT NULL REFERENCES users (id),
    status                    VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    total_amount              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    submission_no             INTEGER NOT NULL DEFAULT 0,
    config_version            INTEGER,
    submitted_at              TIMESTAMPTZ,
    decided_at                TIMESTAMPTZ,
    sample_audit_selected     BOOLEAN,
    sample_audit_selected_at  TIMESTAMPTZ,
    payout_batch_id           UUID REFERENCES payout_batches (id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_claims_number UNIQUE (claim_number),
    CONSTRAINT ck_claims_number CHECK (claim_number ~ '^CLM-[0-9]{4}-[0-9]{5}$'),
    CONSTRAINT ck_claims_status CHECK (status IN ('DRAFT', 'AUTO_REJECTED', 'PENDING_MANAGER', 'PENDING_AUDITOR',
        'PENDING_FINANCE_HEAD', 'SENT_BACK', 'REJECTED', 'SAMPLE_AUDIT_PENDING', 'APPROVED', 'IN_PAYOUT_BATCH', 'PAID')),
    CONSTRAINT ck_claims_total CHECK (total_amount >= 0),
    CONSTRAINT ck_claims_submission_no CHECK (submission_no >= 0),
    CONSTRAINT ck_claims_config_version CHECK (config_version IS NULL OR config_version > 0),
    CONSTRAINT ck_claims_batch_state CHECK ((status IN ('IN_PAYOUT_BATCH', 'PAID')) = (payout_batch_id IS NOT NULL)),
    CONSTRAINT ck_claims_sample_state CHECK (status <> 'SAMPLE_AUDIT_PENDING'
        OR (sample_audit_selected IS TRUE AND sample_audit_selected_at IS NOT NULL)),
    CONSTRAINT ck_claims_submitted CHECK (status = 'DRAFT'
        OR (submission_no >= 1 AND submitted_at IS NOT NULL AND config_version IS NOT NULL)),
    CONSTRAINT ck_claims_decided CHECK (status NOT IN ('APPROVED', 'IN_PAYOUT_BATCH', 'PAID', 'REJECTED', 'AUTO_REJECTED')
        OR decided_at IS NOT NULL)
);
CREATE INDEX ix_claims_claimant_created ON claims (claimant_id, created_at DESC);
CREATE INDEX ix_claims_pending ON claims (status, submitted_at)
    WHERE status IN ('PENDING_MANAGER', 'PENDING_AUDITOR', 'PENDING_FINANCE_HEAD');
CREATE INDEX ix_claims_sample_pending ON claims (sample_audit_selected_at) WHERE status = 'SAMPLE_AUDIT_PENDING';
CREATE INDEX ix_claims_approved ON claims (submitted_at) WHERE status = 'APPROVED';
CREATE INDEX ix_claims_payout_batch_id ON claims (payout_batch_id);
CREATE INDEX ix_claims_decided_at ON claims (decided_at);

-- Yearly display-number sequences are allocated by the service from this counter table.
CREATE TABLE display_counters (
    counter_key  VARCHAR(20) PRIMARY KEY,
    last_value   INTEGER NOT NULL
);

CREATE TABLE claim_line_items (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    claim_id        UUID NOT NULL REFERENCES claims (id),
    line_no         SMALLINT NOT NULL,
    category        VARCHAR(20) NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    expense_date    DATE NOT NULL,
    vendor          VARCHAR(120) NOT NULL,
    receipt_number  VARCHAR(60) NOT NULL,
    city            VARCHAR(100),
    check_in_date   DATE,
    check_out_date  DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_line_items_claim_line UNIQUE (claim_id, line_no),
    CONSTRAINT ck_line_no CHECK (line_no > 0),
    CONSTRAINT ck_line_category CHECK (category IN ('MEALS', 'HOTEL', 'LOCAL_CAB')),
    CONSTRAINT ck_line_amount CHECK (amount > 0),
    CONSTRAINT ck_line_vendor CHECK (char_length(btrim(vendor)) BETWEEN 1 AND 120),
    CONSTRAINT ck_line_receipt_number CHECK (char_length(btrim(receipt_number)) BETWEEN 1 AND 60),
    CONSTRAINT ck_line_hotel_fields CHECK ((category = 'HOTEL') = (city IS NOT NULL AND check_in_date IS NOT NULL
        AND check_out_date IS NOT NULL)),
    CONSTRAINT ck_line_hotel_dates CHECK (check_out_date IS NULL OR check_in_date IS NULL OR check_out_date > check_in_date),
    CONSTRAINT ck_line_hotel_expense_date CHECK (category <> 'HOTEL' OR expense_date = check_in_date)
);
-- CL-05 (date + receipt number) is deliberately NOT unique: a repeat is a DUPLICATE_FLAG (POL-009, ASM-004).
CREATE INDEX ix_line_items_date_receipt_no ON claim_line_items (expense_date, upper(btrim(receipt_number)));
CREATE INDEX ix_line_items_dup_fields ON claim_line_items (expense_date, amount,
    lower(regexp_replace(btrim(vendor), '\s+', ' ', 'g')));
CREATE INDEX ix_line_items_category_date ON claim_line_items (category, expense_date);
CREATE INDEX ix_line_items_claim ON claim_line_items (claim_id);

CREATE TABLE receipts (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    line_item_id  UUID NOT NULL REFERENCES claim_line_items (id) ON DELETE CASCADE,
    storage_name  VARCHAR(64) NOT NULL,
    content_type  VARCHAR(50) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    sha256        VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_receipts_line UNIQUE (line_item_id),
    CONSTRAINT ux_receipts_storage UNIQUE (storage_name),
    CONSTRAINT ck_receipts_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT ck_receipts_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_receipts_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_receipts_sha256 ON receipts (sha256);

CREATE TABLE policy_results (
    id                    UUID PRIMARY KEY DEFAULT uuidv7(),
    claim_id              UUID NOT NULL REFERENCES claims (id),
    submission_no         INTEGER NOT NULL,
    line_item_id          UUID REFERENCES claim_line_items (id) ON DELETE SET NULL,
    rule_id               VARCHAR(7) NOT NULL,
    result_type           VARCHAR(15) NOT NULL,
    config_key            VARCHAR(64) NOT NULL,
    configured_limit      NUMERIC(12, 2),
    actual_value          VARCHAR(200),
    reason                VARCHAR(2000) NOT NULL,
    details               JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_policy_results_submission CHECK (submission_no >= 1),
    CONSTRAINT ck_policy_results_rule CHECK (rule_id IN ('POL-001', 'POL-002', 'POL-003', 'POL-004', 'POL-006',
        'POL-007', 'POL-008', 'POL-009')),
    CONSTRAINT ck_policy_results_type CHECK (result_type IN ('LIMIT_BREACH', 'DUPLICATE_FLAG', 'WEEKEND_FLAG')),
    CONSTRAINT ck_policy_results_type_rule CHECK (
        (rule_id IN ('POL-001', 'POL-002', 'POL-003', 'POL-004') AND result_type = 'LIMIT_BREACH')
        OR (rule_id IN ('POL-006', 'POL-007', 'POL-009') AND result_type = 'DUPLICATE_FLAG')
        OR (rule_id = 'POL-008' AND result_type = 'WEEKEND_FLAG')),
    CONSTRAINT ck_policy_results_reason CHECK (btrim(reason) <> '')
);
CREATE INDEX ix_policy_results_claim_sub ON policy_results (claim_id, submission_no);
CREATE INDEX ix_policy_results_rule_created ON policy_results (rule_id, created_at);
