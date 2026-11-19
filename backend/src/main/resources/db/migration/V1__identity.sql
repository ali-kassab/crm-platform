-- Identity: users, workspaces (tenants), memberships, invitations and credentials.
-- Emails are stored lower-cased; uniqueness is enforced on lower(email).

CREATE TABLE workspaces (
    id          uuid PRIMARY KEY,
    name        varchar(80)  NOT NULL,
    slug        varchar(100) NOT NULL,
    locale      varchar(20)  NOT NULL DEFAULT 'en-US',
    time_zone   varchar(60)  NOT NULL DEFAULT 'UTC',
    deleted_at  timestamptz,
    created_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_at  timestamptz  NOT NULL,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_workspaces_slug ON workspaces (slug);

CREATE TABLE users (
    id                  uuid PRIMARY KEY,
    email               varchar(254) NOT NULL,
    password_hash       varchar(100) NOT NULL,
    first_name          varchar(60)  NOT NULL,
    last_name           varchar(60)  NOT NULL,
    job_title           varchar(100),
    phone               varchar(20),
    avatar_file_id      uuid,
    active              boolean      NOT NULL DEFAULT true,
    super_admin         boolean      NOT NULL DEFAULT false,
    last_login_at       timestamptz,
    last_workspace_id   uuid REFERENCES workspaces (id),
    created_at          timestamptz  NOT NULL,
    created_by          uuid,
    updated_at          timestamptz  NOT NULL,
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE memberships (
    id            uuid PRIMARY KEY,
    workspace_id  uuid        NOT NULL REFERENCES workspaces (id),
    user_id       uuid        NOT NULL REFERENCES users (id),
    role          varchar(10) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    active        boolean     NOT NULL DEFAULT true,
    joined_at     timestamptz NOT NULL,
    created_at    timestamptz NOT NULL,
    created_by    uuid,
    updated_at    timestamptz NOT NULL,
    updated_by    uuid,
    version       bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_memberships_workspace_user ON memberships (workspace_id, user_id);
CREATE INDEX ix_memberships_user ON memberships (user_id);

CREATE TABLE invitations (
    id            uuid PRIMARY KEY,
    workspace_id  uuid         NOT NULL REFERENCES workspaces (id),
    email         varchar(254) NOT NULL,
    role          varchar(10)  NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER', 'OWNER')),
    token_hash    varchar(64)  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    invited_by    uuid         NOT NULL REFERENCES users (id),
    accepted_at   timestamptz,
    revoked_at    timestamptz,
    created_at    timestamptz  NOT NULL,
    created_by    uuid,
    updated_at    timestamptz  NOT NULL,
    updated_by    uuid,
    version       bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_invitations_token ON invitations (token_hash);
CREATE UNIQUE INDEX ux_invitations_pending ON invitations (workspace_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id),
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL,
    created_by  uuid,
    updated_at  timestamptz NOT NULL,
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_password_reset_token ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id);

CREATE TABLE refresh_tokens (
    id                   uuid PRIMARY KEY,
    user_id              uuid         NOT NULL REFERENCES users (id),
    workspace_id         uuid         NOT NULL REFERENCES workspaces (id),
    family_id            uuid         NOT NULL,
    token_hash           varchar(64)  NOT NULL,
    expires_at           timestamptz  NOT NULL,
    absolute_expires_at  timestamptz  NOT NULL,
    rotated_at           timestamptz,
    replaced_by_id       uuid,
    revoked_at           timestamptz,
    user_agent           varchar(255),
    ip                   varchar(45),
    created_at           timestamptz  NOT NULL,
    created_by           uuid,
    updated_at           timestamptz  NOT NULL,
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
