# SESSION HANDOFF — continuation guide for a NEW Claude Code session

_Updated 2026-11-20 (safe shutdown): **R13 IN PROGRESS**. Part 1 (ops/infra) and part 2 (frontend leftovers: sites, notifications, visit record, maintenance + equipment, approvals, week calendar) are done, verified and pushed up to `1300a8c` (+ the final docs commit). Remaining R13 work is in §7. The owner authorized continuous execution on 2026-11-20, then asked for a safe stop here._

## 1. Active repository (read first)
- **Active repository: `D:\Workspace\crm-platform`** — the ONLY place for development, Git operations and tests.
- ⚠️ **`D:\Workspace\Java CRM` is the OLD folder (not a git repository).** Never develop, commit, run tests or run Git there. Only the documentation files listed in §3 may be read from it. **Never read** `D:\Workspace\Java CRM\crm\` or `D:\Workspace\Java CRM\analysis\` (legacy application source — clean-room rule).
- The old `D:\Workspace\Java CRM\SESSION_HANDOFF.md` is superseded by this file.

## 2. Reading order
1. `D:\Workspace\crm-platform\PROJECT_HANDOFF.md` — permanent project reference (status per phase, files, APIs, decisions, constraints §12). **Read it fully first.**
2. This file — exact continuation steps.
3. Only when needed: the documents in §3.
4. **Then follow the skills policy (§2b) and the reuse-first policy (§2c) below before any implementation.**

## 2b. Installed Skills and Mandatory Usage Policy (summary — full rule: `PROJECT_HANDOFF.md` §0b)
Every new session MUST follow this; `PROJECT_HANDOFF.md` §0b is the source of truth and the two files must stay consistent.
- Installed globally: `frontend-design`, `webapp-testing`, `vercel-react-best-practices`, `spring-data-jpa`, `flyway-migrations`, `multi-tenancy`.
- At session start: discover the skills this session lists, load each relevant one (Skill tool) to confirm it can be read, and read its instructions before related work.
- Use per task: frontend UI/UX → `frontend-design`; React implementation/optimization → `vercel-react-best-practices`; browser/e2e testing → `webapp-testing`; JPA entities/repositories/queries → `spring-data-jpa`; schema changes/migrations → `flyway-migrations`; tenant isolation/tenant-aware persistence → `multi-tenancy`.
- Project architecture, security, approved decisions and conventions always override a skill's generic advice (e.g. TypeScript Playwright, not the skill's Python scripts; existing `TenantContext` model; additive Flyway, next `V18`).
- Never claim a skill was used unless it was actually discovered and read. If one is unavailable, report it and proceed cautiously with project conventions. Never install, update or replace skills without the owner's permission.
- Last verified 2026-11-20: all six readable; Python Playwright (for `webapp-testing` helper scripts) is not installed.

## 2c. Reuse-First Policy for Future Components (summary — full rule: `PROJECT_HANDOFF.md` §0c)
Every new session MUST follow this for new work; `PROJECT_HANDOFF.md` §0c is the source of truth.
- **Forward only, from R12:** never modify, refactor, replace or reorganize existing components or completed R0–R11 work to apply it; change existing functionality only when the task requires it.
- For every NEW component or standard feature (charts, tables, forms, dialogs, date pickers, PDF, …): reuse what is installed first → then a shadcn/ui component via `pnpm dlx shadcn@latest add <name>` → then primitives from installed libraries (`radix-ui`, `react-hook-form` + `zod`, TanStack Query, `lucide-react`, `sonner`) → only then a vetted new open-source dependency, or a minimal custom piece for just the missing functionality.
- Customize to the design system, Arabic RTL / English LTR and responsive layouts.
- Before any new dependency: check compatibility (React 19, Vite 8, TS ~5.9, Tailwind 4), maintenance, permissive licence (no GPL/AGPL/commercial), accessibility incl. RTL, and bundle impact (`pnpm build` budget; lazy-load heavy parts).
- R12 PDFs use the approved D15 stack (OpenHTMLtoPDF + ICU4J + embedded Arabic font).
- Apply `frontend-design` and `vercel-react-best-practices` to new components (§2b).

## 3. Governing documents and authorized paths
| Document | Path | Status |
|---|---|---|
| Permanent project handoff | `D:\Workspace\crm-platform\PROJECT_HANDOFF.md` | file, current |
| Product audit: roadmap R0–R13 (§15), decisions D1–D16 (§16) | `D:\Workspace\Java CRM\PRODUCT_AUDIT.md` | file, read-only |
| Master Project Directive (2026-11-19) | — | **not saved as a file anywhere**; summarized in `PROJECT_HANDOFF.md` §1 and §12. Original wording UNAVAILABLE. |
| Implementation Authorization (2026-11-19) | — | **not saved as a file anywhere**; summarized in `PROJECT_HANDOFF.md` §2 and §12. Original wording UNAVAILABLE. |
| Architecture decisions | `D:\Workspace\crm-platform\docs\adr\` (0001–0015) | files |
| Realtime design (D4 as amended, approved 2026-11-20) | `docs/adr/0015-realtime-nestjs-redis-tickets.md`, `realtime-service/README.md` | files |
| Pre-pivot specs | `D:\Workspace\crm-platform\docs\specs\` | files (older, pre-pivot) |
| History only (outdated) | `D:\Workspace\Java CRM\PROJECT_PROGRESS.md`, `VERSION_MATRIX.md`, `improved plan.md` | files, read-only |
| Frontend guide | `D:\Workspace\crm-platform\frontend\README.md` | file |

If a requirement is not covered by these, ask the owner instead of guessing.

## 4. Git state (2026-11-20, safe shutdown)
- Branch: **`feature/saas-foundation`**, in sync with `origin/feature/saas-foundation` after the final docs commit (verify with `git status -sb`). R13 commits this session: `39c38bc`, `1fab3fa`, `ef25092` (docs), `94b7ca0`, `edcce25`, `9b2e97d`, `b385fdc` (docs), `6bfa41c`, `9260925`, `1300a8c`, then the final handoff docs commit (HEAD).
- Remote: `origin` = `https://github.com/ali-kassab/crm-platform.git`.
- `origin/main` = **`50f9437`** (the owner merged **PR #4**, containing everything up to `fac6486` = R11). R12 and R13 are only on the feature branch. Local `main` is stale. **Do not touch `main` in any way**; PRs are the owner's decision.
- Git identity (local config): `ali-kassab <alikassab155@gmail.com>`; no Claude co-author/attribution lines.
- Working tree: clean apart from git-ignored files (`node_modules/`, `dist/`, `backend/target/`, `frontend/test-results/`).

## 5. Completed roadmap phases and evidence
| Phase | Feature commit | Last verified result |
|---|---|---|
| R0 Security & foundations | `17f1fff`, `bc33f2a`, `365a68f`, `b4f2299` | backend suite green |
| R1 Localization & settings | `2f8fab6` | green |
| R2 Customers | `88d9473` | green |
| R3 Work orders, visits, roles | `7438edf` | green |
| R4 Field evidence | `376987b` | 114 tests, 0 failures |
| R5 In-app notifications | `20cfa98` | 117 tests, 0 failures |
| R6 Equipment, warranties, maintenance, SLA | `17ed30a` | 120 tests, 0 failures |
| R7 Billing & platform-admin APIs | `c9d34e0` | 123 tests, 0 failures |
| R8 Finance alignment | `f8c850a` | 124 tests, 0 failures |
| R9 Frontend foundation | `a06072d` | build/lint OK, e2e 14 passed / 2 skipped |
| R10 Frontend workflows (core) | `14bf98c` | e2e 22 passed / 2 skipped |
| R11 Realtime (NestJS + Redis pub/sub + one-time tickets) | `0c4f936` | see below |
| R12 Reports & Arabic PDF | `c27961a` | 131 backend tests, e2e 36 passed / 2 skipped |
| R13 Performance & readiness | `39c38bc` … `1300a8c` | **in progress** (see §6c): backend 134 tests, e2e 60 passed / 2 skipped, live flow passed |

**Tests actually run for R12 (2026-11-20, committed as `c27961a`):**
- `backend`: `./mvnw test` with the JVM on UTC → **131 tests, 0 failures, 0 errors, 0 skipped** (incl. `ReportsIT` 2/2 — real Arabic PDF verified: embedded Plex font, photos + signature embedded, Arabic text in logical order, no internal notes/costs, access 404/401 rules, dashboard tenant isolation and permissions; `VisitReportPdfTest` 2/2).
- `realtime-service`: `pnpm test` → **6/6**.
- `frontend`: `pnpm lint` 0 errors; `pnpm build` OK **256.8 kB gzip**; `pnpm test:e2e` → **36 passed, 2 skipped** (incl. `e2e/reports.spec.ts` 4 scenarios × desktop/mobile).
- Rendered PDF pages were checked visually (Arabic RTL and English) from `backend/target/reports-it/*.png`.

**Tests actually run for R11 (2026-11-20, on the committed tree):**
- `backend`: `./mvnw test` → **127 tests, 0 failures, 0 errors, 0 skipped with the JVM on UTC (`-DargLine="-Djdk.net.unixdomain.tmpdir=target -Duser.timezone=UTC"`); the same run in the default zone right after local midnight had 2 date-boundary failures (`BillingIT.trialThenManualPaymentApprovedByThePlatformAdmin`, `OperationsIT.expiryFlipsOnlyActiveWarrantiesPastTheirEnd`) unrelated to R11 — see §7** (includes `RealtimeIT` 3/3, `ModularityTests`, `LayeringRulesTest`).
- `realtime-service`: `pnpm test` → **6 tests, 6 pass, 0 fail**.
- `frontend`: `pnpm lint` → 0 errors (pre-existing warnings only); `pnpm build` → OK, **254.3 kB gzip** (limit 350; lazy `realtime` chunk 12.5 kB); `pnpm test:e2e` → **28 passed, 2 skipped** (mocked API; includes `e2e/realtime.spec.ts` — 3 realtime scenarios × desktop/mobile against a fake Socket.IO server: handshake ticket never in the URL, hint → refetch, reconnect → fresh ticket + refetch, no service → no socket, sign-out closes the socket).
- UNVERIFIED: any live end-to-end run (browser + backend + Redis + realtime-service) — never done (§8 item 2).

## 6. What R11 delivered (details in `PROJECT_HANDOFF.md` 4l)
- Java publishes id-only hints to Redis pub/sub `rt:events` after commit: notifications (per user), JOB/VISIT/MATERIAL/APPROVAL/EQUIPMENT/MAINTENANCE_PLAN changes and task-board changes (`BOARD`, from `WorkEvents.BoardChanged`).
- `POST /api/v1/realtime/ticket` → one-time 256-bit ticket in Redis (60 s); NestJS consumes it with `GETDEL`; rooms `ws:<workspaceId>`, `user:<userId>:<workspaceId>`.
- Frontend `src/lib/realtime.ts` (`useRealtime` in `AppShell`): ticket first, lazy socket.io, fresh ticket per reconnect, backoff 5 s → 5 min, invalidates TanStack queries (batched), refetches everything after a reconnect, closes on sign-out.
- Known limits: no live run; no Dockerfile/compose entry for realtime-service (R13); no notifications list UI and no board UI yet; an open socket is not force-closed when a member is removed elsewhere (ids only; accepted and documented).

## 6b. What R12 delivered (details in `PROJECT_HANDOFF.md` 4m)
- `GET /api/v1/reports/visits/{visitId}/pdf?lang=ar|en` — technical visit report PDF (OpenHTMLtoPDF 1.1.112 LGPL-2.1 unmodified + ICU4J bidi/shaping + embedded IBM Plex Sans Arabic OFL); same access as the visit (technicians: own visits); only `data:` resources; no costs/internal notes; 30/min per user; `no-store`.
- `GET /api/v1/reports/dashboard?period=WEEK|MONTH|QUARTER|YEAR|ALL` (`reports:read`; money only with `finance:read`) — counts via tenant-filtered JPQL in the workspace time zone.
- Frontend: lazy dashboard panel on the home page (reports:read only), "Download PDF report" button on visits (job detail + technician's visits).
- Not done: charts (KPI tiles only), live run against the real backend, visit-report/photo editing UI (R10 leftovers → R13).

## 6c. R13 progress — done, verified and pushed
**Part 1: ops / production readiness** (configuration only, nothing deployed):
- `39c38bc` fix(api): `?sort=field,desc` was rejected (400 INVALID_SORT) on all 22 list endpoints. Spring binds it to `List<String>` as `["field","desc"]`; `shared/web/Pagination` now applies a bare asc/desc to the previous field (`PaginationTest` 3, `CustomersIT`). Found by the first LIVE run (mocks hid it). OTLP metrics push is off unless `OTLP_METRICS_ENABLED=true` (`application.yml`, `.env.example`). Test JVM pinned to UTC in the `backend/pom.xml` `argLine`, which fixes the midnight date-boundary flake (suite green at 01:09 Cairo).
- `1fab3fa` build(ops): `backend/Dockerfile` (Temurin 17 JRE noble, layered jar, non-root, liveness healthcheck), `realtime-service/Dockerfile`, `frontend/Dockerfile` + `frontend/nginx/` (SPA, `/api` + `/realtime` websocket proxy, caching, security headers + CSP); compose `--profile app` services backend/realtime/frontend (web on `WEB_PORT`, default 8088); `docker-compose.prod.yml`; `.github/workflows/ci.yml` (PRs, pushes to main, manual; actionlint clean; **never run on GitHub**); Linux screenshot baselines `*-linux.png`; `ops/backup/{backup,restore}.sh` (restore only into a NEW db / empty volume); `frontend/e2e-live` + `pnpm test:live`; `ops/perf/baseline.js` (k6); runbook `docs/operations/README.md`, `docs/operations/performance-baseline.md`.

**Part 2: R10 frontend leftovers** (lazy routes/chunks, shadcn components only, **no new npm dependency**):
- `94b7ca0` `/customers/:id` with sites and an add-site dialog (also inline in the new-work-order dialog). This was a **blocking gap found live**: no UI could create a site, so no work order could be created in a fresh workspace.
- `edcce25` header bell with unread count + `/notifications` (all/unread, paging, mark read, mark all read; texts built client-side from type + payload, ar/en, Arabic plural forms).
- `9b2e97d` "Visit record" sheet on every visit: report sections + customer confirmation name, photos by purpose with thumbnails, materials (prices only with `finance:read`).
- `6bfa41c` `/maintenance` (overdue visits, plans by status, new plan, pause/resume; nav entry enabled) and an equipment section on the customer page; realtime hints refresh `['maintenance']`/`['equipment']`; **fix:** the phone menu sheet now closes after choosing a screen (`AppShell` `MenuLink`).
- `9260925` customer approvals card on the work order (request now / decide later, or record an on-site decision).
- `1300a8c` `/visits/calendar` week agenda (Saturday to Friday) for `ops:read`. No calendar library (reuse-first: FullCalendar too heavy, the shadcn calendar is a date picker).

**Verification actually run (2026-11-20):**
- Backend `./mvnw test`: **134 tests, 0 failures, 0 errors, 0 skipped** (after `39c38bc`; no backend change since).
- Frontend at `1300a8c`: `pnpm lint` 0 errors; `pnpm build` OK, **280.0 kB gzip** (limit 350); `pnpm test:e2e` **60 passed, 2 skipped** (Windows). The suite (46 tests at that point) also passed inside the Linux CI image `mcr.microsoft.com/playwright:v1.65.1-noble`.
- `pnpm test:live` (real browser → nginx → Java → Postgres/Redis/RustFS/NestJS on the throwaway stack): **1 passed** at `9260925` (the calendar commit after it doesn't touch that flow). It covers register → customer → Arabic search → site → equipment → maintenance plan create/pause → work order → customer approval → visit start → report + real photo (WebP thumbnail loaded from storage under the CSP) + material → complete → real Arabic PDF download, with the realtime socket connected and the ticket only in the handshake.
- Backup → restore round trip (identical row counts; overwrite refused); k6 baseline 20 VUs/60 s: 22,491 requests, 0 errors, every p95 ≤ 105 ms.
- `realtime-service` `pnpm test` not re-run (no source change this session; its image build was verified).

**Environment notes:**
- Throwaway compose project **`crm-r13-smoke`** is still running with its own volumes (host ports 15432/16379/19000/19001/11025/18025/18088). Its throwaway secrets were in the session scratchpad, not in the repo. Stop it with `docker compose -p crm-r13-smoke down` (its volumes stay; deleting them needs the owner's OK by policy). The owner's `crm-platform` volumes were never touched.
- The live test registers a new workspace each run; registration is limited to 5/hour/IP.

**Known issues / not done:**
- CI has never run on GitHub (triggers: PR, push to main, manual), so it is UNVERIFIED there.
- `JobDetailPage` has no error boundary (a job payload without `site` would crash it; the real API always sends it).
- No signature capture: report and approval forms record the name only.
- The calendar shows no job title (the visit API has none); equipment has no detail/service-history screen yet.
- Screenshot baselines unchanged (the new bell icon is within the 1 % tolerance).

## 7. Next steps, in execution order
1. Verify state (§10 commands). If it differs, the repository wins: update the handoffs first.
2. Follow §2b (skills) and §2c (reuse-first) before any work.
3. Finish **R13**, remaining work in order: sector onboarding wizard (R10 leftover); optionally an equipment detail/service-history screen; then R13 close-out: full backend suite, realtime-service tests, frontend lint/build/e2e/live, update `PROJECT_HANDOFF.md` (§3 status R13 ✅, a 4n section) and this file, commit, push.
4. Platform-admin UI stays deferred to the final stage (owner decision). Owner decisions still open: real plan prices; the local dev DB password mismatch (the throwaway `crm-r13-smoke` stack is a non-destructive way to run live without fixing it).

## 8. Open decisions and operations needing the owner
Open decisions (ask; do not decide alone):
1. Real plan prices — seeded SOLO 300 / TEAM 900 / BUSINESS 2500 EGP per month are placeholders.
2. Local dev database: the Postgres Docker volume was created with a different password than the current `.env` (`password authentication failed for user "crm"`). Fix = reset the volume (**destroys local dev data — owner approval required**) or align `.env`. Needed for any live end-to-end run, including realtime.

Resolved: **D4 deviation approved 2026-11-20** (ADR-0015: Redis pub/sub, one-time tickets, no Streams/outbox, no RS256 for realtime; lost events accepted, frontend refetches).

Known issues: never run end to end against the real backend; scheduler dates use UTC, not the workspace time zone; no notification retention; no e-mail for billing/maintenance notices; Playwright baselines are Windows-only; `PlatformAdminIT` skips on a reused database.

Always requires the owner's explicit approval: database/volume resets or data deletion; anything on `main`, PRs, force-push, history rewrite, branch deletion, repo settings; changing approved decisions D1–D16; setting prices; deploying or applying infrastructure.

## 9. Permanent constraints (full list: `PROJECT_HANDOFF.md` §12)
- Develop only in `D:\Workspace\crm-platform`, only on `feature/saas-foundation`; never touch `main`; never create PRs; never force-push or rewrite history.
- Commit only verified work (tests green), author `ali-kassab <alikassab155@gmail.com>`, **no Claude co-author/attribution**; push verified commits to `origin feature/saas-foundation`.
- Continue phases without asking, except for: critical security issues, destructive/irreversible operations, uncovered business decisions, requirement conflicts, real blockers.
- Arabic-first (RTL) product; only the three sectors; manual payments, no gateway; expiry never deletes data; commercial values configurable.
- Fail-closed tenancy, `@TransactionalEventListener` + `TenantWork`, constructor injection only, Modulith boundaries green, Arabic text for every new error message/code, additive Flyway migrations.
- Realtime is hints only: never put business data in realtime messages and never depend on them for correctness.
- Frontend uses shadcn/ui components only (via the shadcn CLI); TypeScript 5.9; bundle budget.
- Never read legacy source in `D:\Workspace\Java CRM\crm\` or `analysis\`. No deployment, no `terraform apply`.
- Safe-stop phrase: «وقف عند نقطة آمنة وحدّث الهاند أوف» → finish or roll back the in-flight change, verify git, update both handoff files.

## 10. Commands to resume safely (Git Bash)
```bash
cd /d/Workspace/crm-platform
git branch --show-current          # expect: feature/saas-foundation
git status --short                 # expect: nothing
git log --oneline -2               # expect: the R13 safe-shutdown docs commit, then 1300a8c
git fetch origin && git status -sb # expect: in sync with origin/feature/saas-foundation
```
Toolchain: Docker Desktop must be running for backend tests (Testcontainers). If Docker crashes on stale sockets, run `D:\Workspace\tools\fix-docker-sockets.ps1`. Node 24 + pnpm:
```bash
export PATH="/d/Workspace/tools/node-v24.22.3-win-x64:$PATH"
```
Checks:
```bash
cd /d/Workspace/crm-platform/backend && ./mvnw test
cd /d/Workspace/crm-platform/realtime-service && pnpm install && pnpm test
cd /d/Workspace/crm-platform/frontend && pnpm install && pnpm lint && pnpm build && pnpm test:e2e
```
Do **not** run `git stash`, `git reset`, `git clean`, `git checkout -- <file>`, `docker compose down -v` or any volume/database reset.
