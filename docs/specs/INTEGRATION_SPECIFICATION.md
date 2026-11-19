# Integration Specification (ports & adapters)

All external dependencies sit behind interfaces in the owning module's `application` layer. Adapters are chosen per Spring profile.

| Port | Operations | Local / test adapter | Production design (not deployed) | Failure behavior |
|---|---|---|---|---|
| `EmailSender` (shared) | `send(EmailMessage{to[], subject, html, text?, tags})` → `SendResult{messageId}` per recipient | `SmtpEmailSender` → **Mailpit** (`smtp://mailpit:1025`, UI on :8025). In tests: in-memory recorder | Amazon SES (SMTP or SDK), sender per workspace (future) | Timeout 10 s. Retryable for automation (3×). Auth emails: logged and the user sees the generic 202 |
| `SmsSender` (automation) | `send(to E.164, message)` → `messageId` | `LoggingSmsSender` (logs a masked number plus the length) | Amazon SNS / Twilio (future) | Same as email |
| `FileStorage` (files) | `put(key, stream, contentType, size)`, `delete(key)`, `presignGet(key, ttl, disposition)` | S3-compatible container (endpoint override, path-style) | Amazon S3 private bucket, SSE-S3, lifecycle rule for orphans | Upload failure → 503 `STORAGE_UNAVAILABLE`. Delete failure → retried by the cleanup job |
| `Geocoder` (crm) | `geocode(Address)` → `Optional<Coordinates>` | `NoopGeocoder` (always empty) / `FakeGeocoder` in tests | Amazon Location Service place index (future) | Never blocks a save. The warning `GEOCODING_FAILED` is added to the response |
| `Clock` | `now()` | system UTC | — | — |
| `NotificationPublisher` (notifications) | `toUser(userId, Notification)` | STOMP user destination | same | best effort |
| `BoardEventPublisher` (work) | `publish(boardId, event)` | STOMP simple broker + Redis relay | same + ElastiCache | best effort, logged |

## Email templates (Thymeleaf, HTML + text)
| Template | Variables |
|---|---|
| `password-reset` | firstName, resetUrl, expiresInMinutes |
| `workspace-invitation` | inviterName, workspaceName, role, acceptUrl, expiresInDays |
| `automation` | free HTML from the procedure (sanitized with the OWASP Java HTML Sanitizer) |

## Configuration (env vars, see `.env.example`)
`DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, JWT_SECRET (≥ 32 bytes, required), CLIENT_BASE_URL, CORS_ALLOWED_ORIGINS, MAIL_HOST, MAIL_PORT, MAIL_FROM, S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY, S3_PATH_STYLE, DEMO_PASSWORD, OTEL_EXPORTER_OTLP_ENDPOINT, SPRING_PROFILES_ACTIVE`.

## Contract tests
Each adapter has an integration test against its real local counterpart (Testcontainers: PostgreSQL, Redis, Mailpit/GreenMail, S3-compatible container). Port behavior is also covered by unit tests with fakes.
