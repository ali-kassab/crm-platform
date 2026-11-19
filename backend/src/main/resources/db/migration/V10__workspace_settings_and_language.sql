-- Arabic-first workspace settings (D11): language, locale, currency and time zone are separate
-- settings; sector configures onboarding defaults later. Existing workspaces keep their values.

ALTER TABLE workspaces
    ADD COLUMN default_language varchar(5)  NOT NULL DEFAULT 'ar',
    ADD COLUMN currency         varchar(3)  NOT NULL DEFAULT 'EGP',
    ADD COLUMN sector           varchar(20),
    ADD CONSTRAINT ck_workspaces_language CHECK (default_language IN ('ar', 'en')),
    ADD CONSTRAINT ck_workspaces_currency CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_workspaces_sector CHECK (sector IS NULL OR sector IN ('FIT_OUT', 'SECURITY_SYSTEMS', 'MAINTENANCE'));

ALTER TABLE workspaces ALTER COLUMN locale SET DEFAULT 'ar-EG';
ALTER TABLE workspaces ALTER COLUMN time_zone SET DEFAULT 'Africa/Cairo';

-- NULL = follow the active workspace's default language
ALTER TABLE users
    ADD COLUMN preferred_language varchar(5),
    ADD CONSTRAINT ck_users_language CHECK (preferred_language IS NULL OR preferred_language IN ('ar', 'en'));
