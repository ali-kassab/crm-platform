# Operations runbook (R13)

Configuration and procedures for running the platform outside a developer IDE. Nothing here deploys
anything by itself; applying it to a server is the owner's decision.

## 1. Container images
| Image | Build | Notes |
|---|---|---|
| backend | `docker build ./backend` | Temurin 17 JRE (Ubuntu, glibc — needed by the WebP native library), Spring Boot layered jar, non-root `app` user, healthcheck on `/actuator/health/liveness`, `SPRING_PROFILES_ACTIVE=prod` (JSON logs, no Swagger). |
| realtime | `docker build ./realtime-service` | Node 24 Alpine, production deps only, runs as `node`, healthcheck `GET /health`. |
| frontend | `docker build ./frontend` | Static build (bundle budget enforced) served by `nginx-unprivileged`; proxies `/api` → backend and `/realtime` (websocket) → realtime, SPA fallback, immutable caching for `/assets`, `no-cache` for `index.html`, security headers + CSP (`frontend/nginx/`). Upstreams: `API_UPSTREAM`, `REALTIME_UPSTREAM`. |

## 2. Full stack with Docker Compose
```bash
cp .env.example .env              # fill in secrets
docker compose --profile app up -d --build
# browser: http://localhost:8088 (WEB_PORT)
```
Without `--profile app` only the infrastructure starts (for running backend/frontend from the IDE).

**Isolated throwaway stack** (does not touch the normal `crm-platform` volumes): use another project
name and env file, e.g. `docker compose -p crm-smoke --env-file smoke.env --profile app up -d --build`
with different host ports (`POSTGRES_PORT`, `REDIS_PORT`, `S3_PORT`, `S3_CONSOLE_PORT`,
`MAILPIT_SMTP_PORT`, `MAILPIT_UI_PORT`, `WEB_PORT`). `docker compose -p crm-smoke down` stops it; its
volumes stay until removed explicitly.

## 3. Production (single host)
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile app up -d --build
```
`docker-compose.prod.yml`: no host ports for Postgres/Redis/storage; web only on `127.0.0.1:WEB_PORT`
behind a TLS reverse proxy or load balancer (**HTTPS is required** — refresh cookies are `Secure`);
Mailpit disabled; requires `MAIL_HOST`, `PUBLIC_URL`, `S3_PUBLIC_ENDPOINT`; bucket must exist;
memory limits. Before the first start set `PLATFORM_ADMIN_EMAIL`/`PLATFORM_ADMIN_PASSWORD`, then
remove the password (see `PROJECT_HANDOFF.md` §4 R0).

Checklist: strong random `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` (≥ 32 bytes), `S3_*`;
`BILLING_ENFORCEMENT_ENABLED=true`; set real plan prices in the admin API; schedule backups (§4);
`OTLP_METRICS_ENABLED=true` + `OTLP_METRICS_URL` only when a collector exists.

## 4. Backups and restore
```bash
ops/backup/backup.sh  --project crm-platform --env-file .env --out /var/backups/crm --keep 14
ops/backup/restore.sh --from /var/backups/crm/<stamp> --target-db crm_restored --confirm [--storage]
```
- Backup = `pg_dump` custom format (consistent snapshot, verified with `pg_restore --list`) + a
  tar.gz of the object-storage volume + `SHA256SUMS`. Old timestamped folders beyond `--keep` days
  are pruned. Copy the folder off the host (another disk/region).
- Restore checks the checksums, **creates a new database** (refuses if it exists — never overwrites
  live data), then you point `DB_URL` at it. `--storage` extracts files only into an **empty**
  storage volume.
- The storage archive is taken while the store runs; uploaded files are immutable, so at worst a file
  uploaded during the backup is missing. Stop `storage` first for a strictly consistent copy.
- On AWS use RDS automated backups/PITR and S3 versioning instead.
- Verified 2026-11-20 on the throwaway stack: backup → restore into `crm_restore_check` → identical
  row counts; second restore into the same name refused.

## 5. CI
`.github/workflows/ci.yml`: backend `mvnw test` (Testcontainers), realtime `pnpm test`, frontend lint +
build (bundle budget) + Playwright in `mcr.microsoft.com/playwright:v1.65.1-noble` (Linux screenshot
baselines `*-linux.png` were generated in that image), and the three image builds. Runs on pull
requests, pushes to `main` and manual dispatch (not on every feature-branch push).

To regenerate Linux baselines after an intended UI change, run the update inside the same image
(see the command in `SESSION_HANDOFF.md` / R13 notes) and commit only `*-linux.png`.

## 6. Live smoke test (real stack, real browser)
```bash
cd frontend && LIVE_BASE_URL=http://localhost:8088 pnpm test:live
```
`e2e-live/` registers a brand-new workspace each run (never touches existing data) and checks the
real API, Arabic search normalisation, CSP, and that the realtime socket connects through nginx with
the ticket only in the handshake.

## 7. Performance baseline
`ops/perf/baseline.js` (k6). See `performance-baseline.md` for the measured numbers and targets.
