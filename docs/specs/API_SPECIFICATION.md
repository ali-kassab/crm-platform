# API Specification (REST, `/api/v1`)

The source of truth is `openapi.json` (an OpenAPI 3.1 draft in this folder with 164 operations; the implementation generates it with springdoc and CI diffs it). This document lists the conventions and every operation.

## 1. Conventions
- JSON, camelCase. Dates are ISO-8601 (`date` for calendar dates, `date-time` in UTC for instants). Money is a decimal string with 2 places (e.g. `"1250.00"`). Ids are UUIDs.
- Auth: `Authorization: Bearer <accessToken>` everywhere except the public endpoints marked 🔓.
- Errors: RFC 9457 Problem Details (see VALIDATION_AND_ERROR_HANDLING).
- Lists: `?page=0&size=20&sort=createdAt,desc&q=…` plus endpoint filters. Response:
  `{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }`
- Create → **201** + `Location` header + body. Update → **200** + body. Delete → **204**.
- Concurrency: mutable resources expose `version`. Clients send `If-Match: "<version>"` on PATCH/PUT/DELETE (optional, but **required** for task move and diagram save). A mismatch → **412** `STALE_VERSION`. A board conflict on move → **409** with the current board.
- Request DTOs never accept `id`, `workspaceId`, `createdBy`, `createdAt`, `version` (the last one only in the header).
- Rate-limited endpoints return **429** with `Retry-After`.

## 2. Operations

Permission column: `—` = any authenticated member. 🔓 = public.

### Auth & identity
| Method | Path | Perm | Request → Response |
|---|---|---|---|
| POST | /auth/register | 🔓 | RegisterRequest → 201 AuthResponse |
| POST | /auth/login | 🔓 | LoginRequest → 200 AuthResponse |
| POST | /auth/refresh | 🔓 cookie | — → 200 AuthResponse |
| POST | /auth/logout | 🔓 cookie | — → 204 |
| POST | /auth/logout-all | — | — → 204 |
| POST | /auth/switch-workspace | — | {workspaceId} → 200 AuthResponse |
| POST | /auth/password/forgot | 🔓 | {email} → 202 |
| POST | /auth/password/reset | 🔓 | {token, newPassword} → 204 |
| POST | /auth/password/change | — | {currentPassword, newPassword} → 204 |
| GET | /auth/invitations/{token} | 🔓 | → 200 InvitationPreview |
| POST | /auth/invitations/accept | 🔓 (Bearer if existing user) | AcceptInvitationRequest → 200 AuthResponse |
| GET | /me | — | → 200 MeResponse (profile + memberships) |
| PATCH | /me | — | UpdateProfileRequest → 200 |
| GET | /workspace | — | → 200 Workspace |
| PATCH | /workspace | workspace:manage | {name} → 200 |
| DELETE | /workspace | workspace:delete | {confirmName} → 204 |
| GET | /workspace/members | — | list MemberResponse (filter role, active) |
| PATCH | /workspace/members/{userId} | members:manage | {role?, active?} → 200 |
| DELETE | /workspace/members/{userId} | members:manage | → 204 |
| GET | /workspace/invitations | members:manage | list |
| POST | /workspace/invitations | members:manage | {email, role} → 201 |
| DELETE | /workspace/invitations/{id} | members:manage | → 204 |
| GET | /admin/stats | super-admin | → 200 PlatformStats |

### Reference data
| Method | Path | Perm |
|---|---|---|
| GET | /categories · /categories/{id} | refdata:read |
| POST · PATCH · DELETE | /categories · /categories/{id} | refdata:manage |
| GET | /statuses (filter categoryId) · /statuses/{id} | refdata:read |
| POST · PATCH · DELETE | /statuses… | refdata:manage |
| GET | /tags (filter categoryId) · /tags/{id} | refdata:read |
| POST · PATCH · DELETE | /tags… | refdata:manage |

Status/Tag body: `{name, categoryId, description?, backgroundColor?, textColor?, icon?, startProcedureId?, stopProcedureId?}`.

### Contacts, locations, notes
| Method | Path | Perm | Notes |
|---|---|---|---|
| GET | /contacts | crm:read | filters: type, statusId, categoryId, tagId, representativeId, active, q |
| POST | /contacts | crm:write | ContactRequest → 201 ContactResponse (+ `warnings[]`, e.g. GEOCODING_FAILED) |
| GET | /contacts/{id} | crm:read | ContactDetail (tags, status, category, locations summary, milestone) |
| PATCH | /contacts/{id} | crm:write | partial |
| DELETE | /contacts/{id} | crm:delete | 409 HAS_PROJECTS |
| POST | /contacts/batch-delete | crm:delete | {ids[≤100]} → 200 {deleted[], notFound[], failed[{id, code}]} |
| GET | /contacts/{id}/accounting | finance:read | AccountingSummary |
| GET | /locations | crm:read | filters: contactId, type, city |
| POST · GET · PATCH · DELETE | /locations · /locations/{id} | crm:write / read / write / delete | |
| GET | /notes | crm:read | **required** one of contactId, projectId, jobId |
| POST · GET · PATCH · DELETE | /notes · /notes/{id} | crm:write / read / author / author or Admin | |

### Milestones
| Method | Path | Perm |
|---|---|---|
| GET · PUT · DELETE | /{contacts\|locations\|projects\|jobs\|tasks}/{id}/milestone | read / write of the owning module |
| POST | …/milestone/steps/{stepId}/complete · …/reopen | write |
| GET · POST · GET/{id} · PATCH · DELETE | /milestone-templates | work:read / templates:manage |

### Projects & team
| Method | Path | Perm | Notes |
|---|---|---|---|
| GET | /projects | work:read | filters: type, tagId, locationId, contactId, startFrom, startTo, overBudget, q |
| POST | /projects | work:write | ProjectCreateRequest {…, locationId, projectManagerUserId, teamMemberUserIds[], boardTemplateId?, milestone?, documentFileIds[], tagIds[]} |
| GET · PATCH · DELETE | /projects/{id} | work:read / work:write / work:delete | |
| GET | /projects/{id}/accounting | finance:read | |
| GET | /projects/{id}/board | work:read | BoardResponse |
| GET · POST | /projects/{id}/team-members | work:read / work:write | {userId, position?, responsibilities[]} |
| PATCH · DELETE | /team-members/{id} | work:write | can't delete the manager |
| GET | /team-members/responsibilities | work:read | |
| POST | /team-members/{id}/notes | work:write | {text} |
| DELETE | /team-members/{id}/notes/{noteId} | author or Admin | |

### Boards & tasks
| Method | Path | Perm | Notes |
|---|---|---|---|
| GET | /boards/{id} | work:read | columns (position-ordered) with task cards; `version` |
| POST | /boards/{id}/columns | work:write | {title, isDefault?} |
| PATCH | /boards/{id}/columns/{columnId} | work:write | {title?, isDefault?} |
| POST | /boards/{id}/columns/{columnId}/move | work:write | {position} |
| DELETE | /boards/{id}/columns/{columnId}?moveTasksTo= | work:write | 409 COLUMN_NOT_EMPTY |
| GET | /tasks | work:read | filters: projectId, jobId, status, priority, assigneeId, dueFrom, dueTo, overdue |
| POST | /tasks | work:write | {title, description, startDate, endDate, priority?, status?, projectId \| jobId, columnId?, tagIds[], assigneeTeamMemberIds[], milestone?} |
| GET · PATCH · DELETE | /tasks/{id} | work:read / write / write | |
| POST | /tasks/{id}/move | work:write | {columnId, position, boardVersion} → 200 BoardResponse / 409 |
| GET · POST | /tasks/{id}/comments | work:read / write | {text, imageFileId?} |
| PATCH · DELETE | /tasks/{id}/comments/{commentId} | author / author or Admin | |
| GET · POST · GET · PATCH · DELETE | /board-templates… | work:read / templates:manage | |

### Flowcharts
| Method | Path | Perm |
|---|---|---|
| GET (filter projectId, favorite, mine) · POST | /flowcharts | work:read / work:write |
| GET · PATCH · DELETE | /flowcharts/{id} | read / owner or Admin |
| GET · PUT | /flowcharts/{id}/diagram | read / owner or Admin (If-Match required) |

### Jobs & warranties
| Method | Path | Perm |
|---|---|---|
| GET (filters projectId, tagId, startFrom/To) · POST | /jobs | ops:read / ops:write |
| GET · PATCH · DELETE | /jobs/{id} | ops:read / write / delete |
| GET | /jobs/{id}/board · /jobs/{id}/accounting | work:read · finance:read |
| POST | /jobs/{id}/files | ops:write ({fileIds[]}) |
| DELETE | /jobs/{id}/files/{fileId} | ops:write |
| GET (filters projectId, jobId, status, provider, endingBefore) · POST | /warranties | ops:read / ops:write |
| GET · PATCH · DELETE | /warranties/{id} | ops:read / write / delete |

### Finance
| Method | Path | Perm |
|---|---|---|
| GET (filters type, method, jobId, projectId, contactId, from, to, includeVoided) | /transactions | finance:read |
| POST | /transactions | finance:write |
| GET | /transactions/{id} | finance:read |
| POST | /transactions/{id}/void | finance:write ({reason}) |

### Automation
| Method | Path | Perm |
|---|---|---|
| GET · POST | /procedures | automation:read / manage |
| GET · PUT · DELETE | /procedures/{id} | automation:read / manage / manage (409 PROCEDURE_IN_USE when bound to a tag/status, unless `?force=true` unbinds) |
| GET | /automation/commands | automation:read |
| GET (filters entityType, entityId, procedureId, status) | /automation/runs | automation:read |
| GET | /automation/runs/{id} | automation:read |
| POST | /automation/runs/{id}/cancel | automation:manage |

### Files
| Method | Path | Perm |
|---|---|---|
| POST (multipart `files[]`, `kind=IMAGE\|DOCUMENT`) | /files | files:upload → 201 {files:[StoredFile]} |
| GET | /files/{id} · /files/{id}/url | — (tenant) |
| DELETE | /files/{id} | uploader or Admin |

### Audit, reports, notifications
| Method | Path | Perm |
|---|---|---|
| GET | /audit-entries (filters entityType, entityId, actorId, action, projectId, from, to) · /{id} | audit:read |
| GET | /reports/dashboard?period=ALL\|WEEK\|MONTH\|QUARTER\|YEAR | reports:read |
| GET | /notifications?unreadOnly= · /notifications/unread-count | — |
| POST | /notifications/{id}/read · /notifications/read-all | — |

### Operational (no `/api/v1` prefix)
`GET /actuator/health/liveness`, `/actuator/health/readiness` 🔓 · `/actuator/prometheus` (management port, not public) · `/swagger-ui.html`, `/v3/api-docs` (enabled in dev and staging).

## 3. Representative payloads

```json
// POST /api/v1/contacts
{
  "type": "CLIENT",
  "name": { "title": "Mr", "first": "Sam", "middle": null, "last": "Rivera" },
  "company": "Rivera Builders", "industry": "Construction", "position": "Owner",
  "email": "sam@rivera.example", "phone": "+15125550101",
  "address": { "line1": "10 Main St", "city": "Austin", "region": "TX", "postalCode": "78701", "country": "US" },
  "categoryId": "…", "statusId": "…", "tagIds": ["…"], "representativeUserId": "…",
  "metadata": { "source": "referral" }
}
```

```json
// GET /api/v1/projects/{id}/accounting
{ "contract": "250000.00", "received": "120000.00", "actualCost": "90500.00",
  "remaining": "130000.00", "profitLoss": "159500.00", "transactionCount": 14, "overpaid": false }
```

```json
// POST /api/v1/tasks/{id}/move
{ "columnId": "…", "position": 3, "boardVersion": 41 }
```
