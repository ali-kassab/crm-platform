-- Milestones (BR-040..044): reusable templates and per-entity progress trackers.

CREATE TABLE milestone_templates (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    name          varchar(100)  NOT NULL,
    description   varchar(1000),
    category_id   uuid REFERENCES categories (id),
    steps         jsonb         NOT NULL,
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_milestone_templates_ws ON milestone_templates (workspace_id);

CREATE TABLE milestones (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    owner_type    varchar(10)   NOT NULL CHECK (owner_type IN ('CONTACT', 'LOCATION', 'PROJECT', 'JOB', 'TASK')),
    owner_id      uuid          NOT NULL,
    name          varchar(100)  NOT NULL,
    description   varchar(1000),
    category_id   uuid REFERENCES categories (id),
    current_step  integer       NOT NULL DEFAULT 0 CHECK (current_step >= 0),
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_milestones_owner ON milestones (owner_type, owner_id) WHERE deleted_at IS NULL;

CREATE TABLE milestone_steps (
    id                uuid PRIMARY KEY,
    milestone_id      uuid          NOT NULL REFERENCES milestones (id) ON DELETE CASCADE,
    step_order        integer       NOT NULL CHECK (step_order >= 0),
    label             varchar(100)  NOT NULL,
    icon              varchar(40)   NOT NULL DEFAULT 'check-check',
    background_color  varchar(7)    NOT NULL DEFAULT '#000000',
    text_color        varchar(7)    NOT NULL DEFAULT '#FFFFFF',
    view_mode         varchar(5)    NOT NULL DEFAULT 'ICON' CHECK (view_mode IN ('ICON', 'IMAGE')),
    image_file_id     uuid,
    start_date        date,
    end_date          date,
    completed         boolean       NOT NULL DEFAULT false,
    completed_at      timestamptz,
    completed_by      uuid,
    CONSTRAINT ux_milestone_steps_order UNIQUE (milestone_id, step_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_milestone_steps_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
