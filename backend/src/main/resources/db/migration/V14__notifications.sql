-- R5: in-app notifications. One row per recipient; the client renders Arabic/English text from
-- `type` + `payload`, so stored rows never depend on the language at the time they were created.

CREATE TABLE notifications (
    id                 uuid PRIMARY KEY,
    workspace_id       uuid         NOT NULL REFERENCES workspaces (id),
    recipient_user_id  uuid         NOT NULL REFERENCES users (id),
    type               varchar(40)  NOT NULL,
    entity_type        varchar(20),
    entity_id          uuid,
    actor_user_id      uuid,
    payload            jsonb        NOT NULL DEFAULT '{}'::jsonb,
    read_at            timestamptz,
    deleted_at         timestamptz,
    deleted_by         uuid,
    created_at         timestamptz  NOT NULL,
    created_by         uuid,
    updated_at         timestamptz  NOT NULL,
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0
);

-- "my notifications, newest first" and the unread badge
CREATE INDEX ix_notifications_recipient ON notifications (workspace_id, recipient_user_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_notifications_unread ON notifications (workspace_id, recipient_user_id)
    WHERE deleted_at IS NULL AND read_at IS NULL;
