-- ExpenseFlow schema, part 1: extensions, departments, users, policy configuration.
-- database/SPEC.md §1, §2.1–§2.3. Never edit after merge; add a new V0NN__ file instead.

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- delegation overlap exclusion (ASM-007)
CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- seed hashes demo passwords with bcrypt (ADR-000 P-04)

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_departments_name UNIQUE (name)
);

CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    employee_code        VARCHAR(10)  NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    role                 VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE',
    manager_id           UUID REFERENCES users (id),
    department_id        UUID NOT NULL REFERENCES departments (id),
    bank_account_number  VARCHAR(18)  NOT NULL,
    ifsc_code            VARCHAR(11)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_users_employee_code UNIQUE (employee_code),
    CONSTRAINT ux_users_bank_account UNIQUE (bank_account_number),
    CONSTRAINT ck_users_employee_code CHECK (employee_code ~ '^EMP[0-9]{4}$'),
    CONSTRAINT ck_users_password_hash CHECK (password_hash ~ '^\$2[aby]\$'),
    CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'FINANCE_AUDITOR', 'FINANCE_HEAD', 'ADMIN')),
    CONSTRAINT ck_users_not_own_manager CHECK (manager_id IS NULL OR manager_id <> id),
    CONSTRAINT ck_users_bank_account CHECK (bank_account_number ~ '^[0-9]{9,18}$'),
    CONSTRAINT ck_users_ifsc CHECK (ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$')
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_manager_id ON users (manager_id);
CREATE INDEX ix_users_department_id ON users (department_id);
CREATE INDEX ix_users_role ON users (role);

-- Insert-only policy configuration (CL-10, B-08). Each change inserts the next config_version.
CREATE TABLE policy_rules (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    rule_key        VARCHAR(64) NOT NULL,
    config_version  INTEGER     NOT NULL,
    value           JSONB       NOT NULL,
    created_by      UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_policy_rules_key_version UNIQUE (rule_key, config_version),
    CONSTRAINT ck_policy_rules_version CHECK (config_version > 0),
    CONSTRAINT ck_policy_rules_key CHECK (rule_key IN (
        'meals.max_per_day', 'hotel.max_per_night.tier1', 'hotel.tier1_cities', 'hotel.max_per_night.other',
        'local_cab.max_per_trip', 'receipt.mandatory_above', 'duplicate.match_fields',
        'duplicate.match_receipt_hash', 'weekend.flag_for_manager', 'duplicate.match_date_receipt_no',
        'approval.auditor_threshold', 'approval.finance_head_threshold', 'sample_audit.rate'))
);

CREATE VIEW v_policy_config_current AS
SELECT DISTINCT ON (rule_key) rule_key, config_version, value, created_by, created_at
FROM policy_rules
ORDER BY rule_key, config_version DESC;

-- Baseline: the documented values (CLAUDE.md §2.2, architecture/SPEC.md §4.1), version 1.
INSERT INTO policy_rules (rule_key, config_version, value) VALUES
    ('meals.max_per_day',               1, '"1500.00"'),
    ('hotel.max_per_night.tier1',       1, '"6000.00"'),
    ('hotel.tier1_cities',              1, '["Kolkata", "Mumbai", "Delhi", "Chennai", "Bangalore", "Bengaluru", "New Delhi"]'),
    ('hotel.max_per_night.other',       1, '"4000.00"'),
    ('local_cab.max_per_trip',          1, '"2000.00"'),
    ('receipt.mandatory_above',         1, '"500.00"'),
    ('duplicate.match_fields',          1, '["vendor", "amount", "expenseDate"]'),
    ('duplicate.match_receipt_hash',    1, 'true'),
    ('weekend.flag_for_manager',        1, 'true'),
    ('duplicate.match_date_receipt_no', 1, 'true'),
    ('approval.auditor_threshold',      1, '"25000.00"'),
    ('approval.finance_head_threshold', 1, '"100000.00"'),
    ('sample_audit.rate',               1, '"0.10"');
