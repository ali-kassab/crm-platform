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
| R0 Security & foundations | 🟡 in progress (tenant isolation ✅) |
| R1–R13 | ⚪ not started |
| Frontend, NestJS realtime, subscriptions, i18n | ⚪ not started |

## 4. Completed this phase (R0)
1. **Tenant isolation is fail-closed** (critical audit finding S1).
   - `shared/tenant/TenantContext.NONE` — default tenant when no context; matches no row.
   - `shared/tenant/TenantIdentifierResolver` — returns `NONE` instead of root `SYSTEM` when no context. Only explicit `runAs(SYSTEM, …)` is root.
   - `shared/persistence/TenantEntity.requireWorkspaceScope()` (`@PrePersist`) — rejects inserts with no context, `NONE` or `SYSTEM`.
   - `shared/tenant/TenantWork` — sets the workspace **then** opens a `REQUIRES_NEW` transaction (Hibernate fixes the tenant at session open). `callAsSystem` for platform schedulers.
   - Listeners moved off `@ApplicationModuleListener` (which opened the tx before the tenant was set → ran as root on startup republish): `crm/contacts/application/CrmEventHandlers`, `work/projects/application/WorkIntegration.Handlers`, `operations/jobs/application/OperationsIntegration.Handlers` now use `@TransactionalEventListener` + `TenantWork`. Schedulers `OrphanFileCleanup`, `WarrantyExpiry` use `TenantWork.callAsSystem`.
   - ArchUnit guards in `architecture/LayeringRulesTest`: no `@ApplicationModuleListener`; no `@Transactional` on `@TransactionalEventListener`/`@Scheduled` methods.
   - Tests: `security/TenantIsolationIT` (4): no-context query → 0 rows; no-context/SYSTEM insert rejected; deactivating a member in workspace B leaves workspace A untouched; full cross-workspace GET/PATCH/DELETE/void/move matrix → 404, lists empty, foreign references rejected.

## 5. Current phase & exact progress
R0 — next items, in order:
1. Persist audit events (`AuditRecorded` → `audit_entries`, Flyway `V9`), read API for admins.
2. Secure platform-admin bootstrap (env-configured email + password, startup runner, only when no platform admin exists; hashed; audited) + secure email/password change.
3. HTTP compression; fix `ProjectService.search` over-budget in-memory scan, `ProjectService.summary` N+1, comment counting.

## 6. Tests (actual results)
- 2026-11-19, `./mvnw test` on `feature/saas-foundation`: **88 tests, 0 failures, 0 errors** (Testcontainers: postgres:17.12-alpine, redis:8.2.11-alpine, adobe/s3mock:5.2.3). Docker Desktop must be running; on stale-socket crash run `D:\Workspace\tools\fix-docker-sockets.ps1`.

## 7. Known issues / open security items
- S2 no platform-admin bootstrap (next). S3 audit not persisted (next). S4 no subscription enforcement (R7). S5 no technician scoping (R3). S6 any member can read any workspace file by id (review in R4). S7 rate limiting only on auth/upload. S8 HS256 secret would need sharing with NestJS (evaluate in R11).

## 8. Decisions
- Approved (2026-11-19): D1 visits as separate entity; D2 standalone jobs; D3 defer automation engine; D4 NestJS realtime (evaluate STOMP/Redis Streams/RS256 before changing); D5 7-day grace → read-only, never delete; D6 14-day trial; D7 configurable limits; D8 TECHNICIAN + FINANCE roles; D9 hide flowcharts; D10 customer kind + optional email; D11 ar-EG / EGP / Africa/Cairo defaults; D12 materials as job cost lines; D13 branch `feature/saas-foundation`; D14 staff-recorded approvals; D15 Arabic PDF libs later; D16 roadmap order. Commercial values are provisional and must stay configurable. The user is the first Platform Admin; dashboard UI deferred to the final stage.
- Waiting for approval: none.

## 9. Technical decisions
- Fail-closed via a non-matching sentinel tenant rather than throwing in the resolver: Hibernate resolves a tenant for every session, including public endpoints that only touch global identity tables.
- Listeners run after commit on the publishing thread (no `@EnableAsync`); `TenantWork` makes the workspace explicit regardless of thread state.

## 10. Session log
| Commit | Message | Pushed |
|---|---|---|
| _(see git log)_ | | |

## 11. Uncommitted / unpushed
- None at the time of writing (update at every stop).
