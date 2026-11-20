# Free-tier staging deployment ($0, private testing only)

Goal: open the MVP in a browser on real hosted services at **no cost**. Not production: every
service below is a free plan with sleep, quota and durability limits. Config only — no product code
changed. Files: `render.yaml` (backend), `frontend/vercel.json` (frontend).

## Services and plans (checked 2026-11-20)

| Part | Provider / plan | Card needed? | Main limits that matter here |
|---|---|---|---|
| Frontend (React build) | **Vercel Hobby** | no | non-commercial use only; ~100 GB/month bandwidth; proxied (rewrite) requests time out after ~120 s |
| Java API | **Render Free web service** (Docker, Frankfurt) | no (card only changes overage handling) | 0.1 CPU, 512 MB; sleeps after 15 min idle; 750 instance-hours/month per workspace (one service 24/7 = 744 h); SMTP ports 25/465/587 blocked; ephemeral disk; Render may restart it any time; "not for production" |
| PostgreSQL | **Neon Free** (AWS Frankfurt) | no | 1 GB storage per project; 100 CU-hours per project per month; compute scales to zero when idle (wakes in ~1 s) |
| Redis | **Upstash Free** (Frankfurt, TLS) | no | 500 K commands/month; ~256 MB; 10 GB bandwidth |
| File storage (S3 API) | **Backblaze B2** free 10 GB | no | 10 GB stored; free downloads limited (daily/3× storage — check the B2 caps page) |
| Realtime (NestJS) | **not deployed** | — | optional by design (ADR-0015); the app works on plain REST; screens refresh on load |
| Mail | optional (e.g. Brevo free, port 2525) | no | without it invitations / password resets are not delivered (failures are only logged) |

Rejected for $0: **Cloudflare R2** asks for a payment method before the first bucket; Render's own
free Postgres expires after 30 days; Render free Key Value loses data on restart.

## Measured on this project (local Docker with Render's free limits: `--cpus=0.1 --memory=512m`)
- Default JVM flags: start **459 s**, 410 MB. With the `JAVA_TOOL_OPTIONS` in `render.yaml` (serial GC,
  C1 only, smaller stacks/code cache): start **163 s**, ~350 MB idle, **~440 MB peak** during the full
  flow, no OOM. All 17 Flyway migrations applied on an empty database.
- Full live browser flow (register → setup → customer → site → device → plan → work order → approval →
  visit → report + photo → materials → complete → Arabic PDF): **passed in 1.2 min** (29 s unthrottled).
- Consequence: a **cold start takes ~3 min**. Through Vercel's proxy the first requests after a sleep can
  fail (120 s limit) until the API is up. Optional keep-warm: an external monitor (e.g. UptimeRobot free)
  calling `/actuator/health/liveness` every 5 min keeps one service awake within the 750 h.

## How the parts connect
- Browser → `https://<vercel-app>/api/*` → Vercel rewrite → `https://crm-staging-api.onrender.com/api/*`.
  Same origin for the browser, so the `SameSite=Strict` refresh cookie works. `CORS_ALLOWED_ORIGINS`
  and `CLIENT_BASE_URL` = the Vercel production URL.
- `/api/v1/realtime/*` is rewritten to a missing file → no ticket → the app never opens a socket.
- Files: uploads go through the API (multipart); downloads/thumbnails are presigned B2 URLs (CSP
  `img-src https:`). **Unverified:** whether Vercel's proxy limits request bodies (Vercel documents
  4.5 MB only for its Functions) — test a photo > 4.5 MB after deploying.
- Neon: use the **direct** host (not `-pooler`), `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`.
  Hikari keeps no idle connections and sends no keep-alives so Neon can suspend; the health check
  (`/actuator/health/liveness`) touches neither Neon nor Redis. Direct, not pooled: Flyway migrates at
  startup on a session connection (Neon's pooler is PgBouncer in transaction mode) and Hikari already pools.
  Neon's Connect dialog adds `&channel_binding=require`; the JDBC URL only needs `sslmode=require`.
- Neon Postgres version: the project runs **Postgres 18** (local Docker and CI use 17). Verified
  2026-11-20 on `postgres:18-alpine` (18.7): all 17 Flyway migrations apply, Hibernate `validate`
  passes, no warnings. Migrations need no extensions and no superuser; the default owner role is enough.
- Local copy of the staging values: the git-ignored `.env.neon` at the repo root (`DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD` + the Hikari settings above), separate from the local-Docker `.env`.
- Upstash: `REDIS_HOST`=endpoint, `REDIS_PASSWORD`=password, TLS on via `SPRING_DATA_REDIS_SSL_ENABLED`.
- B2: `S3_ENDPOINT` = `S3_PUBLIC_ENDPOINT` = `https://s3.<region>.backblazeb2.com`, `S3_REGION` = `<region>`
  (e.g. `eu-central-003`), private bucket, application key limited to that bucket. AWS SDK checksums
  only when required (`AWS_REQUEST_CHECKSUM_CALCULATION=WHEN_REQUIRED`).

## Setup order (owner actions marked ⚑ — never paste secrets into chat or commit them)
1. ⚑ **Neon** ✅ done 2026-11-20: Free project `crm-platform`, AWS Frankfurt (`eu-central-1`),
   Postgres 18, branch `production`, database `neondb`, role `neondb_owner` (Neon's defaults; no
   extra database created). Optional Neon services (Auth, Data API, Functions, AI Gateway) stay off.
   Connection values (direct host) are in the local `.env.neon`.
2. ⚑ **Upstash** ✅ done 2026-11-20: Free Redis database `crm-staging-redis`, AWS Frankfurt
   (`eu-central-1`), TLS, eviction off (all keys have TTLs; avoids dropping token-denylist entries).
   User `default` + password. Values (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`) are in the local
   git-ignored `.env.upstash`; TLS PING/SET/GET/INCR/EXPIRE verified, plaintext refused.
3. ⚑ **Backblaze B2** ✅ done 2026-11-20: private bucket `crm-staging-files-bf1e36` in
   `eu-central-003` (endpoint `https://s3.eu-central-003.backblazeb2.com`), SSE-B2 encryption,
   Object Lock off, lifecycle "keep only the last version" (deleted files stop counting toward 10 GB),
   no CORS rules (the browser only follows presigned links). Account caps all $0. Application key
   `crm-staging-api`: read/write, this bucket only, cannot list other buckets. All `S3_*` values are
   in the local git-ignored `.env.b2`; put/get/presigned GET/delete verified, unsigned access refused.
4. ⚑ **Render**: sign in with GitHub, allow access to this repository, *New → Blueprint*, branch
   `feature/saas-foundation`, confirm the **Free** plan, and fill the prompted values (`sync: false`).
   `CLIENT_BASE_URL`/`CORS_ALLOWED_ORIGINS` = the Vercel URL from step 5 (edit later if needed).
   Set `PLATFORM_ADMIN_EMAIL`/`PLATFORM_ADMIN_PASSWORD` (≥ 14 chars, letters + digits) for the first
   start, then **delete `PLATFORM_ADMIN_PASSWORD`** once the log says the admin was created.
   If Render gives the service a URL other than `crm-staging-api.onrender.com`, update the rewrite in
   `frontend/vercel.json`.
5. ⚑ **Vercel**: import the repository, project name `crm-platform-staging`, **Root Directory
   `frontend`**, Hobby plan, then Settings → Git → **Production Branch `feature/saas-foundation`**
   (production URL is public; preview URLs are behind Vercel login).
6. Optional ⚑: UptimeRobot keep-warm monitor; Brevo SMTP (`MAIL_HOST=smtp-relay.brevo.com`,
   port 2525, plus `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`,
   `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`
   in Render — not verified yet).

## Verify
```bash
curl -s https://crm-staging-api.onrender.com/actuator/health/liveness
cd frontend && LIVE_SLOW=1 LIVE_REALTIME=off LIVE_BASE_URL=https://<vercel-url> pnpm test:live
```
`LIVE_SLOW=1` lengthens waits for the 0.1-CPU host; `LIVE_REALTIME=off` expects no socket. Each run
registers a new workspace (limit 5 per IP per hour).

## Durability
Free plans give no backup guarantee: Neon keeps a short restore history only; Render's disk is
ephemeral (nothing is stored there). Treat all data as test data.
