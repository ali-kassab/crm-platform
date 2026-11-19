# Validation & Error Handling

## 1. Error format (RFC 9457)

```json
{
  "type": "https://crm-platform.dev/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/v1/contacts",
  "code": "VALIDATION_ERROR",
  "errors": [ { "field": "email", "code": "EMAIL_INVALID", "message": "must be a valid email" },
              { "field": "name.last", "code": "REQUIRED", "message": "is required" } ],
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

- Messages are generic and safe to show users. Stack traces, SQL and internal ids never appear.
- Unexpected errors → 500 `INTERNAL_ERROR` with only the `traceId`. The details go to the log.

## 2. Status & code catalog
| HTTP | code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | Bean Validation failures, malformed JSON (`MALFORMED_REQUEST`), wrong types |
| 400 | business codes | `MANAGER_IN_TEAM`, `JOB_NOT_IN_PROJECT`, `TASK_PARENT_REQUIRED`, `TASK_PARENT_AMBIGUOUS`, `COLUMN_NOT_ON_BOARD`, `SAME_START_STOP_PROCEDURE`, `INVALID_TOKEN`, `INVALID_PASSWORD`, `UNKNOWN_COMMAND`, `INVALID_ACTION_PARAMS` |
| 401 | UNAUTHENTICATED / TOKEN_EXPIRED / INVALID_CREDENTIALS / INVALID_REFRESH / REFRESH_REUSED | auth failures |
| 403 | FORBIDDEN, NO_WORKSPACE | missing permission or not the owner |
| 404 | NOT_FOUND | missing **or another tenant's** resource |
| 409 | NAME_TAKEN, EMAIL_TAKEN, ALREADY_MEMBER, IN_USE, HAS_PROJECTS, COLUMN_NOT_EMPTY, LAST_OWNER, PROCEDURE_ALREADY_BOUND, PROCEDURE_IN_USE, ALREADY_VOIDED, RUN_FINISHED, FILE_IN_USE, BOARD_CONFLICT (the body includes the `board`) | state conflicts |
| 412 | STALE_VERSION | `If-Match` doesn't match |
| 413 | PAYLOAD_TOO_LARGE | upload or body limits |
| 415 | UNSUPPORTED_MEDIA_TYPE | sniffed file type not allowed |
| 423 | ACCOUNT_LOCKED | lockout (`retryAfter` seconds) |
| 429 | RATE_LIMITED | with `Retry-After` |
| 503 | DEPENDENCY_UNAVAILABLE | DB down (Redis being down degrades service but doesn't produce 503) |

## 3. Field validation rules (applied to request DTOs)
| Field type | Rule |
|---|---|
| email | RFC 5322 (Hibernate `@Email` + regex), ≤ 254, trimmed, lower-cased |
| phone | E.164 via libphonenumber (default region from workspace locale); stored as E.164 |
| person name parts | 1–60, trimmed; title ∈ {Mr, Mrs, Ms, Mx, Dr, Eng, Prof} |
| free text short (title/name) | trimmed, length per BR, no control characters |
| free text long (description/body) | ≤ 5000 unless stated otherwise |
| color | `^#[0-9A-Fa-f]{6}$` |
| icon | `^[a-z0-9-]{1,40}$` (lucide icon name) |
| money | decimal, scale ≤ 2, 0 ≤ x ≤ 999,999,999,999.99 (`> 0` for transactions) |
| dates | ISO date; ranges end ≥ start |
| ids in body | UUID; referenced records must exist **in the active workspace** (otherwise 400 `REFERENCE_NOT_FOUND` naming the field) |
| arrays | max sizes: tags 20, team members 50, ids in batch 100, procedure actions 50, milestone steps 30, board columns 20 |
| metadata (contact) | ≤ 50 keys, key `^[a-zA-Z0-9_]{1,40}$`, scalar values (string ≤ 500, number, boolean) |
| working hours | 7 weekday keys, `HH:mm`, close > open unless closed |
| sort param | only whitelisted fields; otherwise 400 `INVALID_SORT` |
| q (search) | ≤ 100 characters; LIKE wildcards escaped |
| page/size | page ≥ 0, 1 ≤ size ≤ 100 |

## 4. Password policy
10–128 characters, at least one letter and one digit, not equal to the email local part, not in the common-password list. Error codes: `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_WEAK`, `PASSWORD_COMMON`.

## 5. Uploads
- IMAGE: jpeg, png or webp (sniffed), ≤ 5 MB, ≤ 40 megapixels. Re-encoded to WebP quality 80, long edge ≤ 2048 px, EXIF stripped.
- DOCUMENT: pdf, doc, docx, xls, xlsx, ppt, pptx (sniffed + extension match), ≤ 10 MB.
- Up to 10 files per request. Filenames are kept only as display names (sanitized). The storage key is generated.

## 6. Logging of errors
- 4xx → DEBUG (WARN for 401/403 bursts, which feed security metrics). 5xx → ERROR with the stack trace.
- Request bodies are never logged.
