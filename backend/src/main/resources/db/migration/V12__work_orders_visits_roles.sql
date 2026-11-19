-- R3: jobs become field-service work orders (D2), visits (D1), technician and finance roles (D8).

-- ---------------------------------------------------------------- jobs → work orders
ALTER TABLE jobs ALTER COLUMN project_id DROP NOT NULL;          -- standalone service jobs
ALTER TABLE jobs ALTER COLUMN start_date DROP NOT NULL;          -- planned window is optional;
ALTER TABLE jobs ALTER COLUMN estimated_end_date DROP NOT NULL;  -- visits carry the real schedule
ALTER TABLE jobs ALTER COLUMN budget SET DEFAULT 0;

ALTER TABLE jobs
    ADD COLUMN contact_id      uuid REFERENCES contacts (id),
    ADD COLUMN location_id     uuid REFERENCES locations (id),
    ADD COLUMN type            varchar(25) NOT NULL DEFAULT 'INSTALLATION',
    ADD COLUMN status          varchar(15) NOT NULL DEFAULT 'NEW',
    ADD COLUMN priority        varchar(10) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN reported_issue  varchar(2000),
    ADD COLUMN internal_notes  varchar(5000),
    ADD COLUMN actual_start_at timestamptz,
    ADD COLUMN completed_at    timestamptz;

-- every job knows its customer and site; existing project jobs inherit them from the project
UPDATE jobs j SET contact_id = p.contact_id, location_id = p.location_id FROM projects p WHERE p.id = j.project_id;
ALTER TABLE jobs ALTER COLUMN contact_id SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN location_id SET NOT NULL;

ALTER TABLE jobs
    ADD CONSTRAINT ck_jobs_type CHECK (type IN ('INSTALLATION', 'PREVENTIVE_MAINTENANCE', 'CORRECTIVE_REPAIR', 'INSPECTION')),
    ADD CONSTRAINT ck_jobs_status CHECK (status IN ('NEW', 'SCHEDULED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_jobs_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
ALTER TABLE jobs DROP CONSTRAINT ck_jobs_dates;
ALTER TABLE jobs ADD CONSTRAINT ck_jobs_dates
    CHECK (start_date IS NULL OR estimated_end_date IS NULL OR estimated_end_date >= start_date);

CREATE INDEX ix_jobs_contact ON jobs (contact_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_jobs_location ON jobs (location_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_jobs_status ON jobs (workspace_id, status) WHERE deleted_at IS NULL;

-- assigned technicians are workspace users; no project team is needed (independent technicians)
CREATE TABLE job_assignees (
    job_id   uuid NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    user_id  uuid NOT NULL REFERENCES users (id),
    PRIMARY KEY (job_id, user_id)
);
CREATE INDEX ix_job_assignees_user ON job_assignees (user_id);

-- ---------------------------------------------------------------- visits
CREATE TABLE job_visits (
    id                   uuid PRIMARY KEY,
    workspace_id         uuid         NOT NULL REFERENCES workspaces (id),
    job_id               uuid         NOT NULL REFERENCES jobs (id),
    scheduled_start      timestamptz  NOT NULL,
    scheduled_end        timestamptz  NOT NULL,
    status               varchar(12)  NOT NULL DEFAULT 'SCHEDULED',
    actual_start_at      timestamptz,
    actual_end_at        timestamptz,
    outcome              varchar(20),
    notes                varchar(2000),
    rescheduled_from_id  uuid REFERENCES job_visits (id),
    deleted_at           timestamptz,
    deleted_by           uuid,
    created_at           timestamptz  NOT NULL,
    created_by           uuid,
    updated_at           timestamptz  NOT NULL,
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_visits_window CHECK (scheduled_end > scheduled_start),
    CONSTRAINT ck_visits_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'MISSED', 'CANCELLED', 'RESCHEDULED')),
    CONSTRAINT ck_visits_outcome CHECK (outcome IS NULL OR outcome IN ('RESOLVED', 'FOLLOW_UP_REQUIRED', 'NOT_RESOLVED', 'CUSTOMER_UNAVAILABLE'))
);
CREATE INDEX ix_visits_job ON job_visits (job_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_visits_schedule ON job_visits (workspace_id, scheduled_start) WHERE deleted_at IS NULL;
CREATE INDEX ix_visits_open ON job_visits (status, scheduled_end) WHERE deleted_at IS NULL;

CREATE TABLE visit_assignees (
    visit_id  uuid NOT NULL REFERENCES job_visits (id) ON DELETE CASCADE,
    user_id   uuid NOT NULL REFERENCES users (id),
    PRIMARY KEY (visit_id, user_id)
);
CREATE INDEX ix_visit_assignees_user ON visit_assignees (user_id);

-- ---------------------------------------------------------------- money on standalone jobs
ALTER TABLE transactions ALTER COLUMN project_id DROP NOT NULL;

-- ---------------------------------------------------------------- roles (D8)
ALTER TABLE memberships DROP CONSTRAINT memberships_role_check;
ALTER TABLE memberships ADD CONSTRAINT memberships_role_check
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'TECHNICIAN', 'FINANCE'));
ALTER TABLE invitations DROP CONSTRAINT invitations_role_check;
ALTER TABLE invitations ADD CONSTRAINT invitations_role_check
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'TECHNICIAN', 'FINANCE'));
