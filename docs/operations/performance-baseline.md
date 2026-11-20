# Performance baseline (R13)

Measured 2026-11-20 with `ops/perf/baseline.js` (k6 1.3.0 in Docker) against the containerised
stack (nginx → backend → Postgres 17 / Redis 8; Docker Desktop on the Windows dev machine), through
the web container like a browser. 20 virtual users, 60 s, no think time; one fresh workspace seeded
with 40 customers, 40 sites, 40 work orders and 40 visits.

| Request | Target p95 | Measured p95 | p99 |
|---|---|---|---|
| Customers list (`sort=createdAt,desc`) | 300 ms | 83 ms | 102 ms |
| Customer search (Arabic, normalised) | 300 ms | 80 ms | 101 ms |
| Work orders list | 300 ms | 105 ms | 138 ms |
| Work order detail | 300 ms | 104 ms | 132 ms |
| Visits list | 300 ms | 82 ms | 103 ms |
| Unread notifications count | 150 ms | 72 ms | 87 ms |
| Dashboard (month) | 500 ms | 96 ms | 129 ms |

Totals: 22,491 requests, 340 req/s, **0 % errors**, all thresholds passed.

Limits of this baseline: small data volume per workspace, single machine (load generator and stack
share CPU), no TLS. Re-run on the target server with production-like volumes before launch; the
`%…%` searches have no trigram index yet — add one (`pg_trgm`, new Flyway migration) if search p95
exceeds the target at real volumes.
