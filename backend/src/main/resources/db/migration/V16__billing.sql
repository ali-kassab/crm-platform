-- R7: SaaS subscriptions with manual payments (Vodafone Cash / InstaPay receipts approved by the
-- platform admin). Platform-level tables: not tenant-filtered; every tenant read filters the
-- workspace explicitly. Commercial values below are PROVISIONAL and editable by the platform admin.

CREATE TABLE billing_plans (
    id                   uuid PRIMARY KEY,
    code                 varchar(30)    NOT NULL UNIQUE,
    name_ar              varchar(100)   NOT NULL,
    name_en              varchar(100)   NOT NULL,
    description_ar       varchar(1000),
    description_en       varchar(1000),
    price_monthly        numeric(19, 2) NOT NULL CHECK (price_monthly >= 0),
    price_yearly         numeric(19, 2) NOT NULL CHECK (price_yearly >= 0),
    currency             varchar(3)     NOT NULL DEFAULT 'EGP',
    max_users            integer CHECK (max_users >= 1),
    max_active_projects  integer CHECK (max_active_projects >= 0),
    max_jobs_per_month   integer CHECK (max_jobs_per_month >= 0),
    max_storage_mb       integer CHECK (max_storage_mb >= 0),
    active               boolean        NOT NULL DEFAULT true,
    sort_order           integer        NOT NULL DEFAULT 0,
    created_at           timestamptz    NOT NULL,
    created_by           uuid,
    updated_at           timestamptz    NOT NULL,
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0
);

CREATE TABLE billing_payment_methods (
    id               uuid PRIMARY KEY,
    kind             varchar(15)   NOT NULL CHECK (kind IN ('VODAFONE_CASH', 'INSTAPAY', 'BANK_TRANSFER', 'OTHER')),
    label_ar         varchar(100)  NOT NULL,
    label_en         varchar(100)  NOT NULL,
    account          varchar(150)  NOT NULL,
    instructions_ar  varchar(2000),
    instructions_en  varchar(2000),
    active           boolean       NOT NULL DEFAULT true,
    sort_order       integer       NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL,
    created_by       uuid,
    updated_at       timestamptz   NOT NULL,
    updated_by       uuid,
    version          bigint        NOT NULL DEFAULT 0
);

-- one subscription per workspace; expiry restricts access but never deletes anything (D5)
CREATE TABLE subscriptions (
    id                     uuid PRIMARY KEY,
    workspace_id           uuid         NOT NULL UNIQUE REFERENCES workspaces (id),
    plan_id                uuid         NOT NULL REFERENCES billing_plans (id),
    status                 varchar(10)  NOT NULL
                               CHECK (status IN ('TRIAL', 'ACTIVE', 'GRACE', 'EXPIRED', 'SUSPENDED')),
    billing_period         varchar(7)   CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    current_period_end     timestamptz  NOT NULL,
    grace_ends_at          timestamptz,
    ending_reminder_for    timestamptz,
    created_at             timestamptz  NOT NULL,
    created_by             uuid,
    updated_at             timestamptz  NOT NULL,
    updated_by             uuid,
    version                bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_subscriptions_period_end ON subscriptions (current_period_end);

CREATE TABLE subscription_requests (
    id                 uuid PRIMARY KEY,
    workspace_id       uuid           NOT NULL REFERENCES workspaces (id),
    plan_id            uuid           NOT NULL REFERENCES billing_plans (id),
    billing_period     varchar(7)     NOT NULL CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    amount             numeric(19, 2) NOT NULL CHECK (amount >= 0),
    currency           varchar(3)     NOT NULL,
    payment_method_id  uuid           NOT NULL REFERENCES billing_payment_methods (id),
    payer_reference    varchar(100)   NOT NULL,
    paid_on            date           NOT NULL,
    receipt_file_id    uuid           NOT NULL REFERENCES stored_files (id),
    note               varchar(1000),
    status             varchar(10)    NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    requested_by       uuid           NOT NULL REFERENCES users (id),
    reviewed_by        uuid REFERENCES users (id),
    reviewed_at        timestamptz,
    review_note        varchar(1000),
    created_at         timestamptz    NOT NULL,
    created_by         uuid,
    updated_at         timestamptz    NOT NULL,
    updated_by         uuid,
    version            bigint         NOT NULL DEFAULT 0
);
CREATE INDEX ix_subscription_requests_status ON subscription_requests (status, created_at);
CREATE INDEX ix_subscription_requests_workspace ON subscription_requests (workspace_id, created_at DESC);
-- at most one open request per workspace
CREATE UNIQUE INDEX ux_subscription_requests_pending ON subscription_requests (workspace_id) WHERE status = 'PENDING';

-- append-only history of every status / plan / period change
CREATE TABLE subscription_events (
    id               uuid PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES workspaces (id),
    subscription_id  uuid          NOT NULL REFERENCES subscriptions (id),
    type             varchar(30)   NOT NULL,
    from_status      varchar(10),
    to_status        varchar(10),
    actor_kind       varchar(8)    NOT NULL CHECK (actor_kind IN ('SYSTEM', 'ADMIN', 'TENANT')),
    actor_user_id    uuid,
    detail           jsonb         NOT NULL DEFAULT '{}'::jsonb,
    occurred_at      timestamptz   NOT NULL
);
CREATE INDEX ix_subscription_events_workspace ON subscription_events (workspace_id, occurred_at DESC);

CREATE FUNCTION subscription_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'subscription_events is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_subscription_events_append_only BEFORE UPDATE OR DELETE ON subscription_events
    FOR EACH ROW EXECUTE FUNCTION subscription_events_append_only();

-- receipts are uploaded as files with their own purpose
ALTER TABLE stored_files DROP CONSTRAINT ck_files_purpose;
ALTER TABLE stored_files ADD CONSTRAINT ck_files_purpose CHECK (purpose IS NULL OR purpose IN
    ('BEFORE', 'DURING', 'AFTER', 'INSPECTION', 'REPORT', 'WARRANTY', 'EQUIPMENT', 'RECEIPT', 'OTHER'));

-- ---------------------------------------------------------------- provisional catalogue (admin-editable)
INSERT INTO billing_plans (id, code, name_ar, name_en, description_ar, description_en, price_monthly, price_yearly,
                           max_users, max_active_projects, max_jobs_per_month, max_storage_mb, sort_order,
                           created_at, updated_at)
VALUES
    ('01900000-0000-7000-8000-000000000001', 'SOLO', 'فني مستقل', 'Solo',
     'لفني أو مهندس يعمل بمفرده', 'For an independent technician or engineer',
     300.00, 3000.00, 1, 10, 100, 2048, 1, now(), now()),
    ('01900000-0000-7000-8000-000000000002', 'TEAM', 'فريق', 'Team',
     'لفريق صغير حتى 5 مستخدمين', 'For a small team of up to 5 users',
     900.00, 9000.00, 5, 50, 500, 10240, 2, now(), now()),
    ('01900000-0000-7000-8000-000000000003', 'BUSINESS', 'شركة', 'Business',
     'للشركات حتى 25 مستخدماً', 'For companies of up to 25 users',
     2500.00, 25000.00, 25, NULL, NULL, 51200, 3, now(), now());

-- existing workspaces start a trial on the team plan from today (14 days, D6)
INSERT INTO subscriptions (id, workspace_id, plan_id, status, current_period_end, created_at, updated_at)
SELECT gen_random_uuid(), w.id, '01900000-0000-7000-8000-000000000002', 'TRIAL', now() + interval '14 days', now(), now()
FROM workspaces w
WHERE NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.workspace_id = w.id);
