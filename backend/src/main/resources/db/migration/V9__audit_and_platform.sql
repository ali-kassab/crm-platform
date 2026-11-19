-- Append-only audit log (BR-111) and one-time platform markers (admin bootstrap).

CREATE TABLE audit_entries (
    id             uuid PRIMARY KEY,
    -- NULL for platform-level entries (platform admin actions); otherwise the owning workspace
    workspace_id   uuid REFERENCES workspaces (id),
    actor_user_id  uuid,
    actor_label    varchar(120),
    action         varchar(40)  NOT NULL,
    entity_type    varchar(40)  NOT NULL,
    entity_id      uuid,
    project_id     uuid,
    job_id         uuid,
    changes        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    trace_id       varchar(64),
    occurred_at    timestamptz  NOT NULL
);
CREATE INDEX ix_audit_ws_occurred ON audit_entries (workspace_id, occurred_at DESC);
CREATE INDEX ix_audit_entity ON audit_entries (entity_type, entity_id);
CREATE INDEX ix_audit_platform ON audit_entries (occurred_at DESC) WHERE workspace_id IS NULL;

CREATE FUNCTION audit_entries_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit entries are append-only' USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_audit_entries_append_only BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_entries_append_only();

-- A marker row records that a one-time platform action happened (e.g. the first platform admin
-- was bootstrapped), so it can never run again even if the account is later removed.
CREATE TABLE platform_markers (
    marker       varchar(60) PRIMARY KEY,
    recorded_at  timestamptz NOT NULL,
    details      jsonb       NOT NULL DEFAULT '{}'::jsonb
);
