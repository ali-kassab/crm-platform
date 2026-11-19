# ADR-0004: Tenant isolation

- Status: Accepted (2026-11-18)
- The active workspace comes **only** from the validated token (`wid`). It is never taken from request bodies or paths.
- A `TenantContext` is set per request by a filter. Repositories always filter by `workspace_id`. A Hibernate `@Filter` adds a second, defense-in-depth layer.
- Ids from another tenant return **404** (not 403), so the API doesn't reveal that the resource exists.
- A `TenantIsolationIT` integration test suite checks every module.

