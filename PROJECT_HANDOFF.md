# PROJECT HANDOFF — Field Service & Project Operations SaaS

> Permanent project-level reference. Read this first in every new session, then `SESSION_HANDOFF.md` (same folder) for the exact continuation steps. The repository state always wins over this file; verify with `git status` / `git log` before resuming.
>
> Last updated: 2026-11-20 (**R13 complete** — roadmap R0–R13 done; last feature commit `602c22c`. Next: the deferred platform-admin UI (final stage); see `SESSION_HANDOFF.md` §7).

## 0. Active repository — read before anything else
- **The ONLY active development repository is `D:\Workspace\crm-platform`.** All development, Git operations, tests and future sessions happen here.
- `D:\Workspace\Java CRM` is the **old folder**. It is NOT a repository for this product and must never be used for development, Git or tests. It holds historical/governing documentation only.
- Authorized documents in the old folder (read-only, documentation only): `D:\Workspace\Java CRM\PRODUCT_AUDIT.md` (Phase 1–2 audit, roadmap R0–R13 in §15, decisions D1–D16 in §16), and for history only `D:\Workspace\Java CRM\PROJECT_PROGRESS.md` (pre-pivot, outdated), `VERSION_MATRIX.md`, `improved plan.md`. The old `D:\Workspace\Java CRM\SESSION_HANDOFF.md` is **superseded** by `D:\Workspace\crm-platform\SESSION_HANDOFF.md`.
- **Never read** legacy application source code: `D:\Workspace\Java CRM\crm\` and `D:\Workspace\Java CRM\analysis\` (clean-room rule; also blocked in `D:\Workspace\Java CRM\.claude\settings.json`).

## 0b. Installed Skills and Mandatory Usage Policy
Permanent rule set by the owner on 2026-11-20. **This file is the source of truth for the policy**; `SESSION_HANDOFF.md` summarizes it and must stay consistent with it.

1. **Globally installed Claude Code skills** (in `C:\Users\MediaTech\.claude\skills\`): `frontend-design`, `webapp-testing`, `vercel-react-best-practices`, `spring-data-jpa`, `flyway-migrations`, `multi-tenancy`.
2. **At the start of every new Claude Code session:** discover which skills the session actually lists; verify each relevant skill can be loaded and read (invoke it with the Skill tool); read its instructions **before** starting work in its area.
3. **Mandatory skill per task type:**

   | Task | Skill |
   |---|---|
   | Frontend UI/UX (design, layout, usability, responsive behaviour, visual consistency) | `frontend-design` |
   | React implementation and optimization (architecture, rendering, bundle, performance) | `vercel-react-best-practices` |
   | Browser and end-to-end testing of real user workflows | `webapp-testing` |
   | JPA entities, repositories, relationships, queries, persistence performance | `spring-data-jpa` |
   | Database schema changes and Flyway migrations | `flyway-migrations` |
   | Tenant isolation and tenant-aware persistence/access | `multi-tenancy` |

4. **Skills are guidance; the project wins.** This project's architecture, security requirements, approved decisions (D1–D16, ADRs incl. ADR-0015) and existing conventions (§12) always take priority over a skill's generic advice. Examples of known conflicts, resolved in favour of the project: the project uses Node/TypeScript Playwright (`frontend/e2e`, `pnpm test:e2e`), not the Python Playwright scripts `webapp-testing` suggests; entities follow the existing `TenantEntity`/constructor-injection/ArchUnit conventions rather than the skill's Lombok template; tenancy is the existing shared-schema `TenantContext`/`TenantWork` fail-closed model; Flyway migrations stay additive and sequential (next `V18`).
5. **Never claim a skill was used unless its instructions were actually discovered and read in that session.**
6. **If a relevant skill is unavailable** (not listed, fails to load), report it to the owner and continue cautiously using the documented project conventions.
7. **Do not install, update or replace skills** automatically — only with the owner's explicit permission.

Verification log: 2026-11-20 — all six skills discovered and read in-session. Note: `webapp-testing`'s helper scripts need Python Playwright, which is **not installed** (`py` = Python 3.13.7 without the `playwright` package); its method was applied with the project's existing TypeScript Playwright instead (no new dependency).

## 0c. Reuse-First Policy for Future Components
Permanent rule set by the owner on 2026-11-20. **This file is the source of truth**; `SESSION_HANDOFF.md` summarizes it and must stay consistent.

**Scope — forward only.** Applies only to NEW components and features built from **R12 onwards**. Do **not** modify, refactor, replace or reorganize existing components to apply it; do **not** revisit completed R0–R11 work; do **not** change existing functionality unless the current task explicitly requires it.

**Rules for every new frontend component (and new standard functionality: charts, tables, forms, dialogs, date pickers, PDF generation, etc.):**
1. First check whether an already-installed library provides it.
2. Prefer ready-made, free, open-source components over custom implementations.
3. Compose existing library components rather than writing from scratch.
4. Customize them to the project's design system, Arabic RTL, English LTR and responsive requirements.
5. Do not add a dependency if an installed library can do the job.
6. If nothing suitable exists, implement only the missing piece, not a whole new component.
7. Before adding any dependency, verify compatibility (React 19, Vite 8, TypeScript ~5.9, Tailwind 4), active maintenance, a permissive licence (MIT/Apache-2.0/BSD/ISC — no GPL/AGPL/commercial), accessibility (keyboard, ARIA, RTL) and bundle-size impact (the `pnpm build` budget: each chunk ≤ 200 kB gzip, total ≤ 350 kB; lazy-load heavy pieces). Record the check in the commit message or the phase notes.

**Lookup order (frontend):**
1. Components already in `frontend/src/components/ui/` (2026-11-20: alert, avatar, badge, button, card, checkbox, dialog, direction, dropdown-menu, empty, field, input, label, radio-group, select, separator, sheet, sidebar, skeleton, sonner, spinner, table, tabs, textarea, tooltip).
2. Other shadcn/ui registry components, added **only** with `pnpm dlx shadcn@latest add <name>` (existing rule, §12; `components.json` has `rtl: true`). Some pull in a vetted dependency (e.g. chart → recharts, calendar/date picker → react-day-picker, data table → @tanstack/react-table); rule 7 still applies to that dependency.
3. Primitives from installed libraries: `radix-ui`, `react-hook-form` + `zod` (forms), `@tanstack/react-query` (data), `lucide-react` (icons), `sonner` (toasts), `i18next` (text).
4. Only then a new, vetted open-source dependency (rule 7), or a minimal custom piece (rule 6).

**Backend / other standard functionality:** same principle — reuse Spring Boot / existing libraries first. Arabic PDF in R12 uses the already-approved D15 stack (OpenHTMLtoPDF + ICU4J + an embedded open-licence Arabic font); no other PDF engine without the owner's approval.

**Skills:** apply `frontend-design` (visual consistency, RTL/LTR, responsive) and `vercel-react-best-practices` (bundle/dynamic imports, re-renders) to every new component, per §0b. Project conventions override generic skill advice.

## 1. Identity & directives
- Product: Arabic-first (RTL, English secondary) multi-tenant SaaS for **Fit-Out & Contracting**, **CCTV/Security/Access Control**, **Maintenance & Field Service** — Egypt first; must serve an independent technician (one-user workspace) up to companies.
- Governing documents:
  - **Master Project Directive** (2026-11-19) and **Implementation Authorization** (2026-11-19): given in earlier chat sessions; **they are NOT saved as files anywhere** (verified 2026-11-19: no copy in either folder). Their binding content is summarized in §12 of this file. The full original wording is UNAVAILABLE to a new session — do not invent requirements beyond §12 and `PRODUCT_AUDIT.md`; ask the owner if something is not covered.
  - `D:\Workspace\Java CRM\PRODUCT_AUDIT.md` — roadmap R0–R13 and decisions D1–D16 (file).
  - ADRs in `docs/adr/` (0001–0015) and specs in `docs/specs/` (pre-pivot sanitized specs).
- **Safe stop phrase:** «وقف عند نقطة آمنة وحدّث الهاند أوف» → finish or roll back the in-flight small change, verify git, update this file and `SESSION_HANDOFF.md`, commit/push only verified work.

## 2. Repository
- Path: `D:\Workspace\crm-platform` · remote `origin` = `https://github.com/ali-kassab/crm-platform.git` (private, owned by the user).
- Working branch: **`feature/saas-foundation`** (created from `main` @ `163d49c`).
- **`main` status (verified 2026-11-20 via `git fetch`):** the owner merged **PR #3** (R0–R10, `7ae560a`) and later **PR #4** → `origin/main` = `50f9437`, which contains every commit up to `fac6486` (R11). R12 and R13 are only on the feature branch. The **local** `main` ref is stale. Claude never pushes to, merges into or updates `main`; leave the local `main` ref alone unless the owner asks.
- Author/committer: `ali-kassab <alikassab155@gmail.com>` (local git config, verified). **No Claude co-author trailer or Claude/Anthropic attribution** in commits or PR text.
- Push: verified commits → `origin feature/saas-foundation` automatically. Never `main`, never force-push, never rewrite history, never delete branches, never open a PR (opening and merging PRs is the owner's decision).

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
| R9 Frontend foundation | ✅ complete (2026-11-19, `a06072d`) |
| R10 Frontend workflows | ✅ core done (2026-11-19, `14bf98c`) — see 4k for what is left |
| R11 NestJS realtime | ✅ complete (2026-11-20, `0c4f936`) — see 4l; live end-to-end run still blocked (§7) |
| R12 Reports & Arabic PDF | ✅ complete (2026-11-20, `c27961a`) — see 4m |
| R13 Performance & production readiness (+ R10 leftovers) | ✅ complete (2026-11-20, `39c38bc` … `602c22c`) — see 4n |

Evidence: each ✅ row has a feature commit on `feature/saas-foundation` (see §10) and a recorded full test run (see §6); R0–R10 are also in `origin/main` via PR #3 (R11 and R12 are only on the feature branch).

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
- Not done (by design for now): no retention/cleanup of old notifications, no e-mail/push delivery; realtime "new notification" hints added in R11 (4l).

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

## 4j. Completed — R9 Frontend foundation — commit `a06072d`
- `frontend/` (see `frontend/README.md`): Vite 8, React 19, TypeScript ~5.9 (template pulled 7.0 — pinned to the approved 5.9), Tailwind 4, shadcn/ui (`components.json`: radix-nova, `rtl: true`; add components only via `pnpm dlx shadcn@latest add`), TanStack Query, React Router 8, i18next, react-hook-form + zod 4, IBM Plex Sans Arabic font.
- `src/i18n` (ar default, en; `<html lang dir>` synced; `localStorage` key `ui-language`), `src/lib/api.ts` (fetch wrapper: Bearer in memory, `Accept-Language`, `X-Requested-With`, one-shot refresh on 401, Problem Details → `ApiError`), `src/auth` (AuthProvider restores session via `/auth/refresh`), `src/components/layout` (AppShell with shadcn Sidebar on the right in RTL, LanguageSwitcher, SubscriptionBanner), pages: login, register (sector), home (subscription status), customers (list/search/paging/create dialog), settings. Nav entries for jobs, visits, maintenance, finance, subscription are present but disabled until R10.
- Bundle budget: `scripts/check-bundle.mjs` runs in `pnpm build` (each JS chunk ≤ 200 kB gzip, total ≤ 350 kB); now 222.7 kB total. Vendor chunks via `build.rolldownOptions.output.codeSplitting`; secondary pages lazy.
- Tests: `pnpm test:e2e` — Playwright vs `vite preview` with a mocked API (`e2e/fixtures.ts`): **14 passed, 2 skipped** (sidebar position checks on mobile) — ar/en, RTL/LTR screenshot baselines (`*-win32.png`), login errors, language switch, customers create/search, read-only banner. `pnpm lint` (oxlint): 0 errors.
- **Not verified:** a live run against the real backend. `docker compose up` works, but the backend could not connect: the existing Postgres volume was initialized with a different password than the current `.env` (`password authentication failed for user "crm"`). Fixing needs the owner (reset the dev volume or align `.env`) — not done. Containers were stopped again (`docker compose stop`).

## 4k. Completed — R10 Frontend workflows (core) — commit `14bf98c`
- Pages: `src/pages/jobs/{JobsPage, JobDetailPage, NewJobDialog, JobBadges, api.ts}`, `src/pages/visits/{VisitsPage, VisitActions, api.ts}`, `src/pages/finance/FinancePage` (`/accounting/outstanding`), `src/pages/subscription/SubscriptionPage` (usage vs limits, plan/period/method, receipt upload `POST /files?kind=IMAGE&purpose=RECEIPT`, request + history); `src/lib/format.ts`; shadcn `checkbox` added. Nav: jobs, visits, finance, subscription enabled; maintenance still disabled.
- Tests: `e2e/workflows.spec.ts` (work order end-to-end, technician visits, finance, subscription renewal) — full suite **22 passed, 2 skipped**; lint 0 errors; bundle 241.1 kB gzip.
- **Not done in R10 (carry to R13 polish):** maintenance-plan and equipment screens, platform-admin screens (plans, payment methods, request review — decision: admin UI at the final stage), calendar view, dashboard KPIs beyond the home cards, sector onboarding wizard, visit reports/photos/materials UI. Still not run against the real backend (see 4j).

## 4l. Completed — R11 NestJS realtime — commit `0c4f936`
Design: `docs/adr/0015-realtime-nestjs-redis-tickets.md`, **approved by the owner on 2026-11-20** as a deliberate deviation from `PRODUCT_AUDIT.md` D4 (Redis pub/sub instead of Streams/outbox; one-time socket tickets instead of RS256). Lost hints are accepted; the frontend recovers by refetching from the Java API. Realtime is never used for business operations or persistence.
- **Backend** — `notifications/NotificationEvents.NotificationsCreated` (published by `NotificationService.notify` for the non-empty recipient set); module `realtime/`: `RealtimeProperties` (`app.realtime.enabled` ← `REALTIME_ENABLED`, `channel` `rt:events`, `ticket-ttl` 60 s), `RealtimeRelay` (`@TransactionalEventListener`, after commit, failures only logged) relays `NotificationsCreated` (per user), `AuditRecorded` for JOB, VISIT, MATERIAL, APPROVAL, EQUIPMENT, MAINTENANCE_PLAN, and **`WorkEvents.BoardChanged`** (entity type `BOARD`, board id + change type such as `TASK_CREATED`; no titles); `RealtimeTicketController` `POST /api/v1/realtime/ticket` (authenticated workspace members only; 256-bit SecureRandom ticket → Redis `rt:ticket:<t>` = `{userId, workspaceId}`, TTL 60 s). `SubscriptionEnforcement` exempts `/api/v1/realtime/` (read-only workspaces still get hints).
- **realtime-service/** (NestJS 12, socket.io 4, ioredis, TS 5.9) — see `realtime-service/README.md`. Socket path `/realtime/socket.io`; ticket consumed with atomic `GETDEL` (format-checked before Redis is queried; claims must be UUIDs); rooms `ws:<workspaceId>` and `user:<userId>:<workspaceId>` only; every message from the channel is shape-validated and anything else dropped; `GET /health`. Env `PORT` (3001), `REDIS_URL`, `REALTIME_CHANNEL`, `ALLOWED_ORIGINS`.
- **Frontend** — `src/lib/realtime.ts` `useRealtime(userId, workspaceId)`, called from `AppShell`: fetches a ticket first and only then lazy-loads `socket.io-client` (separate `realtime` chunk, not on the critical path); every (re)connect uses a fresh ticket; ticket/socket failures retry with backoff 5 s → 5 min, the app stays on plain REST; on `notification` invalidates `['notifications']`; on `changed` invalidates (batched 250 ms) `['jobs']`, `['visits']`, `['outstanding']`, `['job', jobId]` (and `['job', entityId]` for JOB), or `['board', boardId]` for BOARD; **after every reconnect invalidates all queries** to catch up on missed hints; socket closed on sign-out / workspace change. `/realtime` ws dev proxy in `vite.config.ts`.
- R11 acceptance (from `PRODUCT_AUDIT.md`): live job/visit/notification updates ✅; board updates ✅ relayed (no board screen exists in the frontend yet — the `['board', id]` key is the convention for when it does); cross-tenant isolation ✅ (`gateway.spec.ts` room isolation incl. same user in two workspaces; `RealtimeIT` workspace id in each hint); dedup on redelivery — **not applicable** (pub/sub, approved); switch-off ✅ (`REALTIME_ENABLED=false` stops publishing; frontend works without the service — e2e runs with the ticket endpoint returning 404).
- Tests: `realtime/RealtimeIT` (3: id-only job/notification hints, rejected change publishes nothing; ticket random/TTL/claims, 401 without auth; board hint has board id + type, no title); `realtime-service` `pnpm test` (6: ticket rejection + single use, room isolation, garbage dropped, health; Redis ticket store: single use, malformed tickets never reach Redis, non-UUID claims rejected); browser tests `frontend/e2e/realtime.spec.ts` (3 × desktop + mobile, fake Socket.IO server via `page.routeWebSocket`: ticket only in the handshake auth and never in the URL, a JOB hint refetches the work-order list, a BOARD hint does not, a dropped connection reconnects with a NEW ticket and refetches the screen; no realtime service → no socket and the app works; sign-out closes the socket). Mutation-checked: disabling the `changed` handler makes the hint test fail.
- **Not done:** no live run of browser + Java + Redis + NestJS together (local DB password issue, §7); Dockerfile/docker-compose entry for `realtime-service` (R13); no frontend notifications list/badge yet (the `['notifications']` key is ready); a socket that is already open is not closed when the user is removed from the workspace elsewhere (it only receives ids; accepted, documented in the README).

## 4m. Completed — R12 Reports & Arabic PDF — commit `c27961a`
Audit R12 scope: "dashboards, visit report PDF in Arabic; real Arabic PDF verified" — done. No Flyway migration was needed (next is still `V18`).
- **Libraries (D15, owner-approved; licence accepted 2026-11-20):** `io.github.openhtmltopdf:openhtmltopdf-pdfbox` + `openhtmltopdf-rtl-support` **1.1.112** (maintained fork, **LGPL-2.1, used unmodified** as a Maven dependency), bringing PDFBox 3.0.8 (Apache-2.0) and ICU4J 77.1 (Unicode licence). Font: **IBM Plex Sans Arabic** Regular/Bold (OFL-1.1, Arabic + Latin, same typeface as the UI) in `backend/src/main/resources/fonts/` with its licence `IBMPlexSansArabic-OFL.txt`. Source: official npm package `@ibm/plex-sans-arabic` 1.1.0 (owner-approved download); that package has only WOFF/WOFF2, so the WOFF 1.0 files were decompressed to TTF (byte-identical font tables — an unmodified copy under the OFL). Thymeleaf (already a dependency) renders the HTML.
- **Visit report PDF** — `GET /api/v1/reports/visits/{visitId}/pdf?lang=ar|en` (`ops:read` or `ops:assigned`): `reporting/application/{PdfRenderer, VisitReportPdf}`, `reporting/web/ReportsController`, template `templates/reports/visit-report.html`, labels `report.visit.*`, `jobType.*`, `priority.*`, `visitStatus.*`, `outcome.*`, `purpose.*` in `i18n/messages(_ar).properties`. Contents: workspace, customer, phone, site/address, work order, type, priority, scheduled/actual time, status/outcome, technicians, reported issue, report sections (only those filled), materials (name/quantity/unit — **no costs**), up to 12 photos ordered BEFORE/DURING/AFTER/INSPECTION (re-encoded to JPEG ≤1000 px), customer confirmation + signature, page numbers. Language: `lang` param → request language → workspace default. Dates in the workspace time zone with the UI's `ar-EG` digits.
  - Arabic: ICU bidi splitter/reorderer + Arabic shaping + ICU line breaker; layout uses direction-aware floats because the engine does **not** mirror table columns in RTL; user-entered text gets its own direction from its first strong character (`VisitReportPdf.Directions`, like `dir="auto"`), so Arabic with numbers stays correct inside an English PDF and vice versa.
  - Security: data from the new operations API `VisitReports` (`document`, `image`) implemented by `operations/visits/application/VisitReportDocuments` through `VisitService.get` → same access rule as the visit (technicians only their visits, 404 otherwise; tenant filter). Images via new `Files.contentOfOwned(fileId, ownerType, ownerId)` (owner + tenant checked; `FileStorage.get`). The renderer may load **only `data:` URIs** (no server-side fetching). All user text escaped by Thymeleaf. Internal notes and material costs never printed. Rate limit 30 PDFs/user/minute (`RateLimiter`), `Cache-Control: no-store`, `Content-Disposition: attachment; filename="visit-report-<date>.pdf"`.
- **Dashboard** — `GET /api/v1/reports/dashboard?period=WEEK|MONTH|QUARTER|YEAR|ALL` (default MONTH; **`reports:read`** = Owner/Admin/Finance, as in the API spec): open jobs (+ by status), SLA-breached open jobs, jobs created/completed in period, visits today/overdue/completed/missed, overdue maintenance, warranties ending within 30 days; with `finance:read` also received/paid in period and total outstanding. Periods are the workspace's calendar days (its time zone). New public APIs: `operations.OperationsOverview` (`operations/overview/application/OperationsOverviewService`, JPQL aggregates only → Hibernate tenant filter), `finance.Accounting.periodSummary` (`TransactionRepository.sumInPeriod`; `OutstandingBalances.total()` native SQL with explicit workspace filter), `identity.WorkspaceMembers.currentWorkspace()`.
- **Frontend** — `src/pages/dashboard/{DashboardPanel, api}` lazy-loaded on the home page only with `reports:read` (shadcn Tabs/Card/Skeleton/Alert, no new dependency; alert counts highlighted only when > 0; tiles link to jobs/visits/finance); `src/pages/visits/VisitReportButton` next to visit actions in the job detail and the technician's visits page; `lib/api.ts` `download()` (Bearer download, server file name; `send()` now keeps an explicit `Accept`); `lib/format.ts` `formatNumber`; i18n `dashboard.*`, `visits.downloadReport` (ar/en). Bundle 256.8 kB gzip (dashboard chunk 1.4 kB, lazy).
- Tests: `reporting/ReportsIT` (2: real Arabic PDF — embedded Plex font, ≥ 3 images, Arabic text in logical order after NFKC, no internal note/cost, English variant; 404 for an unassigned technician and another workspace, 401 without session; dashboard counts only the caller's workspace, money only with finance:read, 403 for technician/member, invalid period 400), `reporting/application/VisitReportPdfTest` (2: direction rule, image bounding/broken image skipped), `frontend/e2e/reports.spec.ts` (4 × desktop/mobile). Rendered pages were inspected visually (`backend/target/reports-it/*.png`, generated by `ReportsIT`, not committed).
- **Not done / limits:** no live run against the real backend (local DB password, §7); charts were not added (KPI tiles only — a chart would need `recharts` via the shadcn chart component, decide in a later phase under the reuse-first policy); no visit-report editing UI or photo upload UI (R10 leftovers, R13); PDF rendering is synchronous on the request thread (bounded by 12 photos and the rate limit).

## 4n. Completed — R13 Performance, production readiness and R10 leftovers — commits `39c38bc` … `602c22c`
Audit R13 scope: "baselines, load test, Dockerfiles, CI, backups, deployment config; measured p95 targets; clean-env setup". No Flyway migration (next is still `V18`).
- **Ops (configuration only, nothing deployed):** `39c38bc` API sort fix (`?sort=field,desc` on all list endpoints, `shared/web/Pagination`), OTLP metrics push off unless `OTLP_METRICS_ENABLED=true`, test JVM pinned to UTC in `backend/pom.xml`. `1fab3fa` Dockerfiles (backend Temurin 17 JRE non-root, realtime-service, frontend nginx with `/api` + `/realtime` proxy, CSP and security headers), compose `--profile app`, `docker-compose.prod.yml`, `.github/workflows/ci.yml`, Linux screenshot baselines, `ops/backup/{backup,restore}.sh` (restore only into a NEW db / empty volume), `frontend/e2e-live` + `pnpm test:live`, `ops/perf/baseline.js` (k6), runbook `docs/operations/`.
- **Measured:** k6 20 VUs / 60 s → 22,491 requests, 0 errors, every p95 ≤ 105 ms (`docs/operations/performance-baseline.md`); backup → restore round trip with identical row counts.
- **R10 leftovers (frontend, shadcn only, no new npm dependency):** customer page with sites (`94b7ca0`), notifications bell + `/notifications` (`edcce25`), visit record sheet (`9b2e97d`), `/maintenance` + equipment section (`6bfa41c`), customer approvals card (`9260925`), `/visits/calendar` week agenda (`1300a8c`), **sector onboarding wizard** `/setup` (`eb84b4b`: sector → name/default language/currency/time zone via `PATCH /workspace` → sector-specific first-steps checklist; new owners land there after sign-up; home prompt while no sector; Settings link; the sector suggests the new work-order type; shadcn `radio-group` added), **device card + devices on the work order** (`602c22c`: sheet with details, status, service history and warranties from `GET /equipment/{id}`; work-order "Devices" card linking site devices via `PUT /jobs/{id}/equipment`).
- **Frontend conventions added:** `AuthState.updateWorkspace(partial)` merges saved workspace settings into the in-memory session; `GuestOnly` sends a just-registered user to `/setup` (its redirect wins over the page's own `navigate`); sector defaults live in `src/pages/setup/sectors.ts` (suggestions only, never feature switches).
- **Tests:** backend 134/0/0/0; realtime-service 6/6; frontend lint 0 errors, build 290.3 kB gzip, e2e 70 passed / 2 skipped, `pnpm test:live` 1 passed on the throwaway `crm-r13-smoke` stack (register → setup wizard → customer → site → device → maintenance plan → work order → device link + service history → approval → visit → report + photo → materials → complete → Arabic PDF, realtime socket connected).
- **Not done / limits:** CI never ran on GitHub (triggers: PR, push to main, manual); no deployment; sector seeding of tags/board or milestone templates was not added (no screen uses them; the wizard covers sector choice, regional defaults, first steps and the work-order type suggestion); no signature capture; `JobDetailPage` has no error boundary; platform-admin UI deferred to the final stage.

## 5. Current phase & next steps
Roadmap R0–R13 is complete (2026-11-20). The remaining approved item is the **platform-admin UI** (plans, payment methods, subscription-request review, workspaces) that the owner deferred to the final stage; its backend APIs exist (4h). Exact next steps: `SESSION_HANDOFF.md` §7.

**Exact next steps for a new session (in order):**
1. Verify state (`git status` clean apart from ignored files, `git log -1`, branch = `feature/saas-foundation`, in sync with origin).
2. Follow §0b (skills) and §0c (reuse-first).
3. Platform-admin UI (final stage) per `SESSION_HANDOFF.md` §7; after it: tests → commit → push → update both handoff files.

## 6. Tests (actual results)
- 2026-11-20, R13 close-out at `602c22c`: backend `./mvnw test` **134 tests, 0 failures, 0 errors, 0 skipped**; `realtime-service` `pnpm test` **6/6**; frontend `pnpm lint` 0 errors (pre-existing warnings only), `pnpm build` OK **290.3 kB gzip**, `pnpm test:e2e` **70 passed, 2 skipped**, `pnpm test:live` **1 passed** against the throwaway `crm-r13-smoke` stack.
- 2026-11-20, R12 final tree (committed as `c27961a`): backend `./mvnw test` with `-DargLine="-Djdk.net.unixdomain.tmpdir=target -Duser.timezone=UTC"` → **131 tests, 0 failures, 0 errors, 0 skipped** (incl. `ReportsIT` 2/2, `VisitReportPdfTest` 2/2, `ModularityTests`, `LayeringRulesTest`, `TenantIsolationIT`, `ErrorTextsCoverageTest`); `realtime-service` `pnpm test` **6/6**; frontend `pnpm lint` 0 errors (pre-existing warnings only), `pnpm build` OK (**256.8 kB gzip**, limit 350), `pnpm test:e2e` **36 passed, 2 skipped** (incl. 8 in `e2e/reports.spec.ts`; home screenshot baselines unchanged).
- 2026-11-20, R11 browser tests added on top of `fac6486`: frontend `pnpm lint` 0 errors, `pnpm build` OK (254.3 kB gzip), `pnpm test:e2e` **28 passed, 2 skipped** (22 earlier + 6 in `e2e/realtime.spec.ts`); `realtime-service` `pnpm test` **6/6**; backend (`-Duser.timezone=UTC`) `RealtimeIT` 3/3, `TenantIsolationIT` 4/4, `ModularityTests` 2/2, `LayeringRulesTest` 5/5 — all green (full suite not re-run: no backend change since the 127-test run). Committed as `40e5da3`. No backend or realtime-service source changed.
- 2026-11-20, R11 final tree (committed as `0c4f936`): `./mvnw test` **127 tests, 0 failures, 0 errors, 0 skipped with the JVM on UTC (`-DargLine="-Djdk.net.unixdomain.tmpdir=target -Duser.timezone=UTC"`); the same run in the default zone right after local midnight had 2 date-boundary failures (`BillingIT.trialThenManualPaymentApprovedByThePlatformAdmin`, `OperationsIT.expiryFlipsOnlyActiveWarrantiesPastTheirEnd`) unrelated to R11 — see §7**; `RealtimeIT` alone **3/3 passed**; `realtime-service` `pnpm test` **6/6 passed**; frontend `pnpm lint` 0 errors (pre-existing warnings only), `pnpm build` OK (**254.3 kB gzip** total, limit 350; `realtime` chunk 12.5 kB, lazy, not preloaded), `pnpm test:e2e` **22 passed, 2 skipped**.
- 2026-11-19 (audit pass), R11 uncommitted tree on top of `dc393ca`: `./mvnw test` **126 tests, 0 failures, 0 errors, 0 skipped**; `realtime-service` `pnpm test` **3/3 passed**; frontend `pnpm lint` 0 errors, `pnpm build` OK (241.1 kB gzip), `pnpm test:e2e` **22 passed, 2 skipped**.
- 2026-11-19, R11 (uncommitted tree, earlier): `RealtimeIT` alone **2/2 passed**; `realtime-service` `pnpm test` **3/3 passed**.
- 2026-11-19, frontend @ `14bf98c`: `pnpm test:e2e` **22 passed, 2 skipped**, `pnpm lint` 0 errors, `pnpm build` OK (241.1 kB gzip).
- 2026-11-19, frontend @ `a06072d`: `pnpm build` OK (budget 222.7 kB gzip), `pnpm lint` 0 errors, `pnpm test:e2e` 14 passed / 2 skipped. Backend unchanged since `f8c850a` (124 tests green).
- 2026-11-19, `./mvnw test` @ `f8c850a` (R8): **124 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `c9d34e0` (R7): **123 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `17ed30a` (R6): **120 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` @ `20cfa98` (R5): **117 tests, 0 failures, 0 errors, 0 skipped**.
- 2026-11-19, `./mvnw test` on `feature/saas-foundation` @ `376987b`: **114 tests, 0 failures, 0 errors, 0 skipped** (Testcontainers: postgres:17.12-alpine, redis:8.2.11-alpine, adobe/s3mock:5.2.3). Docker Desktop must be running; on a stale-socket crash run `D:\Workspace\tools\fix-docker-sockets.ps1`.

## 7. Known issues / open security items
- ~~S4 no subscription enforcement~~ — resolved in R7 (read-only mode + plan limits).
- S7 rate limiting only on auth, uploads, admin login and subscription requests.
- ~~S8 (HS256 secret sharing with NestJS)~~ — resolved in R11 (ADR-0015, approved 2026-11-20): one-time Redis tickets; NestJS never sees JWTs or keys.
- Realtime hints are best-effort (Redis pub/sub): lost during disconnects by design; the frontend refetches after reconnect. Never use them for business logic.
- Realtime has never run live (browser + Java + Redis + NestJS); only integration tests on each side.
- **Never run against the real backend end to end.** Local Postgres volume was initialized with a different password than the current `.env` (`password authentication failed for user "crm"`). Fix requires the owner: reset the local dev DB volume (destroys local dev data) or align `.env`. Not done.
- Platform admin sessions are access-token only (10 min default); re-login on expiry is by design.
- `PlatformAdminIT` needs a fresh database (it skips otherwise).
- Scheduler dates (maintenance, warranties, subscriptions) use the server clock (UTC), not the workspace time zone. **Test flake from this:** some ITs compute dates with `LocalDate.now()` in the JVM zone (Cairo, +02:00) while the server uses UTC, so between 00:00 and 02:00 local time `BillingIT` and `OperationsIT` fail (seen 2026-11-20 00:11; both pass with `-Duser.timezone=UTC`). **Fixed in R13 (`39c38bc`):** the test JVM is pinned to UTC in the `backend/pom.xml` `argLine`, so plain `./mvnw test` is enough now.
- No notification retention/cleanup; no e-mail delivery for billing/maintenance notices (in-app only).
- Playwright screenshot baselines are Windows-only (`*-win32.png`); Linux CI needs its own baselines.
- Seeded plan prices are placeholders (§4h).

## 8. Decisions
- Approved (2026-11-19): D1 visits as a separate entity; D2 standalone jobs; D3 defer the automation engine; D4 NestJS realtime; D5 7-day grace → read-only, never delete; D6 14-day trial; D7 configurable limits; D8 TECHNICIAN + FINANCE roles; D9 hide flowcharts; D10 customer kind + optional email; D11 ar-EG / EGP / Africa/Cairo defaults; D12 materials as job cost lines; D13 branch `feature/saas-foundation`; D14 staff-recorded approvals; D15 Arabic PDF libraries (OpenHTMLtoPDF + ICU4J + embedded Arabic font) when R12 starts; D16 roadmap order. Commercial values are provisional and configurable. The user is the first Platform Admin; the platform-admin dashboard UI is deferred to the final stage.
- **Approved 2026-11-20 — D4 realtime deviation (ADR-0015):** keep Redis pub/sub (no Redis Streams, no outbox), keep one-time WebSocket connection tickets, Java stays the source of truth, no RS256 migration solely for realtime; lost events are accepted and the frontend recovers by refetching authoritative state after reconnect/refresh; realtime is never relied on for critical business operations or persistence. "Dedup on redelivery" from the audit's R11 acceptance therefore does not apply. Use the simplest, lowest-cost implementation.
- **Approved 2026-11-20 — R12 PDF licences and font:** OpenHTMLtoPDF (LGPL-2.1, unmodified dependency) accepted as D15 specified; IBM Plex Sans Arabic (OFL-1.1) downloaded from the official `@ibm/plex-sans-arabic` npm package and committed as TTF.
- **Waiting for the owner's approval:**
  1. Real plan prices (seeded SOLO/TEAM/BUSINESS values are placeholders).
  2. Local dev DB: reset the Postgres volume or align `.env` (needed for any live end-to-end run).

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
| `4111550` | docs: update handoff - R8 finance alignment complete |
| `73e8cc6` | docs: fix tools path in handoff |
| `a06072d` | feat(frontend): Arabic-first RTL shell, auth, i18n and customers screen |
| `28bdca3` | docs: update handoff - R9 frontend foundation complete |
| `14bf98c` | feat(frontend): work orders, visits, finance and subscription screens |
| `dc393ca` | docs: update handoff - R10 core screens complete |
| `7ae560a` | (on `origin/main` only) Merge pull request #3 — merged by the owner on GitHub |
| `0c4f936` | feat(realtime): live hints via NestJS, Redis pub/sub and one-time socket tickets |
| `fac6486` | docs: update handoff - R11 realtime complete |
| `40e5da3` | test(frontend): browser tests for the realtime hook against a fake Socket.IO server |
| `c2ce290` | docs: skills usage policy and R11 browser test results in handoffs |
| `e1ad62b` | docs: reuse-first policy for future components |
| `c27961a` | feat(reporting): Arabic visit report PDF and workspace dashboard |
| (next) | docs: update handoff - R12 reports and Arabic PDF complete |
| `39c38bc` … `1300a8c` | R13 ops/infra and R10 leftovers (see 4n; docs commits `ef25092`, `b385fdc`, `c14091c` in between) |
| `eb84b4b` | feat(frontend): sector onboarding wizard for new workspaces |
| `602c22c` | feat(frontend): device card with service history, devices on the work order |
| (next) | docs: update handoff - R13 complete (**HEAD**) |

## 11. Working tree (2026-11-20)
- Branch `feature/saas-foundation`; after the R13 docs commit everything is committed and pushed to `origin/feature/saas-foundation` (verify with `git status -sb`). `origin/main` = `50f9437` (PR #4; contains up to `fac6486` = R11, not R12/R13); local `main` is stale — leave it alone.
- Ignored, not committed: `node_modules/`, `dist/`, `backend/target/`, Playwright reports.
- Local Docker: the owner's `crm-platform` dev containers are stopped (Postgres volume password mismatch with `.env`, §7). The throwaway `crm-r13-smoke` stack (own volumes) is used for live runs — see `SESSION_HANDOFF.md` §6c.

## 12. Permanent constraints (summary of the Master Directive, Implementation Authorization and approved decisions)
These must not be violated. The original directive texts are not available as files (§1); this list and `PRODUCT_AUDIT.md` are the reference.

**Product**
- Arabic-first (RTL default, English secondary), Egypt first; only the three sectors (fit-out/contracting, CCTV/security/access control, maintenance/field service); no generic-CRM features; flowcharts hidden (D9), automation engine deferred (D3).
- Works for a one-person workspace up to companies; technicians see only their assigned work (enforced server-side, 404 for others).
- SaaS with plans managed by the Platform Admin and a **manual payment workflow** (Vodafone Cash / InstaPay receipts approved by the admin) — **no payment gateway**. Expiry restricts writing but **never deletes data** (D5). Commercial values (prices, trial 14 d, grace 7 d, limits) are provisional and must stay configurable.
- No paid SMS. Java Spring Boot stays the core and the source of truth; realtime lives in a separate NestJS service.
- Destructive removals of features or data need explicit owner approval.

**Architecture & security**
- Fail-closed tenancy (`TenantContext.NONE`); listeners use `@TransactionalEventListener` + `TenantWork`; never `@ApplicationModuleListener`; no `@Transactional` on event-listener/scheduled methods; constructor injection only (ArchUnit bans `@Autowired`); Spring Modulith boundaries (`ModularityTests`) must stay green.
- Platform-level tables (audit, billing) filter the workspace explicitly in every tenant query; bulk updates repeat the workspace filter.
- Every new `ApiException` message needs Arabic text in `backend/src/main/resources/i18n/error-texts_ar.tsv`, every new code an `error.<CODE>` and every new not-found resource a `resource.<Name>` in `messages_ar.properties` (`ErrorTextsCoverageTest`).
- Searches compare `app_search_norm(column)` with `Pagination.likePattern(q)`.
- Flyway migrations are additive and never edited after commit; next migration is `V18`.
- Frontend: shadcn/ui components only (added with `pnpm dlx shadcn@latest add <name>`), never hand-built; TypeScript 5.9; bundle budget enforced by `pnpm build`.
- No `terraform apply`, no deployment.

**Git & process**
- Work only on `feature/saas-foundation`; commit focused, verified units (tests green) authored as `ali-kassab <alikassab155@gmail.com>`; no Claude co-author/attribution; push verified commits to `origin feature/saas-foundation` automatically.
- Never push/merge to `main`, never force-push, never rewrite history, never delete branches, never create a PR, never change repo visibility.
- Continuous execution (owner, 2026-11-19): run the remaining phases back to back without asking, **except** stop for a critical security issue, a destructive/irreversible operation, a product/business decision not covered by the directive or `PRODUCT_AUDIT.md`, a requirement conflict (as the D4 deviation was until approved on 2026-11-20), or a real blocker.
- After every phase: tests → commit → push → update `PROJECT_HANDOFF.md` and `SESSION_HANDOFF.md`.

**Operations that always need the owner's explicit approval**
- Resetting/deleting any database or Docker volume; deleting data or features.
- Anything on `main`, PRs, repo settings, force pushes, history rewrites.
- Changing approved decisions D1–D16 (D4 as amended by ADR-0015 on 2026-11-20) or setting real prices.
- Deploying or applying infrastructure.
