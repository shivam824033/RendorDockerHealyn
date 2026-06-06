-- Healyn V12: audit.audit_log (append-only clinical access log).
-- Reference: docs/DATABASE_SCHEMA.md §3.14, docs/SECURITY_GUIDELINES.md §11.
-- Enum audit_action created in V2. Append-only: app role gets INSERT/SELECT, never UPDATE/DELETE.

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_account_id    UUID,
    actor_role          account_role,
    action              audit_action NOT NULL,
    resource_type       VARCHAR(64) NOT NULL,
    resource_id         UUID,
    request_id          UUID,
    ip_address          INET,
    metadata            JSONB
);

CREATE INDEX idx_audit_actor_time ON audit.audit_log(actor_account_id, occurred_at DESC);
CREATE INDEX idx_audit_resource ON audit.audit_log(resource_type, resource_id);

-- Least privilege: grant only where the runtime role exists (no-op in test/CI containers).
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'healyn_app') THEN
        GRANT USAGE ON SCHEMA audit TO healyn_app;
        GRANT INSERT, SELECT ON audit.audit_log TO healyn_app;
        GRANT USAGE, SELECT ON SEQUENCE audit.audit_log_id_seq TO healyn_app;
    END IF;
END $$;
