# Architecture Decision Records (proposed)

Format: Context → Decision → Consequences. Each ADR is copied into the repo as `docs/adr/NNNN-*.md`.

## ADR-0001 Modular monolith with Spring Modulith
- **Context:** there are 10 cohesive business areas, a single team, and a CV/portfolio goal. Microservices would add operational cost with no benefit.
- **Decision:** one Spring Boot application.
  - The top-level package per module is `com.crmplatform.<module>`.
  - Spring Modulith verifies the module boundaries in a test.
  - Modules interact through `api` packages (public services/DTOs) and domain events. Event publications are persisted in the Modulith event registry (JDBC).
  - Inside each module:
    - the rich modules (automation, work/board, finance) use `domain` / `application` / `infrastructure` / `web` packages;
    - the CRUD-heavy modules use `web` → `application` → `persistence` with thin entities.
- **Consequences:** a simple deployment (one container) with enforceable boundaries. A module can be extracted later if ever needed.

## ADR-0002 PostgreSQL 17, UUIDv7 keys, Flyway
- Primary keys are `uuid`, generated in the application as **UUIDv7** (time-ordered, good index locality).
- All tenant-owned tables have `workspace_id uuid not null`, plus FK and index.
- Soft delete uses `deleted_at timestamptz`, `deleted_by uuid`. Uniqueness uses partial unique indexes `WHERE deleted_at IS NULL`. Reads exclude deleted rows by default (Hibernate `@SQLRestriction`).
- Auditing columns: `created_at`, `created_by`, `updated_at`, `updated_by`. Optimistic locking: `version bigint` on mutable aggregates.
- Money is `numeric(19,2)` mapped to `BigDecimal`. Free-form structures use `jsonb`.
- Flyway migrations `V{n}__{description}.sql` are never edited after merge. `ddl-auto=validate`.

## ADR-0003 Authentication: stateless JWT + rotating refresh tokens
- Access token: JWT (HS256 via the Nimbus encoder/decoder in Spring Security's resource server), **15 min** lifetime.
  - Claims: `sub` (user id), `wid` (active workspace id), `role`, `perms` (permission list), `sa` (super-admin flag), `jti`.
- Refresh token: an opaque random 256-bit value. Only its SHA-256 hash is stored in the `refresh_tokens` table.
  - It belongs to a **family** (rotation chain), lives **14 days** sliding with a **60-day** absolute limit, and is single use.
  - **Reusing a rotated token revokes the whole family** (theft detection).
- The browser receives the refresh token in an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. The access token is returned in the body and kept in memory.
- CSRF: the cookie-authenticated endpoints (`/auth/refresh`, `/auth/logout`) require the `X-Requested-With: XMLHttpRequest` header plus an Origin check. Other endpoints use the Bearer token, so they aren't CSRF-prone.
- Passwords: BCrypt cost 12 (`DelegatingPasswordEncoder`). Lockout after 5 failed logins for 15 minutes (counter in Redis).
- Logout revokes the current family. Logout-all revokes all of the user's families and adds the current access-token `jti` to a Redis denylist until it expires.

## ADR-0004 Tenant isolation
- The active workspace comes **only** from the validated token (`wid`). It is never taken from request bodies or paths.
- A `TenantContext` is set per request by a filter. Repositories always filter by `workspace_id`. A Hibernate `@Filter` adds a second, defense-in-depth layer.
- Ids from another tenant return **404** (not 403), so the API doesn't reveal that the resource exists.
- A `TenantIsolationIT` integration test suite checks every module.

## ADR-0005 Authorization: fixed roles → permissions
- Roles are Owner, Admin, Member and Viewer, mapped to permission constants (see AUTHENTICATION_AND_AUTHORIZATION.md). Endpoints use `@PreAuthorize("hasAuthority('crm:write')")`.
- Resource-level ownership rules (e.g. only the note's author may edit it) live in the application services.

## ADR-0006 Real-time: REST mutations + STOMP delta events
- All board mutations go through REST with optimistic versioning.
- After commit, the server publishes delta events to `/topic/workspaces/{wid}/boards/{boardId}` over STOMP.
- The JWT is checked on CONNECT. SUBSCRIBE is authorized per board (the board must belong to the token's workspace).
- Multiple instances: events are relayed through Redis pub/sub, and each instance forwards them to its local simple broker.

## ADR-0007 Automation engine: persistent run steps + ShedLock scheduler
- Runs and steps are persisted with status and `due_at`.
- A scheduler (every 5 s, ShedLock-protected) claims due steps with `FOR UPDATE SKIP LOCKED` in batches of 50.
- Each command is a strategy (`ActionHandler`). The commands are validated when a procedure is saved.
- Loop guard: the chain depth is tracked per triggering event (max 5), and at most one active run is allowed per (entity, procedure).

## ADR-0008 Financial rollups are computed, not stored
- Contract, received, actual cost, remaining and P/L are computed with indexed aggregate queries over `transactions`, so they can never disagree with the ledger.
- Transactions are immutable. A correction is made by a **void** (NF-03): the voided row is excluded from the sums and the reason is recorded.

## ADR-0009 Files: private bucket + presigned URLs
- Uploads go through the API (multipart, size and type checked by content sniffing with Apache Tika).
  - Images are re-encoded to WebP (max 2048 px) to strip metadata.
  - Documents are pdf/doc(x)/xls(x)/ppt(x).
- Object key: `{workspaceId}/{kind}/{uuid}`. A `stored_files` row holds the metadata and owner.
- Downloads use 5-minute presigned GET URLs with `Content-Disposition: attachment` for documents.
- Local development uses an S3-compatible container (choice recorded in the repo ADR).

## ADR-0010 Redis usage
1. Rate limiting (Bucket4j + Lettuce): login, register, password reset, uploads.
2. Login-failure counters and lockout.
3. Access-token `jti` denylist (TTL = remaining token life).
4. Caches with TTL and evict-on-write: reference data (categories, statuses, tags) for 10 min; permissions per (user, workspace) for 5 min.
5. Pub/sub fan-out of board events.

If Redis is down: caching falls back to the database, the rate limiter **fails open** with a logged warning, the denylist **fails closed** only for logout-all (otherwise tokens expire in at most 15 min anyway), and readiness reports DEGRADED.

## ADR-0011 Error format: RFC 9457 Problem Details
- `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, `code` (stable machine code), `errors[]` (field violations), and `traceId`.

## ADR-0012 Frontend: React + Vite + TypeScript + Tailwind + shadcn/ui
- UI is built only from shadcn/ui components, blocks and vetted shadcn-registry components (kanban board, flowchart editor via React Flow).
- TanStack Query for server state, React Router, react-hook-form + zod, and an OpenAPI-generated typed client.
- In production the SPA is served from the same origin as the API (nginx locally, CloudFront in the AWS design).

## ADR-0013 Observability
- Actuator health groups (liveness, readiness), Micrometer → Prometheus, OpenTelemetry tracing (OTLP exporter, off by default locally), and Spring Boot structured JSON logging with `traceId`, `workspaceId` and `userId`.
- Prometheus and Grafana run under the Compose `observability` profile.
