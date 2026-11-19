# Functional Requirements

Each FR lists: purpose, the endpoint(s) (details in API_SPECIFICATION), rules (BUSINESS_RULES), validation (VALIDATION_AND_ERROR_HANDLING), errors, edge cases (EDGE_CASES), DB effects, external effects and tests (TEST_SCENARIOS).
Origin: **E** existing · **C** existing with an intentional change · **N** new.

Common to every list endpoint (FR-CMN-01):
- pagination `page` (0-based), `size` (default 20, max 100), `sort=field,asc|desc` (whitelisted per endpoint), optional `q` search;
- response `{content[], page, size, totalElements, totalPages}`;
- tenant scoped; soft-deleted records excluded.

Common to every mutation (FR-CMN-02):
- audit entry written;
- `updatedAt` / `updatedBy` set;
- `version` checked when sent (`If-Match` header or a `version` field) → 409 `STALE_VERSION`.

---

## identity (IAM)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-IAM-01 | N | Self-registration creates a user, a workspace, an Owner membership and seed data | POST /auth/register | F1, BR-016 | 409 EMAIL_TAKEN; 429; weak password 400 | users, workspaces, memberships, seed rows | TS-IAM-01..04 |
| FR-IAM-02 | C | Login with email/password into a workspace | POST /auth/login | F2, lockout | 401 same for unknown/wrong; 423 locked; 403 NO_WORKSPACE | refresh_tokens; Redis counters | TS-IAM-05..09 |
| FR-IAM-03 | N | Silent refresh with rotation and reuse detection | POST /auth/refresh | F3 | 401 INVALID_REFRESH / REFRESH_REUSED | refresh_tokens | TS-IAM-10..12 |
| FR-IAM-04 | N | Switch active workspace | POST /auth/switch-workspace | F4 | 404 not a member | refresh_tokens | TS-IAM-13 |
| FR-IAM-05 | C | Logout and logout everywhere | POST /auth/logout, /auth/logout-all | F5 | idempotent | revoke; Redis denylist | TS-IAM-14,15 |
| FR-IAM-06 | N | Forgot and reset password | POST /auth/password/forgot, /reset | F6 | always 202; 400 INVALID_TOKEN | reset tokens; email port | TS-IAM-16..18 |
| FR-IAM-07 | N | Change password | POST /auth/password/change | F7 | 400 INVALID_PASSWORD | revoke others | TS-IAM-19 |
| FR-IAM-08 | E | View and update own profile | GET/PATCH /me | F9 | avatar must be own IMAGE file | users | TS-IAM-20 |
| FR-IAM-09 | N | Invite members, list and revoke invites | /workspace/invitations | F8 | 409 ALREADY_MEMBER; Admin can't invite as Owner | invitations; email port | TS-IAM-21..23 |
| FR-IAM-10 | N | Accept an invite (new or existing user) | GET /auth/invitations/{token}, POST /auth/invitations/accept | F8 | 404 expired; 401 wrong user | users, memberships | TS-IAM-24..26 |
| FR-IAM-11 | C | List members, change role, remove member, deactivate | /workspace/members | AZ-03..05, BR-024 | 409 LAST_OWNER; 403 self-role | memberships; contacts.representative cleared | TS-IAM-27..30 |
| FR-IAM-12 | N | Rename workspace; delete workspace (Owner, soft) | GET/PATCH/DELETE /workspace | — | delete requires confirm=name | workspaces | TS-IAM-31 |
| FR-IAM-13 | N | Platform super-admin cross-tenant counts | GET /admin/stats | — | 403 non-super-admin | read only | TS-IAM-32 |

## crm (CRM)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-CRM-01 | C | Manage categories (CRUD, list with search) | /categories | BR-003, BR-011, BR-015 | 409 NAME_TAKEN, IN_USE | categories; cache evict | TS-CRM-01..03 |
| FR-CRM-02 | C | Manage statuses | /statuses | BR-010..014 | 400 same start/stop procedure; 409 PROCEDURE_ALREADY_BOUND | statuses | TS-CRM-04..06 |
| FR-CRM-03 | C | Manage tags | /tags | BR-010..015 | as above | tags | TS-CRM-07..09 |
| FR-CRM-04 | C | Create contact | POST /contacts | BR-020..023, BR-044 | 409 EMAIL_TAKEN; geocode failure → saved + warning | contacts, contact_tags; geocoder; triggers automation | TS-CRM-10..13 |
| FR-CRM-05 | E | List, search and filter contacts (type, status, category, tag, representative, active) | GET /contacts | FR-CMN-01 | `q` matches name, email, phone, company (escaped) | — | TS-CRM-14,15 |
| FR-CRM-06 | C | Get and update contact (partial) | GET/PATCH /contacts/{id} | BR-004 | tag diff → start/stop runs | contacts | TS-CRM-16,17 |
| FR-CRM-07 | C | Delete one contact or many | DELETE /contacts/{id}, POST /contacts/batch-delete | BR-028 | batch returns per-id results; max 100 ids | soft delete; cancel runs | TS-CRM-18,19 |
| FR-CRM-08 | E | Contact image upload and replace | PATCH /contacts/{id} `imageFileId` | BR-032 pattern | old file deleted | files | TS-CRM-20 |
| FR-CRM-09 | C | Contact financial summary | GET /contacts/{id}/accounting | BR-094 | — | read | TS-FIN-08 |
| FR-CRM-10 | E | Manage locations (CRUD, filter by owner/type/city) | /locations | BR-025..028, BR-023 | owner must be a contact in the workspace | locations; geocoder | TS-CRM-21..24 |
| FR-CRM-11 | C | Manage notes for a contact, project or job | /notes | BR-030..032, AZ-01 | exactly one parent | notes, files | TS-CRM-25..27 |
| FR-CRM-12 | E | Set, clear and complete steps of an entity milestone (contact, location, project, job, task) | PUT/DELETE /{type}/{id}/milestone, POST …/steps/{stepId}/complete, …/reopen | BR-040..044 | 409 already complete | milestones | TS-WRK-20..22 |
| FR-CRM-13 | E | Manage milestone templates | /milestone-templates | BR-041 | ≥ 2 steps | milestone_templates | TS-WRK-23 |

## work (WRK)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-WRK-01 | C | Create project with manager, team, board from template | POST /projects | BR-050..054 | 400 manager in team; 404 location; atomic (all or nothing) | projects, team_members, boards, columns; notifications | TS-WRK-01..04 |
| FR-WRK-02 | E | List, search and filter projects (type, tag, location, contact, date range, overBudget) | GET /projects | FR-CMN-01 | — | — | TS-WRK-05 |
| FR-WRK-03 | E | Project detail (summary of team, jobs, budget, milestone, board id) | GET /projects/{id} | BR-055 | — | — | TS-WRK-06 |
| FR-WRK-04 | C | Update project, including manager change and team replacement | PATCH /projects/{id} | BR-052, BR-057 | notification diff on user ids | team_members; notifications | TS-WRK-07,08 |
| FR-WRK-05 | C | Delete project (cascade soft delete) | DELETE /projects/{id} | BR-056 | — | cascade; notifications; cancel runs | TS-WRK-09 |
| FR-WRK-06 | E | Manage team members of a project (add, update position/responsibilities, remove) | /projects/{id}/team-members, /team-members/{id} | BR-052, BR-053 | 409 already in project; can't remove manager | team_members | TS-WRK-10,11 |
| FR-WRK-07 | E | Team member notes (add, delete by author or admin) | /team-members/{id}/notes | AZ-01 | — | team_member_notes | TS-WRK-12 |
| FR-WRK-08 | E | Responsibilities catalog | GET /team-members/responsibilities | BR-053 | — | static | — |
| FR-WRK-09 | E | Get the board with columns and task cards | GET /boards/{id}, GET /projects/{id}/board, GET /jobs/{id}/board | BR-070 | — | — | TS-WRK-13 |
| FR-WRK-10 | E | Add, rename, set default and reorder columns | /boards/{id}/columns… | BR-071 | title ≤ 30; one default | board_columns; WS event | TS-WRK-14 |
| FR-WRK-11 | C | Delete column with task relocation | DELETE /boards/{id}/columns/{cid}?moveTasksTo= | BR-072 | 409 COLUMN_NOT_EMPTY | tasks moved; WS | TS-WRK-15 |
| FR-WRK-12 | E | Create task | POST /tasks | BR-073..075 | 400 both or neither parent; 404 column not on board | tasks; WS; notification to assignees | TS-WRK-16 |
| FR-WRK-13 | C | Update task (fields, status, assignees, parent change re-homes to the new board's default column) | PATCH /tasks/{id} | BR-074, BR-078 | — | WS | TS-WRK-17 |
| FR-WRK-14 | C | Move or reorder task | POST /tasks/{id}/move | BR-076, BR-077 | 409 STALE_VERSION + board | WS TASK_MOVED | TS-WRK-18,19 |
| FR-WRK-15 | C | Delete task (removed from board) | DELETE /tasks/{id} | — | positions compacted | WS | TS-WRK-24 |
| FR-WRK-16 | E | Task comments | /tasks/{id}/comments | BR-079, AZ-01 | — | task_comments; WS | TS-WRK-25 |
| FR-WRK-17 | E | List and filter tasks (status, priority, assignee, project, job, due range, overdue) | GET /tasks | — | — | — | TS-WRK-26 |
| FR-WRK-18 | E | Manage board templates (single default) | /board-templates | BR-080 | — | board_templates | TS-WRK-27,28 |
| FR-WRK-19 | C | Manage flowcharts (metadata + owner rules) | /flowcharts | BR-085, AZ-02 | 403 not owner | flowcharts | TS-WRK-29,30 |
| FR-WRK-20 | N | NF-01 edit and save the flowchart diagram | GET/PUT /flowcharts/{id}/diagram | BR-086 | 413 > 1 MB; version check | flowcharts.diagram jsonb | TS-WRK-31 |

## operations (OPS)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-OPS-01 | C | Create job with board | POST /jobs | BR-060..062, BR-055 | manager not in team | jobs, boards; audit | TS-OPS-01..03 |
| FR-OPS-02 | E | List, filter and get jobs | GET /jobs, /jobs/{id} | — | — | — | TS-OPS-04 |
| FR-OPS-03 | C | Update job (incl. budget → project remaining recomputed) | PATCH /jobs/{id} | BR-055, BR-063 | removed files deleted | jobs, files | TS-OPS-05 |
| FR-OPS-04 | E | Add media and documents to a job | POST /jobs/{id}/files | BR-063 | kind matches file | job_files | TS-OPS-06 |
| FR-OPS-05 | C | Delete job (cascade tasks and board, budget freed) | DELETE /jobs/{id} | BR-055 | — | cascade | TS-OPS-07 |
| FR-OPS-06 | C | Manage warranties | /warranties | BR-064 | end ≥ start; job ∈ project | warranties, files | TS-OPS-08..10 |
| FR-OPS-07 | N | Daily warranty expiry | scheduler | BR-065 | idempotent | warranties | TS-OPS-11 |

## finance (FIN)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-FIN-01 | C | Record transaction | POST /transactions | BR-090..092 | 400 JOB_NOT_IN_PROJECT; method-specific requirements | transactions; audit | TS-FIN-01..05 |
| FR-FIN-02 | E | List, filter (type, method, job, project, contact, date range, voided) and get | GET /transactions | — | — | — | TS-FIN-06 |
| FR-FIN-03 | N | Void transaction (NF-03) | POST /transactions/{id}/void | BR-093 | 409 ALREADY_VOIDED | transactions | TS-FIN-07 |
| FR-FIN-04 | C | Accounting summary for a job, project or contact | GET /{jobs,projects,contacts}/{id}/accounting | BR-094 | negative remaining allowed | read | TS-FIN-08..10 |

## automation (AUT)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-AUT-01 | C | Manage procedures (CRUD) with action validation per command | /procedures | BR-100, AUTOMATION_SPEC §3 | 400 unknown command or bad params | procedures, actions | TS-AUT-01..03 |
| FR-AUT-02 | E | Command catalog with parameter schemas (for the UI editor) | GET /automation/commands | — | — | static | TS-AUT-04 |
| FR-AUT-03 | C | Start and stop runs on tag changes | event-driven | BR-101, BR-102 | dedupe | runs | TS-AUT-05..07 |
| FR-AUT-04 | N | NF-02 start and stop runs on status changes | event-driven | BR-101, BR-102 | — | runs | TS-AUT-08 |
| FR-AUT-05 | C | Execute steps in order with waits, at least once, idempotently | scheduler | BR-103..107 | multi-instance safe | runs, steps; email/SMS ports | TS-AUT-09..16 |
| FR-AUT-06 | E | View runs (filter by entity, procedure, status) and run detail with step results | GET /automation/runs | — | — | — | TS-AUT-17 |
| FR-AUT-07 | C | Cancel a run manually | POST /automation/runs/{id}/cancel | — | 409 already finished | runs | TS-AUT-18 |
| FR-AUT-08 | C | Purge old finished runs | scheduler daily | BR-108 | — | delete | TS-AUT-19 |

## files (FIL)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-FIL-01 | C | Upload one or many files (IMAGE ≤ 5 MB → WebP; DOCUMENT ≤ 10 MB) | POST /files | ADR-0009 | 415 bad type (sniffed); 413 too large | stored_files; S3 | TS-FIL-01..04 |
| FR-FIL-02 | C | Get a short-lived download URL | GET /files/{id}/url | — | 404 other tenant | presign | TS-FIL-05 |
| FR-FIL-03 | C | Delete an unattached file (uploader or Admin) | DELETE /files/{id} | — | 409 FILE_IN_USE | S3 delete | TS-FIL-06 |
| FR-FIL-04 | N | Orphan cleanup: files never attached within 24 h are deleted | scheduler | — | — | S3 delete | TS-FIL-07 |

## audit (AUD), reporting (RPT), notifications (NOT)

| ID | O | Requirement | Endpoints | Rules | Errors / edge cases | DB / external | Tests |
|---|---|---|---|---|---|---|---|
| FR-AUD-01 | C | Every mutation writes an audit entry (async after commit, same transaction via the outbox) | — | BR-006, BR-111 | — | audit_entries | TS-AUD-01 |
| FR-AUD-02 | E | Browse the audit log (filter by entity, actor, action, project, date) | GET /audit-entries, /{id} | — | — | — | TS-AUD-02 |
| FR-RPT-01 | C | Workspace dashboard for a period (all, week, month, quarter, year): project totals by type (all-time and this period), over-budget count, deadlines in the next 30 days, budget sum/avg, tasks by status and priority, overdue, due within 7 days, avg completion days, top 5 assignees, contacts by type, received vs paid this period | GET /reports/dashboard | — | deleted rows excluded | read | TS-RPT-01,02 |
| FR-NOT-01 | N | Create in-app notifications on events (BR-110) and push them over WebSocket `/user/queue/notifications` | — | BR-110 | — | notifications | TS-NOT-01 |
| FR-NOT-02 | N | List my notifications, unread count, mark one or all as read | /notifications | — | — | — | TS-NOT-02 |
