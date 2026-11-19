# SESSION HANDOFF — continuation guide for a NEW Claude Code session

_Updated 2026-11-20 (checkpoint): R13 IN PROGRESS — part 1 (ops/infra) and part 2a (sites, notifications, visit record) done and pushed up to `9b2e97d`; next: maintenance plans + equipment screens — see §6c. The owner authorized continuous execution through the roadmap on 2026-11-20._

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

## 4. Git state (2026-11-20)
- Branch: **`feature/saas-foundation`**. Last commits: … → `e1ad62b` docs (reuse-first policy) → `c27961a` feat(reporting) R12 → docs commit "docs: update handoff - R12 reports and Arabic PDF complete" (HEAD). All pushed to `origin/feature/saas-foundation` (verify with `git status -sb`).
- Remote: `origin` = `https://github.com/ali-kassab/crm-platform.git`.
- `origin/main` = **`7ae560a`** (PR #3, merged by the owner; contains R0–R10, **not R11 or R12**). Local `main` is stale at `163d49c`. **Do not touch `main` in any way**; opening/merging a PR for R11 is the owner's decision.
- Git identity (local config): `ali-kassab <alikassab155@gmail.com>`; no Claude co-author/attribution lines.
- Working tree: clean apart from git-ignored files (`node_modules/`, `dist/`, `backend/target/`, Playwright reports).
- Remote verified by the successful push of `0c4f936` and the docs commit on 2026-11-20.

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
| R13 Performance & readiness | — | not started |

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

## 6c. R13 progress (checkpoint)
Done and pushed:
- `39c38bc` fix(api): `?sort=field,desc` was rejected (400 INVALID_SORT) on all 22 list endpoints — Spring splits it into `["field","desc"]`; `Pagination` now applies a bare direction to the previous field. Found by the first LIVE run (mocks hid it). OTLP metrics push off unless `OTLP_METRICS_ENABLED=true`. Test JVM pinned to UTC in `pom.xml` (§7 flake fixed). Backend **134 tests green**.
- `1fab3fa` build(ops): Dockerfiles (backend/realtime/frontend+nginx), compose `--profile app`, `docker-compose.prod.yml`, CI `.github/workflows/ci.yml` (PRs, main, manual; actionlint clean; **never run on GitHub yet**), Linux screenshot baselines, `ops/backup/{backup,restore}.sh`, `frontend/e2e-live` + `pnpm test:live`, `ops/perf/baseline.js`, `docs/operations/{README,performance-baseline}.md`.
- Verified on an isolated throwaway compose project `crm-r13-smoke` (own volumes/ports, env in the session scratchpad; the owner's `crm-platform` volumes untouched): images healthy; live browser smoke green; backup→restore identical counts; k6 20 VUs: 0 errors, all p95 ≤ 105 ms.
- Throwaway stack `crm-r13-smoke` may still be running/left with its volumes: stop with `docker compose -p crm-r13-smoke down` (removing its volumes needs the owner's OK by policy).

R13 part 2 (frontend leftovers from `PROJECT_HANDOFF.md` 4k) — done and pushed:
- `94b7ca0` customer page `/customers/:id` with sites + "add site" (also inline in the new-work-order dialog). This was a **blocking gap found live**: no UI could create a site, so no work order could be created in a fresh workspace.
- `edcce25` notifications: header bell with unread count (all 6 Arabic plural forms) + `/notifications` (tabs, paging, mark read / all read, links to work order/subscription).
- `9b2e97d` visit record sheet (report sections + customer confirmation name, photos by purpose with thumbnails, materials; prices only with `finance:read`).
- Checks after `9b2e97d`: lint 0 errors; build **268.9 kB gzip**; `pnpm test:e2e` **50 passed, 2 skipped**; `pnpm test:live` 1 passed — full real flow (register → customer → site → work order → visit → report + real photo upload/thumbnail + material → complete → Arabic PDF, realtime socket via nginx).
- Live test needs a fresh registration per run; registration is limited to 5/hour/IP (on the throwaway stack the `rl:register:*` Redis keys were cleared between runs).

Next (in order): maintenance plans + equipment screens (enable the disabled "maintenance" nav entry), job approvals UI, calendar view, sector onboarding; then R13 close-out (full backend suite, handoff docs). Platform-admin UI stays deferred to the final stage (owner decision). Known small issue: `JobDetailPage` has no error boundary — a job payload without `site` would crash the screen (the real API always sends it).

## 7. Next steps, in execution order
1. Open the active repo and verify the state matches §4 (commands in §10). If it differs, the repository wins — update the handoffs before doing anything else.
2. **Wait for the owner's explicit go-ahead.** The next phase is **R13 — Performance & production readiness**: performance baselines/load test, Dockerfiles (backend, frontend, realtime-service) + docker-compose entry for realtime-service, CI (Linux Playwright baselines), backups, deployment config (no apply/deploy), test-JVM time-zone fix, plus R10 leftovers (`PROJECT_HANDOFF.md` 4k) and a notifications list/badge UI. Follow §2b and §2c.

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
git log --oneline -2               # expect: the R12 docs commit, then c27961a
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
