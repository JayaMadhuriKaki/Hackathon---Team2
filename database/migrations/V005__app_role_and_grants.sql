-- Least privilege for the running application (NFR-004, CLAUDE.md §13.1, database/SPEC.md §5).
-- The role name and password come from Flyway placeholders (env vars), never from Git.
-- Flyway itself runs with the owner credentials (DB_OWNER_USERNAME / DB_OWNER_PASSWORD).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        CREATE ROLE "${app_user}" LOGIN PASSWORD '${app_password}';
    END IF;
END;
$$;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO "${app_user}";

-- Append-only: SELECT and INSERT only (plus the identity sequence).
GRANT SELECT, INSERT ON audit_events TO "${app_user}";
GRANT USAGE ON SEQUENCE audit_events_id_seq TO "${app_user}";

-- Insert-only tables.
GRANT SELECT, INSERT ON policy_rules, policy_results, claim_comments TO "${app_user}";

-- Tables the app updates but never deletes from.
GRANT SELECT, INSERT, UPDATE ON claims, approval_steps, delegations, payout_batches, display_counters TO "${app_user}";

-- Draft lines and receipts can be deleted (API-009, API-010 replace); idempotency rows expire after 24 h.
GRANT SELECT, INSERT, UPDATE, DELETE ON claim_line_items, receipts, idempotency_keys TO "${app_user}";

-- Reference data: read only (no user management, Q-20).
GRANT SELECT ON departments, users, v_policy_config_current TO "${app_user}";
