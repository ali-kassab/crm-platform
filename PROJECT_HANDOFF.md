# PROJECT HANDOFF — Field Service & Project Operations SaaS

> Read this first in every new session. The repository state always wins over this file; verify with `git status` / `git log` before resuming.

## 1. Identity & directives
- Product: Arabic-first (RTL, English secondary) multi-tenant SaaS for **Fit-Out & Contracting**, **CCTV/Security/Access Control**, **Maintenance & Field Service** — Egypt first.
- Governing documents (outside the repo, in `D:\Workspace\Java CRM\`): the Master Project Directive (2026-11-19, in the conversation history), the Implementation Authorization (2026-11-19), and `PRODUCT_AUDIT.md` (Phase 1–2 audit, roadmap R0–R13, decisions D1–D16).
- Older context: `D:\Workspace\Java CRM\SESSION_HANDOFF.md` and `PROJECT_PROGRESS.md` describe the pre-pivot build (still accurate for modules up to finance).
- **Safe stop phrase:** «وقف عند نقطة آمنة وحدّث الهاند أوف» → finish/roll back the in-flight small change, verify git, update this file, commit/push only verified work.

## 2. Repository
- Path: `D:\Workspace\crm-platform` · remote `origin` = `github.com/ali-kassab/crm-platform` (private, owned by the user).
- Working branch: **`feature/saas-foundation`** (created from `main` @ `163d49c`). `main` must stay unchanged.
- Author/committer: `ali-kassab <alikassab155@gmail.com>` (local git config). No Claude co-author trailer.
- Push: verified commits → `origin feature/saas-foundation` automatically. Never main, never force.

## 3. Overall status
| Area | Status |
|---|---|
| Backend modules shared, identity, files, milestones, crm, work, operations, finance | ✅ implemented + tested (pre-pivot) |
| R0 Security & foundations | ✅ complete (2026-11-19) |
| R1 Localization & workspace settings | ⏭ next (not started) |
| R2–R13 | ⚪ not started |
| Frontend, NestJS realtime, subscriptions | ⚪ not started |

## 4. Completed — R0 Security & foundations
1. **Tenant isolation is fail-closed** (audit S1, critical) — commit `17f1fff`.
   - `shared/tenant/TenantContext.NONE`: default tenant when no context; matches no row. `TenantIdentifierResolver` returns `NONE`; only an explicit `runAs(SYSTEM, …)` is root.
   - `shared/persistence/TenantEntity.requireWorkspaceScope()` (`@PrePersist`): rejects inserts with no context, `NONE` or `SYSTEM`.
   - `shared/tenant/TenantWork`: sets the workspace **then** opens a `REQUIRES_NEW` tx (Hibernate fixes the tenant at session open); `callAsSystem` for platform schedulers.
   - Listeners moved off `@ApplicationModuleListener` (it opened the tx before the tenant was set → ran as root when events were republished at startup; e.g. `clearRepresentative` could span workspaces): `CrmEventHandlers`, `WorkIntegration.Handlers`, `OperationsIntegration.Handlers` now use `@TransactionalEventListener` + `TenantWork`; schedulers `OrphanFileCleanup`, `WarrantyExpiry` use `callAsSystem`.
   - ArchUnit guards (`architecture/LayeringRulesTest`): no `@ApplicationModuleListener`; no `@Transactional` on `@TransactionalEventListener`/`@Scheduled` methods.
   - `security/TenantIsolationIT` (4): no-context query → 0 rows; no-context/SYSTEM insert rejected; member deactivation stays in its workspace; cross-workspace GET/PATCH/DELETE/void/move matrix → 404, lists empty, foreign references rejected.
2. **Audit persistence** (S3) — commit `bc33f2a`.
   - Flyway `V9__audit_and_platform.sql`: `audit_entries` (workspace NULL = platform-level; trigger rejects UPDATE/DELETE) and `platform_markers`.
   - Module `audit`: `domain/AuditEntry` (not a TenantEntity; reads filter the workspace explicitly), `domain/AuditEntryRepository` (`searchWorkspace`, `searchPlatform`), `application/AuditRecorder` (synchronous `@EventListener` in the same tx as the change; stores the trace id), `application/AuditQueries`, `web/AuditController`: `GET /api/v1/audit` (audit:read, caller's workspace only), `GET /api/v1/admin/audit` (platform:admin, platform rows only).
   - `shared/events/AuditTrail.platform(...)` for platform-level actions. Money in audit snapshots formatted with 2 decimals.
   - `audit/AuditIT` (2): rows per workspace with field diffs, no cross-workspace reads, MEMBER forbidden, append-only enforced, a failed change leaves no row.
3. **First platform admin** (S2) — commit `365a68f`.
   - `identity/application/PlatformAdminBootstrap` (ApplicationRunner) reads `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` (+ optional `PLATFORM_ADMIN_FIRST_NAME`, `PLATFORM_ADMIN_LAST_NAME`, `PLATFORM_ADMIN_SESSION_TTL` default 10m) through `PlatformAdminProperties` (`app.platform-admin.*` in `application.yml`; empty placeholders in `.env.example`). Runs once: refuses if an admin or the `PLATFORM_ADMIN_BOOTSTRAPPED` marker exists, if the email already belongs to an account (never promotes), or if the password is weak. Hashed with the existing delegating encoder; never logged.
   - `identity/application/PlatformAdminService` + `identity/web/PlatformAdminController`: `POST /api/v1/admin/auth/login` (public; short-lived platform token with `wid = NONE`, no refresh token), `GET /api/v1/admin/account`, `POST /api/v1/admin/account/email`, `POST /api/v1/admin/account/password` (current password required; admin passwords ≥ 14 chars). `/auth/password/change` refuses admins (`USE_ADMIN_ACCOUNT_ENDPOINT`); the e-mailed reset applies the admin rule and is audited.
   - Also: `AccessTokenService.issuePlatform`, `User.changeEmail`, `UserRepository.existsBySuperAdminTrue`, `identity/domain/PlatformMarker(+Repository)`, public POST for admin login in `SecurityConfig`.
   - Platform audit actions: `ADMIN_BOOTSTRAPPED`, `ADMIN_LOGIN`, `ADMIN_LOGIN_FAILED`, `ADMIN_EMAIL_CHANGED`, `ADMIN_PASSWORD_CHANGED`, `ADMIN_PASSWORD_RESET` (no secrets).
   - `identity/application/PlatformAdminIT` (1, full lifecycle; skips on a reused database that already has an admin).
   - **How the owner creates their account:** put `PLATFORM_ADMIN_EMAIL` and `PLATFORM_ADMIN_PASSWORD` (≥ 14 chars, letters + digits) in `.env`, start the backend once, look for the log line "Platform admin account created", then remove `PLATFORM_ADMIN_PASSWORD`. Login identifier = email (the system's username).
4. **Performance foundations** — commit `b4f2299` (measured, not assumed).
   - `server.compression` for JSON/text ≥ 1 KB: `/v3/api-docs` 91,141 → 7,902 bytes (`performance/CompressionIT`, real embedded server).
   - `ProjectService.findAll` batches managers (`TeamMemberRepository.findByProjectIdInAndManagerTrue`); statement count identical for 2 and 6 projects (≤ 4).
   - Over-budget filter uses `work/projects/domain/ProjectBudgetQuery` (id, budget tuples); a 2-row page hydrates ≤ 2 entities.
   - `TaskService` comment counts via `TaskCommentRepository.countByTaskId`.
   - `performance/PerformanceGuardsIT` (2).

## 5. Current phase & exact next steps — R1 Localization & workspace settings (backend)
Not started. Planned order:
1. Flyway `V10`: `workspaces` + `default_language` (default `ar`), `currency` (default `EGP`), `sector` (nullable: `FIT_OUT`, `SECURITY_SYSTEMS`, `MAINTENANCE`); new-workspace defaults `locale` → `ar-EG`, `time_zone` → `Africa/Cairo`; `users.preferred_language` (nullable → falls back to the workspace).
2. Workspace settings API (`PATCH /api/v1/workspace` extended, `workspace:manage`) and `PATCH /api/v1/me` language; registration accepts optional language/sector.
3. Backend i18n: `MessageSource` (`messages.properties` en, `messages_ar.properties` ar) keyed by the existing stable error codes; locale = explicit `Accept-Language` > user preference > workspace default > `ar`; `GlobalExceptionHandler` localizes `title`/`detail` and validation messages, keeps `code` unchanged.
4. Arabic e-mail templates (invitation, password reset) chosen by the recipient's language.
5. Tests: the same error in ar/en; Arabic names/addresses round-trip; Arabic search (Arabic letter normalisation أ/ا/إ, ى/ي, ة/ه not handled yet — decide in R2).
Then R2 customers → R3 work orders / visits / technician role (roadmap: `D:\Workspace\Java CRM\PRODUCT_AUDIT.md` §15).

## 6. Tests (actual results)
- 2026-11-19, `./mvnw test` on `feature/saas-foundation` @ `b4f2299`: **94 tests, 0 failures, 0 errors, 0 skipped** (Testcontainers: postgres:17.12-alpine, redis:8.2.11-alpine, adobe/s3mock:5.2.3). Docker Desktop must be running; on a stale-socket crash run `D:\Workspace\tools\fix-docker-sockets.ps1`.

## 7. Known issues / open security items
- S4 no subscription enforcement (R7). S5 no technician scoping (R3). S6 any workspace member can read any workspace file by id (review in R4). S7 rate limiting only on auth, uploads and admin login. S8 the HS256 secret would have to be shared with NestJS (evaluate RS256 in R11).
- Platform admin sessions are access-token only (10 min default); re-login on expiry is by design.
- `PlatformAdminIT` needs a fresh database (it skips otherwise).

## 8. Decisions
- Approved (2026-11-19): D1 visits as a separate entity; D2 standalone jobs; D3 defer the automation engine; D4 NestJS realtime (evaluate STOMP/Redis Streams/RS256 before changing); D5 7-day grace → read-only, never delete; D6 14-day trial; D7 configurable limits; D8 TECHNICIAN + FINANCE roles; D9 hide flowcharts; D10 customer kind + optional email; D11 ar-EG / EGP / Africa/Cairo defaults; D12 materials as job cost lines; D13 branch `feature/saas-foundation`; D14 staff-recorded approvals; D15 Arabic PDF libraries later; D16 roadmap order. Commercial values are provisional and configurable. The user is the first Platform Admin; the dashboard UI is deferred to the final stage.
- Waiting for approval: none.

## 9. Technical decisions (with reasoning)
- Fail-closed via a non-matching sentinel tenant, not by throwing in the resolver: Hibernate resolves a tenant for every session, including public endpoints that touch only global identity tables.
- Listeners run after commit on the publishing thread (no `@EnableAsync`); `TenantWork` makes the workspace explicit regardless of thread state.
- Audit rows are written synchronously in the business transaction (atomic) and are not tenant entities (platform rows have no workspace); every read query filters the workspace explicitly and is tested.
- The platform admin uses a separate login endpoint with access-token-only sessions scoped to `NONE`: `refresh_tokens.workspace_id` is a NOT NULL FK, and an admin must get neither a tenant nor a root scope. Login identifier is the email.
- Compression is a connector setting, so it is tested against a real embedded server; query guards use Hibernate statistics.

## 10. Session log (all pushed to `origin/feature/saas-foundation`)
| Commit | Message |
|---|---|
| `17f1fff` | fix(security): make tenant resolution fail closed and scope listeners explicitly |
| `be009d7` | docs: add project handoff for the SaaS foundation work |
| `bc33f2a` | feat(audit): persist audit events append-only with per-workspace reads |
| `365a68f` | feat(identity): bootstrap the first platform admin securely from the environment |
| `b4f2299` | perf: gzip responses and remove per-row queries from project lookups |
| (next commit) | docs: update handoff — R0 complete |

## 11. Uncommitted / unpushed
- None after the handoff commit. `main` is untouched at `163d49c`.
