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
| R1 Localization & workspace settings (backend) | ✅ complete (2026-11-19, `2f8fab6`) |
| R2 Customers | ✅ complete (2026-11-19, `88d9473`) |
| R3 Work orders, visits, technician role | ⏭ next (not started) |
| R4–R13 | ⚪ not started |
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

## 4b. Completed — R1 Localization & workspace settings (backend) — commit `2f8fab6`
- Flyway `V10__workspace_settings_and_language.sql`: `workspaces.default_language` (default `ar`), `currency` (default `EGP`), `sector` (`FIT_OUT` / `SECURITY_SYSTEMS` / `MAINTENANCE`, nullable); defaults `locale` `ar-EG`, `time_zone` `Africa/Cairo`; `users.preferred_language` (nullable). Existing rows keep their values.
- `identity/domain/Workspace` (`Sector`, `configure`, `DEFAULT_*`), `User.preferredLanguage` + `effectiveLanguage(workspace)`.
- APIs: `PATCH /api/v1/workspace` is now a **partial** update (name, defaultLanguage, locale, currency [ISO 4217], timeZone [IANA], sector; `workspace:manage`) via `WorkspaceService.updateSettings`; `PUT /api/v1/me/language` `{language: "ar"|"en"|null}`; registration accepts optional `language` (`en` → locale `en-EG`) and `sector`. `AuthResponse.language`, `WorkspaceSummary` (+defaultLanguage, currency, sector), `UserSummary.preferredLanguage`.
- Access tokens carry `lng` (effective language) → `AuthenticatedUser.language`.
- `shared/i18n`: `Languages` (ar default, en), `RequestLanguage` (bean `localeResolver`: Accept-Language > token language > ar), `ErrorTexts` (Arabic by exact English message from `resources/i18n/error-texts_ar.tsv`, then `error.<CODE>` in `resources/i18n/messages_ar.properties`, then generic; English keeps the original message). `GlobalExceptionHandler` + `identity/security/ProblemWriter` localize title/detail/field messages; `code` unchanged. `ApiException.notFound` adds a `resource` property (used for "X غير موجود").
- `spring.messages.basename: i18n/messages` (UTF-8). `shared/mail/EmailTemplates.render(template, language, vars)` + `subject(...)`; Arabic RTL templates `templates/email/*_ar.html`; invitation uses the invitee's language (existing account) else the workspace default; password reset uses the user's language.
- Tests: `shared/i18n/LocalizationIT` (6) and `shared/i18n/ErrorTextsCoverageTest` (2; fails if any literal error message lacks Arabic text or any error code lacks an Arabic fallback).
- **Rule for new code:** every new `ApiException` message needs a line in `error-texts_ar.tsv` and every new code an `error.<CODE>` key — the coverage test enforces it.
- Known gaps: messages built at runtime (e.g. `field + " is required"`) show the per-code Arabic fallback; Arabic workspace names produce an ASCII slug like `workspace-…`; Arabic letter normalisation in search (أ/ا/إ, ى/ي, ة/ه) is not handled yet.

## 4c. Completed — R2 Customers — commit `88d9473`
- Flyway `V11__customers_and_arabic_search.sql`: `contacts.kind` (`INDIVIDUAL`/`COMPANY`), `email`, `first_name`, `last_name` nullable; checks `ck_contacts_kind_name` (individual → first name, company → company) and `ck_contacts_reachable` (≥ 1 of email/phone/mobile/work_phone); SQL function `app_search_norm(text)`.
- `crm/contacts`: `Contact.Kind`, `personName()`, `fullName()` = company name for companies; `ContactService.requireIdentity` (field errors `name.first` / `company` / `phone`); email null clears it; `GET /contacts?kind=`; `kind` in request/response.
- Search: `shared/web/SearchText` (Java twin of `app_search_norm`), `Pagination.likePattern` normalises the query; `shared/persistence/SearchFunctionContributor` (registered in `META-INF/services/org.hibernate.boot.model.FunctionContributor`) so JPQL uses `app_search_norm(x) like :q`; all Specs use `SearchText.column(cb, …)`. **Rule:** new searches must compare `app_search_norm(column)` with `Pagination.likePattern(q)`.
- New module **`reporting`** (top of the dependency graph, read-only): `application/CustomerHistoryService`, `web/CustomerHistoryController` → `GET /api/v1/contacts/{id}/history` (crm:read; money only with finance:read). New public APIs: `work.Projects.ofContact`, `operations.Jobs.ofProjects` (+`JobOverview`), `operations.Warranties` (implemented by `WarrantyService`), `finance.Accounting` (implemented by `AccountingService`).
- Tests: `crm/CustomersIT` (5, incl. Java↔SQL normalisation parity); `ContactsIT`, `LocalizationIT` adjusted.
- Not done: no trigram index for `%…%` searches yet (fine for current volumes; measure in R13).

## 5. Current phase & exact next steps — R3 Work orders, visits, technician role
Not started. Planned order (extend `operations.jobs.Job`, do not duplicate it — D1, D2, D8):
1. Flyway `V12`: `jobs.project_id` nullable; add `contact_id`, `location_id` (required for standalone jobs; derived from the project for project jobs), `type` (`INSTALLATION`, `PREVENTIVE_MAINTENANCE`, `CORRECTIVE_REPAIR`, `INSPECTION`), `status` (`NEW`, `SCHEDULED`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETED`, `CANCELLED`), `priority` (`LOW`, `NORMAL`, `HIGH`, `URGENT`), `reported_issue`, `internal_notes`, `actual_start_at`, `completed_at`; budget optional (default 0); job assignees as workspace users so a job needs no project team.
2. `job_visits` (D1): job, scheduled start/end (timestamptz), assigned user(s), status (`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `MISSED`, `CANCELLED`), actual start/end, outcome, notes, `rescheduled_from_id`; idempotent daily job marks past, never-started visits `MISSED`.
3. Roles (D8): `TECHNICIAN` (sees and updates only jobs/visits assigned to them; can upload and report) and `FINANCE`; server-side scoping in queries.
4. API: `/jobs` extended (filters type/status/priority/assignee/contact/location/date), standalone job creation, status transitions; `/jobs/{id}/visits`; `/visits?assignee=me&from&to&status` (calendar, upcoming, overdue, missed); `POST /visits/{id}/start|complete|reschedule|cancel`.
5. Finance: allow transactions on standalone jobs (`transactions.project_id` nullable, contact from the job) and keep rollups correct.
6. Tests: independent technician end-to-end (one-user workspace), company with technicians, technician isolation, visit lifecycle, missed-visit job idempotency, project jobs unchanged.
Then R4 visit reports, photo purposes, materials, approvals (roadmap: `D:\Workspace\Java CRM\PRODUCT_AUDIT.md` §15).

## 6. Tests (actual results)
- 2026-11-19, `./mvnw test` on `feature/saas-foundation` @ `88d9473`: **107 tests, 0 failures, 0 errors, 0 skipped** (Testcontainers: postgres:17.12-alpine, redis:8.2.11-alpine, adobe/s3mock:5.2.3). Docker Desktop must be running; on a stale-socket crash run `D:\Workspace\tools\fix-docker-sockets.ps1`.

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
| `0d575bf` | docs: update handoff - R0 security and foundations complete |
| `2f8fab6` | feat(i18n): Arabic-first workspace settings, user language and localized errors |
| `19a7e3e` | docs: update handoff - R1 localization complete |
| `88d9473` | feat(crm): individual and company customers, Arabic-aware search, customer history |
| (next commit) | docs: update handoff - R2 complete |

## 11. Uncommitted / unpushed
- None after the handoff commit. `main` is untouched at `163d49c`.
