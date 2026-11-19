# Implementation Plan

Repository: `crm-platform` (private GitHub repo, monorepo).
Commits are small and use the Conventional Commits format (`feat(crm): …`, `test(work): …`, `chore(ci): …`). Each slice = several commits on `main`, kept green (CI must pass at each push).

## M2: foundation + first slices (shippable)
| # | Step | Output |
|---|---|---|
| 1 | Repo bootstrap | README stub, .gitignore, .editorconfig, LICENSE placeholder (MIT pending your confirmation), .env.example, docs/specs copy, ADRs |
| 2 | Backend skeleton | Maven Wrapper, Boot 4.1.2 parent, modules package layout, application.yml profiles (dev, test, demo, prod), Actuator, Problem Details handler, Modulith verify test, ArchUnit test |
| 3 | Docker Compose (dev) | postgres, redis, mailpit, S3-compatible storage, backend, frontend; health checks; volumes |
| 4 | Flyway V1 identity + security | JWT encoder/decoder, refresh rotation, password encoder, lockout and rate limit (Redis), tenant context filter, permission evaluator |
| 5 | identity APIs | register/login/refresh/logout/switch/password/invitations/members/workspace + seed defaults + email port (SMTP→Mailpit) |
| 6 | Frontend skeleton | Vite + React + TS + Tailwind + shadcn init; sidebar block shell; router; TanStack Query; auth store + refresh interceptor; OpenAPI client generation; login/register/forgot/reset/invite pages |
| 7 | Reference data | categories/statuses/tags + cache + UI settings tab |
| 8 | Files | storage port, S3 adapter, upload/presign/delete, orphan cleanup |
| 9 | CRM | contacts, locations, notes, milestones (+ templates), geocoder port; UI lists/forms/detail |
| 10 | Work (without real-time) | projects, team members, boards, columns, tasks, comments, board templates, flowcharts metadata; UI including the kanban (REST only) |
| 11 | CI | GitHub Actions backend-ci, frontend-ci, secret scan, dependency scan, Docker build |
| — | **Checkpoint B** | Logged in PROJECT_PROGRESS (no stop, per directive) |

## M3: complete domain
| # | Step |
|---|---|
| 12 | Real-time: STOMP config, auth interceptor, board events after commit, Redis relay, notifications push; UI subscription |
| 13 | Operations: jobs (+ board, files), warranties, expiry job |
| 14 | Finance: transactions, void, computed accounting; UI finance tabs |
| 15 | Automation: procedures, command registry and handlers, triggers via events, scheduler + ShedLock, runs UI, procedure editor |
| 16 | Audit + notifications + reporting dashboard |
| 17 | NF-01 flowchart diagram editor (React Flow) |
| 18 | Observability: Prometheus, OTel, JSON logs, Grafana dashboard JSON, Compose `observability` profile |
| 19 | Playwright E2E + Compose-based e2e workflow |
| — | **Checkpoint C** |

## M4: infrastructure & portfolio
| # | Step |
|---|---|
| 20 | Production Dockerfiles (backend layered jar, non-root; frontend nginx unprivileged) |
| 21 | Terraform: modules network, data (RDS, ElastiCache), storage (S3 + CloudFront), app (ECR, ECS Fargate, ALB, IAM, Secrets Manager, CloudWatch); envs staging and prod; remote state config (S3 native locking); `fmt`/`validate`/`tflint` in CI — **never applied** |
| 22 | Deploy workflow (OIDC, ECR push, ECS deploy, manual approval) — written, `workflow_dispatch` only, documented as Planned |
| 23 | Demo seed profile |
| 24 | README (badges, architecture and ERD diagrams, quick start), CONTRIBUTING, SECURITY, AWS_ARCHITECTURE (with cost estimate), RUNBOOK, TRACEABILITY |
| 25 | Final verification in a clean clone + FINAL_REPORT.md |

## Definition of Done per slice
- FRs implemented; TS scenarios automated (or marked manual with a reason).
- `./mvnw verify` green, including Testcontainers and the Modulith and ArchUnit checks. JaCoCo ≥ 80% on the domain and application packages of the slice.
- `pnpm lint && pnpm typecheck && pnpm test && pnpm build` green.
- OpenAPI regenerated and the TS client regenerated.
- Docs updated (TRACEABILITY, COMPATIBILITY_DIFFERENCES if needed).

## Risks
| Risk | Mitigation |
|---|---|
| Scope size (10 modules + UI) | Vertical slices, generated client, shared table/form compositions |
| Tooling: Node 18 local | Portable Node 24 in a tools folder (no system install) or Docker-based builds |
| Docker availability (Testcontainers) | Docker Desktop started; tests tagged so unit tests run without Docker |
| Registry component churn (kanban) | Pinned registry item version, recorded in an ADR |
| Clean-room purity | Implementation reads only `docs/specs`; deny rule on the legacy folder; honest statement in FINAL_REPORT |
