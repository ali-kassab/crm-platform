# ADR-0010: Redis usage

- Status: Accepted (2026-11-18)
1. Rate limiting (Bucket4j + Lettuce): login, register, password reset, uploads.
2. Login-failure counters and lockout.
3. Access-token `jti` denylist (TTL = remaining token life).
4. Caches with TTL and evict-on-write: reference data (categories, statuses, tags) for 10 min; permissions per (user, workspace) for 5 min.
5. Pub/sub fan-out of board events.

If Redis is down: caching falls back to the database, the rate limiter **fails open** with a logged warning, the denylist **fails closed** only for logout-all (otherwise tokens expire in at most 15 min anyway), and readiness reports DEGRADED.

