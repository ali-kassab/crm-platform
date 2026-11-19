# SESSION HANDOFF — continuation guide for a NEW Claude Code session

_Updated 2026-11-20: R11 complete (`0c4f936`), R11 browser tests and the skills policy added afterwards. State verified with git and fresh test runs on that date. Everything marked UNVERIFIED could not be checked._

## 1. Active repository (read first)
- **Active repository: `D:\Workspace\crm-platform`** — the ONLY place for development, Git operations and tests.
- ⚠️ **`D:\Workspace\Java CRM` is the OLD folder (not a git repository).** Never develop, commit, run tests or run Git there. Only the documentation files listed in §3 may be read from it. **Never read** `D:\Workspace\Java CRM\crm\` or `D:\Workspace\Java CRM\analysis\` (legacy application source — clean-room rule).
- The old `D:\Workspace\Java CRM\SESSION_HANDOFF.md` is superseded by this file.

## 2. Reading order
1. `D:\Workspace\crm-platform\PROJECT_HANDOFF.md` — permanent project reference (status per phase, files, APIs, decisions, constraints §12). **Read it fully first.**
2. This file — exact continuation steps.
3. Only when needed: the documents in §3.
4. **Then follow the skills policy below before any implementation.**

## 2b. Installed Skills and Mandatory Usage Policy (summary — full rule: `PROJECT_HANDOFF.md` §0b)
Every new session MUST follow this; `PROJECT_HANDOFF.md` §0b is the source of truth and the two files must stay consistent.
- Installed globally: `frontend-design`, `webapp-testing`, `vercel-react-best-practices`, `spring-data-jpa`, `flyway-migrations`, `multi-tenancy`.
- At session start: discover the skills this session lists, load each relevant one (Skill tool) to confirm it can be read, and read its instructions before related work.
- Use per task: frontend UI/UX → `frontend-design`; React implementation/optimization → `vercel-react-best-practices`; browser/e2e testing → `webapp-testing`; JPA entities/repositories/queries → `spring-data-jpa`; schema changes/migrations → `flyway-migrations`; tenant isolation/tenant-aware persistence → `multi-tenancy`.
- Project architecture, security, approved decisions and conventions always override a skill's generic advice (e.g. TypeScript Playwright, not the skill's Python scripts; existing `TenantContext` model; additive Flyway, next `V18`).
- Never claim a skill was used unless it was actually discovered and read. If one is unavailable, report it and proceed cautiously with project conventions. Never install, update or replace skills without the owner's permission.
- Last verified 2026-11-20: all six readable; Python Playwright (for `webapp-testing` helper scripts) is not installed.

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
- Branch: **`feature/saas-foundation`**. Last commits: `0c4f936` feat(realtime) → `fac6486` docs (R11 handoff) → `40e5da3` test(frontend) realtime browser tests → docs commit "docs: skills usage policy and R11 browser test results in handoffs" (HEAD). All pushed to `origin/feature/saas-foundation`.
- Remote: `origin` = `https://github.com/ali-kassab/crm-platform.git`.
- `origin/main` = **`7ae560a`** (PR #3, merged by the owner; contains R0–R10, **not R11**). Local `main` is stale at `163d49c`. **Do not touch `main` in any way**; opening/merging a PR for R11 is the owner's decision.
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
| R12 Reports & Arabic PDF | — | not started |
| R13 Performance & readiness | — | not started |

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

## 7. Next steps, in execution order
1. Open the active repo and verify the state matches §4 (commands in §10). If it differs, the repository wins — update the handoffs before doing anything else.
2. **R12 — reports & Arabic PDF** (D15: OpenHTMLtoPDF + ICU4J shaping + embedded Arabic font; verify a real Arabic PDF). Next Flyway migration is `V18`. Tests → commit → push → update both handoffs.
3. **R13** — performance baselines/load test, Dockerfiles (backend, frontend, realtime-service) + docker-compose entry for realtime-service, CI (Linux Playwright baselines), backups, deployment config (no apply/deploy), plus R10 leftovers (`PROJECT_HANDOFF.md` 4k) and a notifications list/badge UI.

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
git log --oneline -2               # expect: the skills-policy docs commit, then 40e5da3
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
