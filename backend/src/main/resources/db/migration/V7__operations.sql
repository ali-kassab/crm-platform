-- Operations: jobs (with their own board) and warranties.
-- Job and warranty files are tracked by the files module (owner_type JOB / WARRANTY), not here.

CREATE TABLE jobs (
    id                      uuid PRIMARY KEY,
    workspace_id            uuid           NOT NULL REFERENCES workspaces (id),
    project_id              uuid           NOT NULL REFERENCES projects (id),
    title                   varchar(150)   NOT NULL,
    description             varchar(5000)  NOT NULL,
    start_date              date           NOT NULL,
    estimated_end_date      date           NOT NULL,
    budget                  numeric(19, 2) NOT NULL CHECK (budget >= 0),
    team_manager_member_id  uuid REFERENCES team_members (id),
    deleted_at              timestamptz,
    deleted_by              uuid,
    created_at              timestamptz    NOT NULL,
    created_by              uuid,
    updated_at              timestamptz    NOT NULL,
    updated_by              uuid,
    version                 bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_jobs_dates CHECK (estimated_end_date >= start_date)
);
CREATE INDEX ix_jobs_project ON jobs (project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_jobs_ws_created ON jobs (workspace_id, created_at DESC);

CREATE TABLE job_team_members (
    job_id          uuid NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    team_member_id  uuid NOT NULL REFERENCES team_members (id),
    PRIMARY KEY (job_id, team_member_id)
);

CREATE TABLE job_tags (
    job_id  uuid NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    tag_id  uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (job_id, tag_id)
);
CREATE INDEX ix_job_tags_tag ON job_tags (tag_id);

-- tasks.job_id and notes.job_id were created without a FK because jobs did not exist yet
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_job FOREIGN KEY (job_id) REFERENCES jobs (id);
ALTER TABLE notes ADD CONSTRAINT fk_notes_job FOREIGN KEY (job_id) REFERENCES jobs (id);

CREATE TABLE warranties (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    project_id    uuid REFERENCES projects (id),
    job_id        uuid REFERENCES jobs (id),
    title         varchar(100)  NOT NULL,
    provider      varchar(150)  NOT NULL,
    status        varchar(10)   NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    note          varchar(2000),
    start_date    date          NOT NULL,
    end_date      date          NOT NULL,
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_warranties_dates CHECK (end_date >= start_date)
);
CREATE INDEX ix_warranties_status_end ON warranties (status, end_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_warranties_project ON warranties (project_id);
CREATE INDEX ix_warranties_job ON warranties (job_id);

CREATE TABLE warranty_tags (
    warranty_id  uuid NOT NULL REFERENCES warranties (id) ON DELETE CASCADE,
    tag_id       uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (warranty_id, tag_id)
);
CREATE INDEX ix_warranty_tags_tag ON warranty_tags (tag_id);
