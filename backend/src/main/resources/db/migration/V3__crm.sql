-- CRM: reference data (categories, statuses, tags), contacts, locations, notes.
-- Name uniqueness is per workspace, case-insensitive, among non-deleted rows (BR-003).

CREATE TABLE categories (
    id            uuid PRIMARY KEY,
    workspace_id  uuid         NOT NULL REFERENCES workspaces (id),
    name          varchar(50)  NOT NULL,
    description   varchar(500),
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz  NOT NULL,
    created_by    uuid,
    updated_at    timestamptz  NOT NULL,
    updated_by    uuid,
    version       bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_categories_name ON categories (workspace_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE statuses (
    id                  uuid PRIMARY KEY,
    workspace_id        uuid         NOT NULL REFERENCES workspaces (id),
    name                varchar(25)  NOT NULL,
    category_id         uuid         NOT NULL REFERENCES categories (id),
    description         varchar(500),
    background_color    varchar(7)   NOT NULL DEFAULT '#000000',
    text_color          varchar(7)   NOT NULL DEFAULT '#FFFFFF',
    icon                varchar(40)  NOT NULL DEFAULT 'check-check',
    start_procedure_id  uuid,
    stop_procedure_id   uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    created_at          timestamptz  NOT NULL,
    created_by          uuid,
    updated_at          timestamptz  NOT NULL,
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_statuses_procedures CHECK (start_procedure_id IS NULL OR start_procedure_id <> stop_procedure_id)
);
CREATE UNIQUE INDEX ux_statuses_name ON statuses (workspace_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_statuses_category ON statuses (category_id);

CREATE TABLE tags (
    id                  uuid PRIMARY KEY,
    workspace_id        uuid         NOT NULL REFERENCES workspaces (id),
    name                varchar(50)  NOT NULL,
    category_id         uuid         NOT NULL REFERENCES categories (id),
    description         varchar(500),
    background_color    varchar(7)   NOT NULL DEFAULT '#000000',
    text_color          varchar(7)   NOT NULL DEFAULT '#FFFFFF',
    icon                varchar(40)  NOT NULL DEFAULT 'check-check',
    start_procedure_id  uuid,
    stop_procedure_id   uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    created_at          timestamptz  NOT NULL,
    created_by          uuid,
    updated_at          timestamptz  NOT NULL,
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tags_procedures CHECK (start_procedure_id IS NULL OR start_procedure_id <> stop_procedure_id)
);
CREATE UNIQUE INDEX ux_tags_name ON tags (workspace_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_tags_category ON tags (category_id);

CREATE TABLE contacts (
    id                      uuid PRIMARY KEY,
    workspace_id            uuid          NOT NULL REFERENCES workspaces (id),
    type                    varchar(10)   NOT NULL CHECK (type IN ('CLIENT', 'PROSPECT', 'PARTNER', 'LEAD')),
    name_title              varchar(10),
    first_name              varchar(60)   NOT NULL,
    middle_name             varchar(60),
    last_name               varchar(60)   NOT NULL,
    company                 varchar(150),
    industry                varchar(100),
    position                varchar(100),
    email                   varchar(254)  NOT NULL,
    phone                   varchar(20),
    mobile                  varchar(20),
    work_phone              varchar(20),
    address_line1           varchar(200),
    address_line2           varchar(200),
    city                    varchar(100),
    region                  varchar(100),
    postal_code             varchar(20),
    country                 varchar(2),
    latitude                numeric(9, 6),
    longitude               numeric(9, 6),
    image_file_id           uuid,
    category_id             uuid REFERENCES categories (id),
    status_id               uuid REFERENCES statuses (id),
    representative_user_id  uuid REFERENCES users (id),
    contract_signed         boolean       NOT NULL DEFAULT false,
    active                  boolean       NOT NULL DEFAULT true,
    metadata                jsonb         NOT NULL DEFAULT '{}'::jsonb,
    deleted_at              timestamptz,
    deleted_by              uuid,
    created_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_at              timestamptz   NOT NULL,
    updated_by              uuid,
    version                 bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_contacts_email_lower CHECK (email = lower(email))
);
CREATE UNIQUE INDEX ux_contacts_email ON contacts (workspace_id, email) WHERE deleted_at IS NULL;
CREATE INDEX ix_contacts_workspace_created ON contacts (workspace_id, created_at DESC);
CREATE INDEX ix_contacts_status ON contacts (status_id);
CREATE INDEX ix_contacts_category ON contacts (category_id);
CREATE INDEX ix_contacts_representative ON contacts (representative_user_id);

CREATE TABLE contact_tags (
    contact_id  uuid NOT NULL REFERENCES contacts (id) ON DELETE CASCADE,
    tag_id      uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (contact_id, tag_id)
);
CREATE INDEX ix_contact_tags_tag ON contact_tags (tag_id);

CREATE TABLE locations (
    id                   uuid PRIMARY KEY,
    workspace_id         uuid          NOT NULL REFERENCES workspaces (id),
    contact_id           uuid          NOT NULL REFERENCES contacts (id),
    type                 varchar(12)   NOT NULL CHECK (type IN ('RESIDENTIAL', 'COMMERCIAL', 'INDUSTRIAL', 'RETAIL', 'WAREHOUSE', 'OFFICE')),
    name                 varchar(150)  NOT NULL,
    address_line1        varchar(200),
    address_line2        varchar(200),
    city                 varchar(100),
    region               varchar(100),
    postal_code          varchar(20),
    country              varchar(2),
    latitude             numeric(9, 6),
    longitude            numeric(9, 6),
    access_instructions  varchar(2000),
    parking_available    boolean       NOT NULL DEFAULT false,
    working_hours        jsonb         NOT NULL,
    deleted_at           timestamptz,
    deleted_by           uuid,
    created_at           timestamptz   NOT NULL,
    created_by           uuid,
    updated_at           timestamptz   NOT NULL,
    updated_by           uuid,
    version              bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_locations_contact ON locations (contact_id);
CREATE INDEX ix_locations_city ON locations (workspace_id, lower(city));

CREATE TABLE notes (
    id                uuid PRIMARY KEY,
    workspace_id      uuid          NOT NULL REFERENCES workspaces (id),
    contact_id        uuid REFERENCES contacts (id),
    project_id        uuid,
    job_id            uuid,
    title             varchar(100)  NOT NULL,
    body              varchar(5000) NOT NULL,
    document_file_id  uuid,
    deleted_at        timestamptz,
    deleted_by        uuid,
    created_at        timestamptz   NOT NULL,
    created_by        uuid,
    updated_at        timestamptz   NOT NULL,
    updated_by        uuid,
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_notes_single_parent CHECK (num_nonnulls(contact_id, project_id, job_id) = 1)
);
CREATE INDEX ix_notes_contact ON notes (contact_id) WHERE contact_id IS NOT NULL;
CREATE INDEX ix_notes_project ON notes (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX ix_notes_job ON notes (job_id) WHERE job_id IS NOT NULL;
