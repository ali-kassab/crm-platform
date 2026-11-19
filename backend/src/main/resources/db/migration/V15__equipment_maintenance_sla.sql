-- R6: equipment register, warranty links + reminders, preventive-maintenance plans, SLA response tracking.
-- Additive only.

-- ---------------------------------------------------------------- equipment (site-scoped)
CREATE TABLE equipment (
    id                uuid PRIMARY KEY,
    workspace_id      uuid          NOT NULL REFERENCES workspaces (id),
    contact_id        uuid          NOT NULL REFERENCES contacts (id),
    location_id       uuid          NOT NULL REFERENCES locations (id),
    installed_job_id  uuid REFERENCES jobs (id),
    category          varchar(20)   NOT NULL CHECK (category IN ('CCTV_CAMERA', 'RECORDER', 'ACCESS_CONTROL',
                          'ALARM', 'FIRE_SYSTEM', 'NETWORK', 'HVAC_UNIT', 'ELECTRICAL', 'PLUMBING', 'OTHER')),
    name              varchar(150)  NOT NULL,
    manufacturer      varchar(100),
    model             varchar(100),
    serial_number     varchar(100),
    installed_on      date,
    status            varchar(10)   NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'FAULTY', 'REPLACED', 'REMOVED')),
    notes             varchar(2000),
    deleted_at        timestamptz,
    deleted_by        uuid,
    created_at        timestamptz   NOT NULL,
    created_by        uuid,
    updated_at        timestamptz   NOT NULL,
    updated_by        uuid,
    version           bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_equipment_location ON equipment (location_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_equipment_contact ON equipment (contact_id) WHERE deleted_at IS NULL;
-- a serial number identifies one device per workspace
CREATE UNIQUE INDEX ux_equipment_serial ON equipment (workspace_id, lower(serial_number))
    WHERE deleted_at IS NULL AND serial_number IS NOT NULL;

-- which devices a work order dealt with (equipment service history)
CREATE TABLE job_equipment (
    job_id        uuid NOT NULL REFERENCES jobs (id),
    equipment_id  uuid NOT NULL REFERENCES equipment (id),
    PRIMARY KEY (job_id, equipment_id)
);
CREATE INDEX ix_job_equipment_equipment ON job_equipment (equipment_id);

-- ---------------------------------------------------------------- warranties: direct links + reminder
ALTER TABLE warranties
    ADD COLUMN contact_id       uuid REFERENCES contacts (id),
    ADD COLUMN location_id      uuid REFERENCES locations (id),
    ADD COLUMN equipment_id     uuid REFERENCES equipment (id),
    ADD COLUMN reminder_days    integer NOT NULL DEFAULT 30 CHECK (reminder_days BETWEEN 0 AND 365),
    ADD COLUMN reminder_sent_at timestamptz;
CREATE INDEX ix_warranties_equipment ON warranties (equipment_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_warranties_contact ON warranties (contact_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------- preventive-maintenance plans (= agreements, D3)
CREATE TABLE maintenance_plans (
    id                        uuid PRIMARY KEY,
    workspace_id              uuid           NOT NULL REFERENCES workspaces (id),
    contact_id                uuid           NOT NULL REFERENCES contacts (id),
    location_id               uuid           NOT NULL REFERENCES locations (id),
    title                     varchar(150)   NOT NULL,
    description               varchar(2000),
    frequency                 varchar(12)    NOT NULL
                                  CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'SEMI_ANNUAL', 'ANNUAL', 'CUSTOM')),
    interval_days             integer CHECK (interval_days BETWEEN 1 AND 730),
    start_date                date           NOT NULL,
    end_date                  date,
    next_due_date             date,
    lead_days                 integer        NOT NULL DEFAULT 7 CHECK (lead_days BETWEEN 0 AND 90),
    response_hours            integer CHECK (response_hours BETWEEN 1 AND 720),
    included_visits           integer CHECK (included_visits >= 1),
    generated_count           integer        NOT NULL DEFAULT 0,
    visit_budget              numeric(19, 2) NOT NULL DEFAULT 0 CHECK (visit_budget >= 0),
    status                    varchar(8)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    renewal_reminder_sent_at  timestamptz,
    deleted_at                timestamptz,
    deleted_by                uuid,
    created_at                timestamptz    NOT NULL,
    created_by                uuid,
    updated_at                timestamptz    NOT NULL,
    updated_by                uuid,
    version                   bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_plan_custom_interval CHECK (frequency <> 'CUSTOM' OR interval_days IS NOT NULL),
    CONSTRAINT ck_plan_period CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_plans_due ON maintenance_plans (next_due_date) WHERE deleted_at IS NULL AND status = 'ACTIVE';
CREATE INDEX ix_plans_contact ON maintenance_plans (contact_id) WHERE deleted_at IS NULL;

CREATE TABLE maintenance_plan_assignees (
    plan_id  uuid NOT NULL REFERENCES maintenance_plans (id),
    user_id  uuid NOT NULL REFERENCES users (id),
    PRIMARY KEY (plan_id, user_id)
);

CREATE TABLE maintenance_plan_equipment (
    plan_id       uuid NOT NULL REFERENCES maintenance_plans (id),
    equipment_id  uuid NOT NULL REFERENCES equipment (id),
    PRIMARY KEY (plan_id, equipment_id)
);

-- one row per generated due date: the UNIQUE key makes generation idempotent
CREATE TABLE maintenance_occurrences (
    id                   uuid PRIMARY KEY,
    workspace_id         uuid         NOT NULL REFERENCES workspaces (id),
    plan_id              uuid         NOT NULL REFERENCES maintenance_plans (id),
    due_date             date         NOT NULL,
    job_id               uuid REFERENCES jobs (id),
    overdue_notified_at  timestamptz,
    deleted_at           timestamptz,
    deleted_by           uuid,
    created_at           timestamptz  NOT NULL,
    created_by           uuid,
    updated_at           timestamptz  NOT NULL,
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ux_occurrence_plan_due UNIQUE (plan_id, due_date)
);
CREATE INDEX ix_occurrences_due ON maintenance_occurrences (due_date) WHERE overdue_notified_at IS NULL;

-- ---------------------------------------------------------------- jobs: SLA + plan origin
ALTER TABLE jobs
    ADD COLUMN response_due_at      timestamptz,
    ADD COLUMN maintenance_plan_id  uuid REFERENCES maintenance_plans (id);
CREATE INDEX ix_jobs_response_due ON jobs (response_due_at)
    WHERE deleted_at IS NULL AND response_due_at IS NOT NULL;
