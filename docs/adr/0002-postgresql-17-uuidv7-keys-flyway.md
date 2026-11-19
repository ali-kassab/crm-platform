# ADR-0002: PostgreSQL 17, UUIDv7 keys, Flyway

- Status: Accepted (2026-11-18)
- Primary keys are `uuid`, generated in the application as **UUIDv7** (time-ordered, good index locality).
- All tenant-owned tables have `workspace_id uuid not null`, plus FK and index.
- Soft delete uses `deleted_at timestamptz`, `deleted_by uuid`. Uniqueness uses partial unique indexes `WHERE deleted_at IS NULL`. Reads exclude deleted rows by default (Hibernate `@SQLRestriction`).
- Auditing columns: `created_at`, `created_by`, `updated_at`, `updated_by`. Optimistic locking: `version bigint` on mutable aggregates.
- Money is `numeric(19,2)` mapped to `BigDecimal`. Free-form structures use `jsonb`.
- Flyway migrations `V{n}__{description}.sql` are never edited after merge. `ddl-auto=validate`.

