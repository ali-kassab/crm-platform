# SESSION HANDOFF — continuation guide for a NEW Claude Code session

_Updated 2026-11-20 (later session; free-tier staging config added `a47c70c`, deployment waiting for owner accounts — §6f): **Roadmap R0–R13 complete, platform-admin UI done** (`0d85543`), and the last documented in-scope polish item — an error boundary on the work-order screen — is implemented, pushed and fully verified incl. the live smoke run (`5ff273e`, §6e, §7 item 3). **Remaining backend gaps need the owner's decision (§7 item 4).** **No further approved scope exists: the next step is the owner's decision (§7–§8).**_

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
- Branch: **`feature/saas-foundation`**, in sync with `origin/feature/saas-foundation` (verify with `git status -sb`). Latest: `5ff273e` (error boundary), then this docs commit (HEAD). Previous session: `eb84b4b`, `602c22c`, `5f47e92` (docs), `0d85543` (admin UI), `2f00113` (shutdown docs). Earlier R13 commits: `39c38bc`, `1fab3fa`, `ef25092`, `94b7ca0`, `edcce25`, `9b2e97d`, `b385fdc`, `6bfa41c`, `9260925`, `1300a8c`, `c14091c`.
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
| R13 Performance & readiness | `39c38bc` … `602c22c` | ✅ backend 134/0/0/0, realtime 6/6, e2e 70 passed / 2 skipped, live 1 passed (§6c) |
| Final stage: platform-admin UI | `0d85543` | ✅ lint 0 errors, build 308.4 kB, e2e 74 passed / 2 skipped, live admin 1 passed (§6d) |
| Polish: work-order error boundary | `5ff273e` | ✅ backend 134/0/0/0, realtime 6/6, lint 0 errors, tsc clean, build 308.9 kB, e2e 78 passed / 2 skipped, live 2 passed (§6e) |

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

## 6c. R13 — complete, verified and pushed
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

**Part 3 (this session): last R10 leftovers** (shadcn only, no new npm dependency, no backend change):
- `eb84b4b` **sector onboarding wizard** `/setup` (`src/pages/setup/{SetupPage,api,sectors}.ts(x)`, owners/admins only, lazy 3.1 kB): sector (shadcn `radio-group`, added with the CLI) → name, default language, currency, time zone (`PATCH /workspace`; client checks: 3-letter currency, `Intl`-valid zone) → first-steps checklist per sector (FIT_OUT customer/job/visit; SECURITY_SYSTEMS customer/equipment/job/visit; MAINTENANCE customer/plan/job) ticked by page-size-1 probes. Sign-up now lands on `/setup` (`App.tsx` `GuestOnly` redirect — the live run showed the page's own `navigate` lost the race); home shows a prompt while `workspace.sector` is null; Settings links to it; `NewJobDialog` default type = INSTALLATION for FIT_OUT/SECURITY_SYSTEMS, else CORRECTIVE_REPAIR. `AuthState.updateWorkspace` merges saved settings into the session.
- `602c22c` **device card + devices on the work order**: `customers/EquipmentDetailsSheet.tsx` (device name opens a sheet: details, status select for `ops:write`, service history linking to work orders, warranties); `jobs/JobEquipmentCard.tsx` ("Devices" card; picker lists only the site's devices, not REMOVED ones; `PUT /jobs/{id}/equipment`; edit needs `ops:read` + `ops:write`, technicians only see the list). Without this, no screen could fill a device's service history.
- Skills read and applied this session: `frontend-design`, `vercel-react-best-practices` (lazy route, parallel `useQueries`, derived state, no effects), `webapp-testing` (method only; TypeScript Playwright per project rule; screenshots reviewed for RTL/mobile).

**Verification actually run (2026-11-20):**
- **R13 close-out at `602c22c`:** backend `./mvnw test` **134 tests, 0 failures, 0 errors, 0 skipped**; `realtime-service` `pnpm test` **6/6**; frontend `pnpm lint` 0 errors, `pnpm build` **290.3 kB gzip**, `pnpm test:e2e` **70 passed, 2 skipped** (new: `e2e/setup.spec.ts` 3 × 2, `e2e/equipment.spec.ts` 2 × 2); `pnpm test:live` **1 passed** (extended: setup wizard after sign-up, device linked to the work order on the real backend and shown in its service history).
- Earlier: backend 134/0/0/0 after `39c38bc`.
- Frontend at `1300a8c`: `pnpm lint` 0 errors; `pnpm build` OK, **280.0 kB gzip** (limit 350); `pnpm test:e2e` **60 passed, 2 skipped** (Windows). The suite (46 tests at that point) also passed inside the Linux CI image `mcr.microsoft.com/playwright:v1.65.1-noble`.
- `pnpm test:live` (real browser → nginx → Java → Postgres/Redis/RustFS/NestJS on the throwaway stack): **1 passed** at `9260925` (the calendar commit after it doesn't touch that flow). It covers register → customer → Arabic search → site → equipment → maintenance plan create/pause → work order → customer approval → visit start → report + real photo (WebP thumbnail loaded from storage under the CSP) + material → complete → real Arabic PDF download, with the realtime socket connected and the ticket only in the handshake.
- Backup → restore round trip (identical row counts; overwrite refused); k6 baseline 20 VUs/60 s: 22,491 requests, 0 errors, every p95 ≤ 105 ms.
- `realtime-service` `pnpm test` not re-run (no source change this session; its image build was verified).

**Environment notes:**
- Throwaway compose project **`crm-r13-smoke`** is running with its own volumes (web on host port **18088**; others 15432/16379/19000/19001/11025/18025). Its env file is outside the repo: `C:/Users/MEDIAT~1/AppData/Local/Temp/claude/652b2fe467101510/a967f3e4-6cdc-40ff-93c6-ab9d32019c3e/scratchpad/smoke.env` (a temp folder — it may be cleaned up; then the stack needs new throwaway secrets). Rebuild only the web image after frontend changes: `docker compose -p crm-r13-smoke --env-file <that file> --profile app up -d --build frontend`, then `LIVE_BASE_URL=http://localhost:18088 pnpm test:live`. Stop with `docker compose -p crm-r13-smoke down` (volumes stay; deleting them needs the owner's OK). The owner's `crm-platform` volumes were never touched.
- Docker Desktop crashed at start this session with the known stale-socket error (`dockerInference ... cannot be accessed`); fixed with `D:\Workspace\tools\fix-docker-sockets.ps1` (moves `%LOCALAPPDATA%\Docker\run` and `docker-secrets-engine` aside as `*.stale-<timestamp>`; nothing deleted).
- The live test registers a new workspace each run; registration is limited to 5/hour/IP.

**Known issues / not done:**
- CI has never run on GitHub (triggers: PR, push to main, manual), so it is UNVERIFIED there.
- ~~`JobDetailPage` has no error boundary~~ — done in `5ff273e` (§6e).
- No signature capture: report and approval forms record the name only.
- The calendar shows no job title (the visit API has none).
- Sector seeding of tags / board / milestone templates was not added: no screen uses them yet (the wizard covers sector choice, regional defaults, first steps and the work-order type suggestion).
- Screenshot baselines unchanged (the new bell icon is within the 1 % tolerance).

## 6d. Platform-admin UI (final stage) — commit `0d85543`, verified and pushed
- `frontend/src/admin/`: `session.ts` (`adminApi` + in-memory admin token; `credentials: 'omit'`, never the tenant token, no refresh; 401 → `onExpired`), `admin-context.ts`, `AdminApp.tsx` (lazy `/admin/*` routes outside the tenant shell; expiry timer from `expiresIn`; sign-out drops all `['admin', …]` queries; unmount or reload ends the session by design), `AdminLoginPage`, `RequestsPage` (tabs by status; receipt opens the presigned URL in a new tab; approve with optional note; reject needs a reason), `WorkspacesPage` / `WorkspaceDetailPage` (search, status filter, usage vs limits, requests, history; EXTEND 1–366 days, SUSPEND with required reason, REACTIVATE, CHANGE_PLAN), `PlansPage`, `MethodsPage` (create/edit), `AccountPage` (change email / password, 14+ chars). `App.tsx` route `/admin/*`; i18n `admin.*` (ar/en, Arabic plural forms for members).
- Uses only existing APIs (`PROJECT_HANDOFF.md` 4h); no backend change, no new dependency. Plan prices entered here remain the owner's business decision (seeded values are still placeholders).
- Tests: `e2e/admin.spec.ts` (2 × desktop/mobile, own fake `/api/v1/admin/**` route: separate token and no cookie on every admin call, wrong password, receipt popup, reject needs reason, approve body, extend/suspend bodies and validation, plan create body, 401 → back to sign-in with notice); `e2e-live/admin.spec.ts` (skipped unless `LIVE_ADMIN_EMAIL` / `LIVE_ADMIN_PASSWORD` are set) **1 passed** on `crm-r13-smoke`.
- For that live run a throwaway platform admin was bootstrapped on `crm-r13-smoke` only: `PLATFORM_ADMIN_EMAIL=admin@smoke.test` and a random password appended to that stack's env file (outside the repo, path in §6c), backend container recreated. Pass them as `LIVE_ADMIN_EMAIL` / `LIVE_ADMIN_PASSWORD` from that file; never commit them.
- Verification at `0d85543`: `pnpm lint` 0 errors (pre-existing warnings only); `pnpm build` OK **308.4 kB gzip** (limit 350; admin chunks lazy, largest 2.8 kB); `pnpm test:e2e` **74 passed, 2 skipped**; live admin **1 passed**. Backend/realtime unchanged since the R13 close-out (134/0/0/0, 6/6).
- **Not re-verified:** the live smoke test after `0d85543`: its run stopped at sign-up with "too many requests" (registration limit 5/hour/IP, used up by this session's live runs). It passed at `602c22c`; `0d85543` only adds the separate `/admin/*` route in `App.tsx` besides new files.

## 6e. Work-order error boundary — commit `5ff273e`, verified and pushed
- `frontend/src/components/layout/PageErrorBoundary.tsx` (new): minimal React class boundary (`getDerivedStateFromError`; React has no hook API, so no dependency such as `react-error-boundary` was added) + shadcn `Alert`/`Button`. Fallback: "couldn't show this page", **Try again** (calls `queryClient.resetQueries(retryQueryKey)` so a bad cached payload is refetched, then re-renders) and a link back to the list. Keyed on `pathname` → resets on navigation. Logs the error + component stack to the console only.
- `frontend/src/App.tsx`: `/jobs/:id` renders `JobDetailRoute` = `PageErrorBoundary` (back to `/jobs`, retry key `['job', id]`) around `JobDetailPage`. No other route changed (forward-only reuse policy); reuse the component for other screens only when a task needs it.
- i18n `errors.screenFailed`, `errors.screenFailedHint` (ar/en).
- Test: `frontend/e2e/error-boundary.spec.ts` (2 × desktop/mobile): a job payload without `assignees` shows the contained alert while the shell stays; retry refetches `/jobs/j9` and renders the fixed job; the back link clears the error.
- Verification (2026-11-20): `pnpm lint` exit 0, 0 errors (pre-existing warnings only, none in changed files); `pnpm exec tsc -b --noEmit` clean; `pnpm build` **308.9 kB gzip** (limit 350); `pnpm test:e2e` **78 passed, 2 skipped**. Backend/realtime unchanged (134/0/0/0, 6/6 at the R13 close-out). Skills read and applied: `frontend-design` (plain, directive error copy; existing design tokens), `vercel-react-best-practices` (no new bundle weight, no effects), `webapp-testing` (method only, TypeScript Playwright).
- Live smoke stack: web image rebuilt with `5ff273e`; `pnpm test:live` **2 passed** at 11:44 UTC (§7 item 3). Same session, same tree: backend `./mvnw test` **134 run, 0 failures, 0 errors, 0 skipped**; `realtime-service` `pnpm test` **6/6**.

## 6f. Free-tier staging deployment ($0) — config `a47c70c`, NOT deployed yet (waiting for owner accounts)
- Owner request 2026-11-20: deploy for private testing on free plans only (never paid, pause if a card is required).
- Chosen: Vercel Hobby (frontend), Render Free web service `crm-staging-api` (Java API, Docker, Frankfurt), Neon Free (Postgres), Upstash Free (Redis, TLS), **Backblaze B2** 10 GB (S3). **Cloudflare R2 rejected:** asks for a payment method before the first bucket. Realtime service **not deployed** (optional; `REALTIME_ENABLED=false`, Vercel does not proxy the ticket path → no socket). Mail optional (Render free blocks 25/465/587; Brevo on 2525 possible).
- Files: `render.yaml` (Blueprint; secrets `sync: false`, `JWT_SECRET` generated by Render), `frontend/vercel.json` (/api rewrite → `https://crm-staging-api.onrender.com`, SPA fallback, security headers, pnpm 12.21.0), `frontend/playwright.live.config.ts` (`LIVE_SLOW=1`), `frontend/e2e-live/smoke.spec.ts` (`LIVE_REALTIME=off`), runbook `docs/operations/free-tier-deployment.md`.
- Verified locally 2026-11-20 with Render's limits (`--cpus=0.1 --memory=512m`, empty database `crm_freetier` on `crm-r13-smoke`): default JVM start 459 s; with the `render.yaml` flags **163 s**, ~440 MB peak, no OOM; 17 migrations applied; full live flow **passed** (1.2 min; 37 s warm with `LIVE_SLOW=1`); CORS: unknown origin 403, allowed origin 200 + credentials; tenant API without session 401. Bundle scan: no secrets, no source maps. Frontend lint/tsc/build/e2e (78/2) re-run green; backend 134/0/0/0 and realtime 6/6 earlier the same day on identical code.
- **Unverified until deployed:** Render's BuildKit `--mount=type=cache` in `backend/Dockerfile`; real cold start on Render (Vercel proxy limit ~120 s → first requests after a sleep can fail); Vercel proxy body limit for photos > 4.5 MB; B2 with AWS SDK 2.59 (checksum env vars set); client IP for the sign-up rate limit behind Vercel + Render.
- Next: owner creates the accounts (runbook "Setup order"), tells Claude the two public URLs (never secrets), then Claude verifies `/actuator/health/liveness`, runs `LIVE_SLOW=1 LIVE_REALTIME=off LIVE_BASE_URL=<vercel> pnpm test:live`, a > 4.5 MB upload and a browser check, and records results.

## 7. Next steps, in execution order
1. Verify state (§10 commands). If it differs, the repository wins: update the handoffs first.
2. Follow §2b (skills) and §2c (reuse-first) before any work.
3. ✅ **Live smoke test — PASSED at `5ff273e`** (was temporarily blocked; history below). Run once after the key expired (key gone 11:44:03 UTC, run 11:44:08–11:44:41 UTC): `pnpm test:live` → **2 passed** (`e2e-live/smoke.spec.ts` full field flow 29.2 s; `e2e-live/admin.spec.ts` 12.3 s). Block history: stack `crm-r13-smoke`, web image rebuilt at `5ff273e`; admin live test passed at 11:17 UTC; the main test failed at sign-up with 429 `RATE_LIMITED` («طلبات كثيرة جدًا»).
   - Source: our own backend, `AuthService.register` → `RateLimiter.check("register:" + ip, 5, 1 h)` (`backend/src/main/java/com/crmplatform/identity/application/AuthService.java:94`, `shared/ratelimit/RateLimiter.java`: fixed window, Redis `INCR`, expiry set on the first hit only; rejected hits still count but don't extend the window). Not an external provider, not Claude/Anthropic.
   - Every smoke run registers a new workspace, and all host traffic reaches the backend from the Docker gateway IP, so all runs from this machine share one bucket.
   - Confirmed read-only in Redis at 11:26:37 UTC: key `rl:register:<gateway ip>` count 7, TTL 1043 s → **expires ≈ 11:44:00 UTC** (window opened ≈ 10:44 UTC from the previous session's runs).
   - One run only, after the key was gone (read-only check of the key, no retries). Rate limiter untouched. That run used 1 of the 5 sign-ups in the new window.
   - Command: `cd frontend && LIVE_BASE_URL=http://localhost:18088 pnpm test:live` (plus `LIVE_ADMIN_EMAIL` / `LIVE_ADMIN_PASSWORD` from the smoke env file). Before re-running, check the counter read-only: `rl:register:*` in the stack's Redis (count > 5 → wait for its TTL).
4. The approved roadmap (R0–R13 + platform-admin UI + documented polish) is implemented. Checked against `PRODUCT_AUDIT.md` §15/§5 on 2026-11-20, two backend items are **not** implemented and need the owner's decision before any work (new scope / content choices):
   - **Sector seed data** (R10 "seeders", audit §8 #14): no per-sector tags, board or milestone templates are seeded (the wizard covers sector, regional defaults, first steps, work-order type).
   - **Project status/lifecycle + progress** (audit §5 item 6): listed as an improvement but in no roadmap phase; `work/projects/domain/Project` has no status/progress (next migration would be `V18`).
   - Also unverifiable: R10 acceptance "scenario tests 1–9, 12–13" refers to the Master Directive, which is not saved anywhere.
   **Ask the owner what comes next; do not invent new product scope.**
5. Owner decisions still open (§8): real plan prices (now editable in `/admin/plans`); the local dev DB password mismatch; running CI on GitHub (needs a PR or a push to main — the owner's call); whether to keep or stop the `crm-r13-smoke` stack.

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
git log --oneline -2               # expect: the latest docs commit, then 5ff273e
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
