-- R4: execution evidence — photo purposes + thumbnails, visit reports, materials, approvals.

-- ---------------------------------------------------------------- files
ALTER TABLE stored_files
    ADD COLUMN purpose       varchar(12),
    ADD COLUMN thumbnail_key varchar(300),
    ADD CONSTRAINT ck_files_purpose CHECK (purpose IS NULL OR purpose IN
        ('BEFORE', 'DURING', 'AFTER', 'INSPECTION', 'REPORT', 'WARRANTY', 'EQUIPMENT', 'OTHER'));

-- ---------------------------------------------------------------- visit reports (one per visit)
CREATE TABLE visit_reports (
    id                       uuid PRIMARY KEY,
    workspace_id             uuid          NOT NULL REFERENCES workspaces (id),
    visit_id                 uuid          NOT NULL REFERENCES job_visits (id),
    reported_problem         varchar(2000),
    findings                 varchar(4000),
    diagnosis                varchar(4000),
    work_performed           varchar(4000),
    recommendations          varchar(4000),
    notes                    varchar(4000),
    customer_name            varchar(150),
    customer_confirmed_at    timestamptz,
    customer_signature_id    uuid,
    deleted_at               timestamptz,
    deleted_by               uuid,
    created_at               timestamptz   NOT NULL,
    created_by               uuid,
    updated_at               timestamptz   NOT NULL,
    updated_by               uuid,
    version                  bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_visit_reports_visit ON visit_reports (visit_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------- materials / spare parts (D12: cost lines)
CREATE TABLE job_materials (
    id            uuid PRIMARY KEY,
    workspace_id  uuid           NOT NULL REFERENCES workspaces (id),
    job_id        uuid           NOT NULL REFERENCES jobs (id),
    visit_id      uuid REFERENCES job_visits (id),
    name          varchar(150)   NOT NULL,
    quantity      numeric(12, 3) NOT NULL CHECK (quantity > 0),
    unit          varchar(20),
    unit_cost     numeric(19, 2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
    note          varchar(500),
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz    NOT NULL,
    created_by    uuid,
    updated_at    timestamptz    NOT NULL,
    updated_by    uuid,
    version       bigint         NOT NULL DEFAULT 0
);
CREATE INDEX ix_job_materials_job ON job_materials (job_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------- customer approvals (D14: staff-recorded)
CREATE TABLE job_approvals (
    id                  uuid PRIMARY KEY,
    workspace_id        uuid          NOT NULL REFERENCES workspaces (id),
    job_id              uuid          NOT NULL REFERENCES jobs (id),
    kind                varchar(12)   NOT NULL CHECK (kind IN ('QUOTATION', 'WORK', 'COMPLETION')),
    status              varchar(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    approver_name       varchar(150),
    decided_at          timestamptz,
    notes               varchar(2000),
    signature_file_id   uuid,
    deleted_at          timestamptz,
    deleted_by          uuid,
    created_at          timestamptz   NOT NULL,
    created_by          uuid,
    updated_at          timestamptz   NOT NULL,
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_approvals_decision CHECK ((status = 'PENDING') = (decided_at IS NULL))
);
CREATE INDEX ix_job_approvals_job ON job_approvals (job_id) WHERE deleted_at IS NULL;
