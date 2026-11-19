# ADR-0003: Authentication: stateless JWT + rotating refresh tokens

- Status: Accepted (2026-11-18)
- Access token: JWT (HS256 via the Nimbus encoder/decoder in Spring Security's resource server), **15 min** lifetime.
  - Claims: `sub` (user id), `wid` (active workspace id), `role`, `perms` (permission list), `sa` (super-admin flag), `jti`.
- Refresh token: an opaque random 256-bit value. Only its SHA-256 hash is stored in the `refresh_tokens` table.
  - It belongs to a **family** (rotation chain), lives **14 days** sliding with a **60-day** absolute limit, and is single use.
  - **Reusing a rotated token revokes the whole family** (theft detection).
- The browser receives the refresh token in an `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. The access token is returned in the body and kept in memory.
- CSRF: the cookie-authenticated endpoints (`/auth/refresh`, `/auth/logout`) require the `X-Requested-With: XMLHttpRequest` header plus an Origin check. Other endpoints use the Bearer token, so they aren't CSRF-prone.
- Passwords: BCrypt cost 12 (`DelegatingPasswordEncoder`). Lockout after 5 failed logins for 15 minutes (counter in Redis).
- Logout revokes the current family. Logout-all revokes all of the user's families and adds the current access-token `jti` to a Redis denylist until it expires.

