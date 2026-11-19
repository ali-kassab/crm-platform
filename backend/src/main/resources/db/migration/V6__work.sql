-- Work: projects, team members, boards, columns, tasks, comments, board templates, flowcharts.

CREATE TABLE projects (
    id                         uuid PRIMARY KEY,
    workspace_id               uuid           NOT NULL REFERENCES workspaces (id),
    name                       varchar(150)   NOT NULL,
    description                varchar(5000)  NOT NULL,
    type                       varchar(15)    NOT NULL CHECK (type IN ('COMMERCIAL', 'RESIDENTIAL', 'INDUSTRIAL', 'INSTITUTIONAL', 'INFRASTRUCTURE')),
    address_line1              varchar(200),
    address_line2              varchar(200),
    city                       varchar(100)   NOT NULL,
    region                     varchar(100)   NOT NULL,
    postal_code                varchar(20)    NOT NULL,
    country                    varchar(2)     NOT NULL,
    total_area                 numeric(12, 2) NOT NULL CHECK (total_area >= 0),
    budget                     numeric(19, 2) NOT NULL CHECK (budget >= 0),
    start_date                 date           NOT NULL,
    estimated_end_date         date           NOT NULL,
    location_id                uuid           NOT NULL REFERENCES locations (id),
    contact_id                 uuid           NOT NULL REFERENCES contacts (id),
    deleted_at                 timestamptz,
    deleted_by                 uuid,
    created_at                 timestamptz    NOT NULL,
    created_by                 uuid,
    updated_at                 timestamptz    NOT NULL,
    updated_by                 uuid,
    version                    bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_projects_dates CHECK (estimated_end_date >= start_date)
);
CREATE INDEX ix_projects_ws_created ON projects (workspace_id, created_at DESC);
CREATE INDEX ix_projects_location ON projects (location_id);
CREATE INDEX ix_projects_contact ON projects (contact_id);

CREATE TABLE project_tags (
    project_id  uuid NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    tag_id      uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (project_id, tag_id)
);
CREATE INDEX ix_project_tags_tag ON project_tags (tag_id);

CREATE TABLE team_members (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    project_id    uuid          NOT NULL REFERENCES projects (id),
    user_id       uuid          NOT NULL REFERENCES users (id),
    position      varchar(100)  NOT NULL,
    is_manager    boolean       NOT NULL DEFAULT false,
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_team_members_project_user ON team_members (project_id, user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_team_members_manager ON team_members (project_id) WHERE is_manager AND deleted_at IS NULL;
CREATE INDEX ix_team_members_user ON team_members (user_id);

CREATE TABLE team_member_responsibilities (
    team_member_id  uuid        NOT NULL REFERENCES team_members (id) ON DELETE CASCADE,
    responsibility  varchar(30) NOT NULL CHECK (responsibility IN ('PROJECT_PLANNING', 'TEAM_MANAGEMENT', 'RESOURCE_ALLOCATION', 'REPORTING')),
    PRIMARY KEY (team_member_id, responsibility)
);

CREATE TABLE team_member_notes (
    id              uuid PRIMARY KEY,
    workspace_id    uuid          NOT NULL REFERENCES workspaces (id),
    team_member_id  uuid          NOT NULL REFERENCES team_members (id),
    text            varchar(2000) NOT NULL,
    deleted_at      timestamptz,
    deleted_by      uuid,
    created_at      timestamptz   NOT NULL,
    created_by      uuid,
    updated_at      timestamptz   NOT NULL,
    updated_by      uuid,
    version         bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_team_member_notes_member ON team_member_notes (team_member_id);

CREATE TABLE boards (
    id               uuid PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES workspaces (id),
    owner_type       varchar(10)   NOT NULL CHECK (owner_type IN ('PROJECT', 'JOB')),
    owner_id         uuid          NOT NULL,
    title            varchar(200)  NOT NULL,
    workflow         varchar(10)   NOT NULL DEFAULT 'SIMPLE' CHECK (workflow IN ('SIMPLE', 'COMPLEX')),
    max_wip          integer       CHECK (max_wip IS NULL OR max_wip > 0),
    last_changed_at  timestamptz   NOT NULL,
    deleted_at       timestamptz,
    deleted_by       uuid,
    created_at       timestamptz   NOT NULL,
    created_by       uuid,
    updated_at       timestamptz   NOT NULL,
    updated_by       uuid,
    version          bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_boards_owner ON boards (owner_type, owner_id) WHERE deleted_at IS NULL;

CREATE TABLE board_columns (
    id          uuid PRIMARY KEY,
    board_id    uuid         NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    title       varchar(30)  NOT NULL,
    position    integer      NOT NULL CHECK (position >= 1),
    is_default  boolean      NOT NULL DEFAULT false,
    CONSTRAINT ux_board_columns_position UNIQUE (board_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX ux_board_columns_default ON board_columns (board_id) WHERE is_default;

CREATE TABLE tasks (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    project_id    uuid REFERENCES projects (id),
    job_id        uuid,
    board_id      uuid          NOT NULL REFERENCES boards (id),
    column_id     uuid          NOT NULL REFERENCES board_columns (id),
    position      integer       NOT NULL CHECK (position >= 1),
    title         varchar(200)  NOT NULL,
    description   varchar(5000) NOT NULL,
    start_date    date          NOT NULL,
    end_date      date          NOT NULL,
    priority      varchar(6)    NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status        varchar(11)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    completed_at  timestamptz,
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_tasks_parent CHECK ((project_id IS NULL) <> (job_id IS NULL)),
    CONSTRAINT ck_tasks_dates CHECK (end_date >= start_date)
);
CREATE INDEX ix_tasks_column_position ON tasks (column_id, position) WHERE deleted_at IS NULL;
CREATE INDEX ix_tasks_project ON tasks (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX ix_tasks_job ON tasks (job_id) WHERE job_id IS NOT NULL;
CREATE INDEX ix_tasks_status_end ON tasks (workspace_id, status, end_date);

CREATE TABLE task_tags (
    task_id  uuid NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    tag_id   uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (task_id, tag_id)
);

CREATE TABLE task_assignees (
    task_id         uuid NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    team_member_id  uuid NOT NULL REFERENCES team_members (id),
    PRIMARY KEY (task_id, team_member_id)
);
CREATE INDEX ix_task_assignees_member ON task_assignees (team_member_id);

CREATE TABLE task_comments (
    id             uuid PRIMARY KEY,
    workspace_id   uuid          NOT NULL REFERENCES workspaces (id),
    task_id        uuid          NOT NULL REFERENCES tasks (id),
    text           varchar(2000) NOT NULL,
    image_file_id  uuid,
    deleted_at     timestamptz,
    deleted_by     uuid,
    created_at     timestamptz   NOT NULL,
    created_by     uuid,
    updated_at     timestamptz   NOT NULL,
    updated_by     uuid,
    version        bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_task_comments_task ON task_comments (task_id, created_at);

CREATE TABLE board_templates (
    id            uuid PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspaces (id),
    title         varchar(100)  NOT NULL,
    description   varchar(500),
    is_default    boolean       NOT NULL DEFAULT false,
    deleted_at    timestamptz,
    deleted_by    uuid,
    created_at    timestamptz   NOT NULL,
    created_by    uuid,
    updated_at    timestamptz   NOT NULL,
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_board_templates_default ON board_templates (workspace_id) WHERE is_default AND deleted_at IS NULL;

CREATE TABLE board_template_columns (
    template_id  uuid         NOT NULL REFERENCES board_templates (id) ON DELETE CASCADE,
    title        varchar(30)  NOT NULL,
    position     integer      NOT NULL CHECK (position >= 1),
    is_default   boolean      NOT NULL DEFAULT false,
    PRIMARY KEY (template_id, position)
);

CREATE TABLE flowcharts (
    id                 uuid PRIMARY KEY,
    workspace_id       uuid          NOT NULL REFERENCES workspaces (id),
    title              varchar(100)  NOT NULL,
    description        varchar(500),
    thumbnail_file_id  uuid,
    favorite           boolean       NOT NULL DEFAULT false,
    project_id         uuid REFERENCES projects (id),
    owner_user_id      uuid          NOT NULL REFERENCES users (id),
    diagram            jsonb         NOT NULL DEFAULT '{"nodes":[],"edges":[]}'::jsonb,
    diagram_size       integer       NOT NULL DEFAULT 0 CHECK (diagram_size <= 1048576),
    deleted_at         timestamptz,
    deleted_by         uuid,
    created_at         timestamptz   NOT NULL,
    created_by         uuid,
    updated_at         timestamptz   NOT NULL,
    updated_by         uuid,
    version            bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_flowcharts_project ON flowcharts (project_id);
CREATE INDEX ix_flowcharts_owner ON flowcharts (owner_user_id);

CREATE TABLE flowchart_tags (
    flowchart_id  uuid NOT NULL REFERENCES flowcharts (id) ON DELETE CASCADE,
    tag_id        uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (flowchart_id, tag_id)
);
