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
| R3 Work orders, visits, technician role | ✅ complete (2026-11-19, `7438edf`) |
| R4 Visit reports, photo purposes, materials, approvals | ✅ complete (2026-11-19, `376987b`) |
| R5 Notifications (in-app) | ✅ complete (2026-11-19, `20cfa98`) |
| R6 Equipment, warranties, preventive maintenance, SLA | ✅ complete (2026-11-19, `17ed30a`) |
| R7 Subscriptions & Platform Admin | ✅ complete (2026-11-19, `c9d34e0`) |
| R8 Finance alignment | ✅ complete (2026-11-19, `f8c850a`) |
| R9 Frontend foundation | ⏭ next (not started) |
| R10–R13 | ⚪ not started |
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

## 4d. Completed — R3 Work orders, visits, technician role — commit `7438edf`
- Flyway `V12__work_orders_visits_roles.sql`: jobs `project_id` nullable, `contact_id` + `location_id` NOT NULL (backfilled from projects), `type`, `status`, `priority`, `reported_issue`, `internal_notes`, `actual_start_at`, `completed_at`, dates optional, budget default 0; `job_assignees`; `job_visits` + `visit_assignees`; `transactions.project_id` nullable; membership/invitation role checks include `TECHNICIAN`, `FINANCE`.
- `operations/jobs/domain/Job` = the work order (`Type`, `Priority`, `Status` with allowed transitions in `Status.canMoveTo`, `moveTo`, `assign`); `JobService` (standalone jobs via `resolveSite`, `changeStatus`, `advanceForVisit`, `ensureAssigned`), `JobAccess` (technician scoping: `ops:assigned` without `ops:read` → only assigned work, 404 otherwise), `JobController` (`POST /jobs/{id}/status`, new filters, customer + site embedded).
- `operations/visits` (domain `JobVisit`, `VisitService`, `VisitController`): `/jobs/{id}/visits`, `/visits?assignee=me&from&to&status&overdue`, `/visits/{id}/start|complete|cancel|reschedule`; hourly `MissedVisits` (ShedLock, `missed-visits-cron`, 2 h grace).
- `Role.TECHNICIAN` (refdata:read, ops:assigned, files:upload), `Role.FINANCE`; `Permissions.OPS_ASSIGNED`.
- `crm.Contacts.ContactSummary` +mobile, `LocationSummary` +addressLine1, accessInstructions, latitude, longitude. CRM delete guards ask every `ProjectUsage` bean (operations registers `jobUsage`).
- Finance: transactions on standalone jobs (customer from the job); customer contract = project budgets + standalone job budgets. `operations.Jobs` (+`ofContact`, `standaloneBudgetTotalOfContact`), `operations.Warranties.ofProjectsOrJobs`; customer history lists all jobs.
- Tests: `operations/WorkOrdersIT` (5).

## 4e. Completed — R4 Visit reports, photo purposes, materials, approvals — commit `376987b`
- Flyway `V13__reports_files_materials_approvals.sql`: `stored_files.purpose` + `thumbnail_key`; `visit_reports` (unique per visit); `job_materials`; `job_approvals`.
- files: `FilePurpose`, `FileOwnerAccess` SPI, `FileSummary` (+purpose, hasThumbnail), 320 px WebP thumbnails (`FileInspector.THUMBNAIL_EDGE`), `GET /files/{id}/thumbnail-url`, upload `purpose` param; `Permissions.FILES_READ` (all roles except TECHNICIAN); technicians open only own uploads or files of assigned work (`OperationsIntegration.operationsFileAccess`: owner types JOB, VISIT, VISIT_REPORT, JOB_APPROVAL).
- `operations/visits`: `VisitReport(+Repository)`, `VisitReportService`, `VisitReportController` (`GET/PUT /visits/{id}/report`, `GET/POST /visits/{id}/files`).
- `operations/jobs`: `JobMaterial`, `JobApproval` (+repositories), `JobExecutionService`, `JobExecutionController` (`/jobs/{id}/materials`, `/jobs/{id}/approvals`, `.../approvals/{aid}/decision`).
- Finance: `materialsCost` in job/project/customer rollups (`Jobs.materialsCostOf*`), profit = contract − actual cost − materials cost; `finance.Accounting.Totals` and history `Totals` include it.
- Tests: `operations/FieldEvidenceIT` (2).

## 4f. Completed — R5 In-app notifications — commit `20cfa98`
- Flyway `V14__notifications.sql`: `notifications` (workspace, recipient_user_id, type, entity_type, entity_id, actor_user_id, payload jsonb, read_at + TenantEntity columns); indexes for "mine newest first" and unread.
- Module `notifications`: `domain/Notification` (enum `Type`: TASK_ASSIGNED, PROJECT_MEMBER_ADDED, PROJECT_MEMBER_REMOVED, PROJECT_MANAGER_ASSIGNED, PROJECT_DELETED, JOB_ASSIGNED, VISIT_SCHEDULED, VISIT_RESCHEDULED), `NotificationRepository` (all queries bound to the recipient; `markAllRead` also filters the workspace explicitly), `application/NotificationService`, `application/NotificationHandlers` (`@TransactionalEventListener` + `TenantWork`), `web/NotificationController`.
- APIs (any signed-in user, own rows only): `GET /api/v1/notifications?unread=`, `GET /notifications/unread-count`, `POST /notifications/{id}/read` (404 for anyone else's), `POST /notifications/read-all`. Text is rendered by the client from `type` + `payload` (ar/en).
- New events `operations/OperationsEvents.JobAssigned` (published by `JobService.create/update` for newly added assignees only) and `VisitScheduled` (`VisitService.schedule/reschedule`). `JobService.ensureAssigned` (visit-driven) publishes nothing — the visit notification covers it. The actor is never notified of their own action.
- `resource.Notification` added to `messages_ar.properties`.
- Tests: `notifications/NotificationsIT` (3).
- Not done (by design for now): no retention/cleanup of old notifications, no e-mail/push delivery, no realtime push (R11).

## 4g. Completed — R6 Equipment, warranties, maintenance plans, SLA — commit `17ed30a`
- Flyway `V15__equipment_maintenance_sla.sql`: `equipment` (unique lower(serial) per workspace), `job_equipment`, warranties +`contact_id`/`location_id`/`equipment_id`/`reminder_days` (default 30)/`reminder_sent_at`, `maintenance_plans` (+assignees, +equipment), `maintenance_occurrences` UNIQUE(plan_id, due_date), jobs +`response_due_at`, +`maintenance_plan_id`.
- `operations/equipment`: `Equipment`, `EquipmentService`, `EquipmentController` — `/api/v1/equipment` CRUD (filters contactId, locationId, category, status, q), detail has `serviceHistory` + `warranties`; `GET/PUT /jobs/{id}/equipment` (technicians on own jobs). Codes `DUPLICATE_SERIAL`, `EQUIPMENT_NOT_AT_SITE`.
- Warranties: device implies customer + site; job/project imply customer; `JOB_NOT_OF_CUSTOMER`; filters `contactId`, `equipmentId`; reminder resets when end date or reminder days change.
- `operations/maintenance`: `MaintenancePlan` (due dates computed from start date, no month drift; bounded by end date and `includedVisits`), `MaintenanceOccurrence`, `MaintenanceService` (`generateDue` idempotent, max 3 catch-up per run), `MaintenanceIntegration` (`SlaPolicy` bean, `siteAssetsUsage` delete guard, `MaintenanceDaily.runOnce()` @ `app.operations.maintenance-cron` default `0 15 0 * * *`, ShedLock `maintenance-daily`: generate → overdue reminders → warranty reminders → renewal reminders (30 days) → end expired plans). `/api/v1/maintenance-plans` CRUD, `GET /overdue`, `POST /{id}/generate`.
- SLA: job `responseHours` (1–720) → `response_due_at`; unplanned jobs at a site with an active plan get its shortest `responseHours`; PM jobs get none. `JobResponse.sla {responseDueAt, respondedAt, breached}`; `GET /jobs?slaBreached=true`.
- `WorkspaceMembers.managers()` (active OWNER/ADMIN). Notifications: `WARRANTY_EXPIRING`, `MAINTENANCE_OVERDUE`, `MAINTENANCE_PLAN_ENDING`.
- Tests: `operations/MaintenanceIT` (3; uses JDBC to move dates into the past — no controllable clock).
- Known limits: dates use the server clock (UTC), not the workspace time zone; generation runs daily (or on demand).

## 4h. Completed — R7 Subscriptions & Platform Admin — commit `c9d34e0`
- Flyway `V16__billing.sql`: `billing_plans` (seeded **provisional** SOLO 300/3000 EGP 1 user, TEAM 900/9000 5 users, BUSINESS 2500/25000 25 users — prices are placeholders for the owner to set), `billing_payment_methods`, `subscriptions` (UNIQUE workspace), `subscription_requests` (one PENDING per workspace), `subscription_events` (append-only trigger); `RECEIPT` file purpose; existing workspaces backfilled with a 14-day TEAM trial.
- Module `billing` (platform-level, explicit workspace filters): `domain/{Plan, PaymentMethod, Subscription, SubscriptionRequest, SubscriptionEvent, BillingPeriod, BillingRepositories}`, `application/{BillingProperties (app.billing.*), SubscriptionService, BillingAdminService, PlanLimits (UsageGuard impl), UsageCounter (read-only JDBC counts), BillingIntegration (trial on WorkspaceCreated in the same tx; daily `SubscriptionLifecycle` ShedLock `subscription-lifecycle`)}`, `web/{SubscriptionController, BillingAdminController, SubscriptionEnforcement}`; `BillingEvents.SubscriptionNotice` → notifications `SUBSCRIPTION_*`.
- Lifecycle: TRIAL/ACTIVE → GRACE (7 d, writable) → EXPIRED (read-only); SUSPENDED (admin, read-only); status is computed on read too (`effectiveStatus`), so access never depends on the scheduler. Read-only = every non-GET `/api/**` returns **402 `SUBSCRIPTION_INACTIVE`** except `/subscription`, `/auth/`, `/me`, `/notifications`, `/admin/` and receipt upload (`POST /files?purpose=RECEIPT`). Nothing is ever deleted. Flag `BILLING_ENFORCEMENT_ENABLED=false` disables read-only mode and limits.
- Tenant APIs: `GET /subscription` (status, plan, limits, usage, daysLeft, pendingRequest), `GET /subscription/plans`, `GET /subscription/payment-methods`, `GET/POST /subscription/requests` + `POST /requests/{id}/cancel` (`workspace:manage`). Tenants can never change status.
- Admin APIs (`platform:admin`): `/admin/plans` (GET/POST/PATCH), `/admin/payment-methods` (GET/POST/PATCH), `/admin/subscription-requests` (list by status, detail, `receipt-url`, `approve`, `reject` with reason), `/admin/workspaces` (list with status/q), `/admin/workspaces/{id}` (subscription + history + requests), `POST /admin/workspaces/{id}/subscription` `{action: EXTEND|SUSPEND|REACTIVATE|CHANGE_PLAN}`. All audited via `AuditTrail.platform`.
- Limits (`shared/limits/UsageGuard`): USERS (invite + accept), ACTIVE_PROJECTS, JOBS_PER_MONTH (plan-generated PM jobs exempt), STORAGE_BYTES → 409 `PLAN_LIMIT_REACHED`. Injected as `ObjectProvider<UsageGuard>` constructor params (ArchUnit forbids `@Autowired`).
- `Files` API: `downloadUrlOfOwned`, `usedBytes`.
- Tests: `billing/BillingIT` (3; admin sessions via `AccessTokenService.issuePlatform` for a throwaway user so `PlatformAdminIT` keeps its fresh-bootstrap path).
- Not done: subscription e-mails (in-app notices only); `PlatformAdminIT`-style UI is R10.

## 4i. Completed — R8 Finance alignment — commit `f8c850a`
- Flyway `V17__finance_alignment.sql`: `transactions.job_id` nullable + `ck_transactions_parent` (job or project); reversal documented in the file.
- `TransactionService.record`: job (project or standalone) or project alone; contact defaults to the job's/project's customer; `TRANSACTION_PARENT_REQUIRED`.
- `finance/transactions/application/OutstandingBalances` + `GET /api/v1/accounting/outstanding?page&size` (finance:read): per customer contract (project budgets + standalone job budgets) − received > 0, largest first; `pageRemaining`, `lastPaymentDate`.
- Tests: `finance/FinanceAlignmentIT` (1, hand-computed figures).

## 5. Current phase & exact next steps — R9 Frontend foundation
Not started. `PRODUCT_AUDIT.md` R9 row: new `frontend/` — React + Vite + TypeScript 5.9 + Tailwind + shadcn/ui only (never hand-built components), Arabic-first RTL shell, auth, i18n (ar default, en), layout, core CRUD; Playwright ar/en + RTL/LTR snapshots, bundle budget. Portable Node 24 + pnpm at `D:Workspace	ools` (system Node 18 is too old).

## 6. Tests (actual results)
- 2026-11-19, `./mvnw test` @ `f8c850a` (R8): **124 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `c9d34e0` (R7): **123 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `17ed30a` (R6): **120 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `20cfa98` (R5): **117 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` on `feature/saas-foundation` @ `376987b`: **114 tests, 0 failures, 0 errors, 0 skipped** (Testcontainers: postgres:17.12-alpine, redis:8.2.11-alpine, adobe/s3mock:5.2.3). Docker Desktop must be running; on a stale-socket crash run `D:\Workspace\tools\fix-docker-sockets.ps1`.

## 7. Known issues / open security items
- S4 no subscription enforcement (R7). S7 rate limiting only on auth, uploads and admin login. S8 the HS256 secret would have to be shared with NestJS (evaluate RS256 in R11).
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
| `8bb659f` | docs: update handoff - R2 customers complete |
| `f410cc5` | docs: handoff - R3 in progress, stopped mid-phase |
| `7438edf` | feat(operations): work orders, visits and technician scoping |
| `eefd676` | docs: update handoff - R3 work orders complete |
| `376987b` | feat(operations): visit reports, photo purposes and thumbnails, materials, approvals |
| `271797b` | docs: update handoff - R4 field evidence complete |
| `20cfa98` | feat(notifications): in-app notifications for assignments, visits and project teams |
| `424e421` | docs: update handoff - R5 notifications complete |
| `17ed30a` | feat(operations): equipment register, warranty links and reminders, maintenance plans, SLA |
| `af1c310` | docs: update handoff - R6 maintenance and SLA complete |
| `c9d34e0` | feat(billing): subscriptions with manual payments, read-only expiry and plan limits |
| `936a3be` | docs: update handoff - R7 billing complete |
| `f8c850a` | feat(finance): project-level payments and outstanding balances |

## 11. Uncommitted / unpushed
- None. `main` untouched at `163d49c`.
