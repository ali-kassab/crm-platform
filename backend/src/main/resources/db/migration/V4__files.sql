-- Files: metadata for objects in the private bucket (ADR-0009). Each file attaches to one owner.

CREATE TABLE stored_files (
    id               uuid PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES workspaces (id),
    kind             varchar(10)   NOT NULL CHECK (kind IN ('IMAGE', 'DOCUMENT')),
    storage_key      varchar(200)  NOT NULL,
    original_name    varchar(255)  NOT NULL,
    content_type     varchar(150)  NOT NULL,
    size_bytes       bigint        NOT NULL CHECK (size_bytes >= 0),
    checksum_sha256  varchar(64)   NOT NULL,
    uploaded_by      uuid REFERENCES users (id),
    owner_type       varchar(30),
    owner_id         uuid,
    attached_at      timestamptz,
    deleted_at       timestamptz,
    deleted_by       uuid,
    created_at       timestamptz   NOT NULL,
    created_by       uuid,
    updated_at       timestamptz   NOT NULL,
    updated_by       uuid,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_files_owner CHECK ((owner_type IS NULL) = (owner_id IS NULL))
);
CREATE UNIQUE INDEX ux_stored_files_key ON stored_files (storage_key);
CREATE INDEX ix_stored_files_orphans ON stored_files (created_at) WHERE owner_id IS NULL AND deleted_at IS NULL;
CREATE INDEX ix_stored_files_owner ON stored_files (owner_type, owner_id);
