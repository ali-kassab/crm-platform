# Test Scenarios (Given / When / Then)

Level: **U** unit · **I** integration (Testcontainers) · **A** API (MockMvc / RestAssured against the full context) · **E** E2E (Playwright).
Every scenario implicitly also asserts the **tenant isolation** and **audit** side effects where relevant.

## Identity
| ID | Lvl | Scenario |
|---|---|---|
| TS-IAM-01 | A | Given a new email, when registering with a valid payload, then 201 with an access token and refresh cookie; workspace, Owner membership and seed categories/statuses/board template exist |
| TS-IAM-02 | A | Given an existing email (different case), when registering, then 409 EMAIL_TAKEN |
| TS-IAM-03 | A | Weak or common password → 400 with codes PASSWORD_TOO_WEAK / PASSWORD_COMMON |
| TS-IAM-04 | I | 6 registrations per hour from the same IP → the 6th gets 429 |
| TS-IAM-05 | A | Correct credentials → 200 and the token claims include `wid` and `role` |
| TS-IAM-06 | A | Wrong password and unknown email → both 401 INVALID_CREDENTIALS with the same body |
| TS-IAM-07 | I | 5 wrong passwords → the 6th attempt returns 423 even with the correct password; works again after 15 min (Clock) |
| TS-IAM-08 | A | Disabled user → 401 |
| TS-IAM-09 | A | User with no memberships → 403 NO_WORKSPACE |
| TS-IAM-10 | A | Refresh with a valid cookie → new access token + rotated cookie; the old token is marked rotated |
| TS-IAM-11 | A | Reuse a rotated refresh token after the grace window → 401 REFRESH_REUSED and the whole family is revoked |
| TS-IAM-12 | A | Expired access token on an API call → 401 TOKEN_EXPIRED |
| TS-IAM-13 | A | Switch to a workspace the user belongs to → new `wid`; a foreign workspace → 404 |
| TS-IAM-14 | A | Logout → 204, the cookie is cleared, and the refresh fails afterwards |
| TS-IAM-15 | I | Logout-all → all families revoked; the current access `jti` is denylisted (401 on the next call) |
| TS-IAM-16 | I | Forgot password for an existing email → 202 and an email captured in Mailpit/GreenMail containing the reset link |
| TS-IAM-17 | A | Forgot password for an unknown email → 202 and no email |
| TS-IAM-18 | A | Reset with a valid token → 204; reusing the token → 400; refresh tokens revoked |
| TS-IAM-19 | A | Change password with a wrong current password → 400; success → other sessions revoked |
| TS-IAM-20 | A | PATCH /me updates the names; email isn't changeable |
| TS-IAM-21 | I | An Admin invites a new email → 201 and an email is sent |
| TS-IAM-22 | A | Invite an existing member → 409 ALREADY_MEMBER |
| TS-IAM-23 | A | A Member tries to invite → 403 |
| TS-IAM-24 | A | Accept an invite as a new user → user + membership created, tokens issued |
| TS-IAM-25 | A | Accept an invite as an existing user with a matching Bearer token → membership added |
| TS-IAM-26 | A | Expired invite → 404 |
| TS-IAM-27 | A | Change a member's role (Admin→Member) → 200; the member's next request reflects it |
| TS-IAM-28 | A | Demote or remove the last Owner → 409 LAST_OWNER |
| TS-IAM-29 | A | Change your own role → 403 |
| TS-IAM-30 | I | Deactivate a member → contacts they represent have their representative cleared |
| TS-IAM-31 | A | Delete the workspace without the matching confirmName → 400; with it → 204 and members lose access |
| TS-IAM-32 | A | /admin/stats as a non-super-admin → 403 |

## Cross-cutting
| ID | Lvl | Scenario |
|---|---|---|
| TS-SEC-01 | A | **Tenant isolation matrix:** for every resource type, a user of workspace B calling GET/PATCH/DELETE on an id from workspace A → 404, and listing never contains A's rows |
| TS-SEC-02 | A | **Permission matrix:** for every endpoint × role (Owner, Admin, Member, Viewer), the expected 2xx/403 per AUTHZ §2 (parameterized) |
| TS-SEC-03 | A | Unauthenticated calls to every non-public endpoint → 401 |
| TS-SEC-04 | A | Body containing `workspaceId`, `createdBy` or `id` is ignored (or 400 when unknown properties fail) |
| TS-SEC-05 | A | `size=1000` → 400; `sort=passwordHash` → 400 INVALID_SORT |
| TS-SEC-06 | I | STOMP CONNECT without a token → ERROR; SUBSCRIBE to another workspace's board → ERROR FORBIDDEN |
| TS-SEC-07 | A | Security headers present (CSP, X-Content-Type-Options, frame-ancestors) and CORS allows only the configured origin |
| TS-CMN-01 | A | Pagination meta is correct; `q` with `%_` matches literally |
| TS-CMN-02 | A | PATCH with a stale `If-Match` → 412 |

## CRM
| ID | Lvl | Scenario |
|---|---|---|
| TS-CRM-01..03 | A | Category CRUD; duplicate name (case-insensitive) → 409; delete when in use → 409 IN_USE |
| TS-CRM-04..06 | A | Status CRUD; name length 2 → 400; same start/stop procedure → 400; procedure already bound to a tag → 409 |
| TS-CRM-07..09 | A | Tag CRUD with the same rules; deleting a tag removes it from contacts and cancels runs |
| TS-CRM-10 | A | Create a contact with a full payload → 201 and the detail matches |
| TS-CRM-11 | A | Duplicate email (case-insensitive) → 409 |
| TS-CRM-12 | I | Geocoder fails → 201 with warning GEOCODING_FAILED and null coordinates |
| TS-CRM-13 | A | Contact milestone without steps → two default steps created |
| TS-CRM-14,15 | A | Filter by type/status/tag; search by partial name, email, phone |
| TS-CRM-16 | A | PATCH changes only the fields sent; `company: null` clears it |
| TS-CRM-17 | I | Adding a tag with a start procedure creates a run; removing it cancels the run |
| TS-CRM-18 | A | Delete a contact with projects → 409 HAS_PROJECTS |
| TS-CRM-19 | A | Batch delete mixed ids → per-id outcome |
| TS-CRM-20 | I | Replacing the contact image deletes the old stored object |
| TS-CRM-21..24 | A | Location CRUD; owner from another tenant → 400; invalid working hours → 400; default hours applied |
| TS-CRM-25..27 | A | Note needs exactly one parent; only the author edits; author or Admin deletes |

## Work
| ID | Lvl | Scenario |
|---|---|---|
| TS-WRK-01 | A | Create a project → board created from the default template; manager team member created; contact = location owner |
| TS-WRK-02 | A | Manager also in the team list → 400 MANAGER_IN_TEAM |
| TS-WRK-03 | I | No templates at all → fallback columns |
| TS-WRK-04 | I | Failure mid-creation → no rows persisted |
| TS-WRK-05 | A | Filter overBudget=true returns only projects with remaining < 0 |
| TS-WRK-06 | A | remainingBudget = budget − Σ active job budgets |
| TS-WRK-07 | I | Replace the team → removed members notified, added members notified |
| TS-WRK-08 | A | Budget can be set to 0 |
| TS-WRK-09 | I | Delete a project → jobs, tasks, board, notes, warranties soft-deleted; transactions kept |
| TS-WRK-10,11 | A | Add the same user twice → 409; delete the manager team member → 409 |
| TS-WRK-12 | A | Team note deleted by a non-author Member → 403; by an Admin → 204 |
| TS-WRK-13 | A | GET board → columns ordered, tasks ordered, version present |
| TS-WRK-14 | A | Reorder column 3 → 1 → positions 1..n contiguous; event COLUMN_MOVED published |
| TS-WRK-15 | A | Delete a non-empty column without moveTasksTo → 409; with it → tasks appended to the target |
| TS-WRK-16 | A | Create a task without a column → placed at the end of the default column; both parents → 400; unknown column → 400 COLUMN_NOT_ON_BOARD |
| TS-WRK-17 | A | Task status COMPLETED sets completedAt; reverting clears it |
| TS-WRK-18 | U+I | Move within a column and across columns → positions contiguous in both columns (property-based test over random moves) |
| TS-WRK-19 | I | Concurrent moves with the same boardVersion → one 200, one 409 BOARD_CONFLICT |
| TS-WRK-20..22 | A | Set a milestone; complete a step → currentStep advances and progress %; reopen |
| TS-WRK-23 | A | Milestone template with 1 step → 400 |
| TS-WRK-24 | A | Delete a task → removed from the board and positions compacted |
| TS-WRK-25 | A | Comments add/list/delete; WS event emitted |
| TS-WRK-26 | A | Tasks overdue filter = end < today and status not in (COMPLETED, CANCELLED) |
| TS-WRK-27,28 | A | Board template default switching is unique per workspace; templates of another workspace aren't usable |
| TS-WRK-29,30 | A | Flowchart CRUD; a non-owner Member PATCH → 403 |
| TS-WRK-31 | A | Diagram PUT with a stale version → 412; > 1 MB → 413 |
| TS-WRK-E2E | E | Two browsers on the same board: drag a card in A → it moves in B within 1 s |

## Operations, finance
| ID | Lvl | Scenario |
|---|---|---|
| TS-OPS-01..03 | A | Create a job → board created, project remaining reduced; manager in members → 400; end < start → 400 |
| TS-OPS-04 | A | Filter jobs by project |
| TS-OPS-05 | A | Change the job budget → project remaining recomputed |
| TS-OPS-06 | I | Attach files; removing one deletes the object |
| TS-OPS-07 | A | Delete a job → remaining budget restored; tasks hidden |
| TS-OPS-08..10 | A | Warranty CRUD; end < start → 400; job not in project → 400 |
| TS-OPS-11 | I | Expiry job flips only ACTIVE warranties past their end date |
| TS-FIN-01 | A | Create RECEIVED by check without checkNumber → 400 |
| TS-FIN-02 | A | Due before commitment → 400 |
| TS-FIN-03 | A | Job not in project → 400 JOB_NOT_IN_PROJECT |
| TS-FIN-04 | A | Contact omitted → the project's contact is used |
| TS-FIN-05 | A | Amount 0 → 400; amount with 3 decimals → 400 |
| TS-FIN-06 | A | List filters by date range and type |
| TS-FIN-07 | A | Void → excluded from rollups; void again → 409 |
| TS-FIN-08..10 | I | Rollups for job, project, contact match hand-computed values, including overpayment (remaining < 0, overpaid=true) |

## Automation
| ID | Lvl | Scenario |
|---|---|---|
| TS-AUT-01..03 | A | Procedure with an unknown command → 400; bad params → 400 listing the fields; delete while bound → 409 |
| TS-AUT-04 | A | Command catalog lists 15 commands with JSON schemas |
| TS-AUT-05 | I | Tag added → run created with steps copied |
| TS-AUT-06 | I | Same tag re-added while running → no duplicate run |
| TS-AUT-07 | I | Stop tag added → run CANCELLED, steps SKIPPED |
| TS-AUT-08 | I | Status change starts and stops runs (NF-02) |
| TS-AUT-09 | U | DELAY computes dueAt from the previous step's finish time (fixed Clock) |
| TS-AUT-10 | U | WAIT_UNTIL before the end date by 3 days; date in the past → immediate |
| TS-AUT-11 | I | SEND_EMAIL with `sendAt` in the future blocks the following steps until due |
| TS-AUT-12 | I | SEND_EMAIL resolves `{{contact.email}}` and `{{teamMembers.emails}}`; the email appears in Mailpit |
| TS-AUT-13 | U | Templating: missing field → empty string + missingFields reported |
| TS-AUT-14 | I | Loop A→B→A stops at depth 5 |
| TS-AUT-15 | I | Two scheduler instances (two contexts / threads) → each step executed exactly once |
| TS-AUT-16 | U | Each handler: success and failure paths (CREATE_TASK, CREATE_JOB, CREATE_TRANSACTION, ASSIGN_REPRESENTATIVE, SET_STATUS, milestone commands) |
| TS-AUT-17 | A | Runs list filtered by entity |
| TS-AUT-18 | A | Cancel a finished run → 409 |
| TS-AUT-19 | I | Purge deletes runs finished more than 30 days ago only |

## Files, audit, reports, notifications, ops
| ID | Lvl | Scenario |
|---|---|---|
| TS-FIL-01 | I | Upload a PNG → stored as WebP, EXIF removed, size recorded |
| TS-FIL-02 | A | 11 MB document → 413 |
| TS-FIL-03 | A | `.exe` renamed to `.pdf` → 415 |
| TS-FIL-04 | I | Key format `{workspaceId}/{kind}/{uuid}` |
| TS-FIL-05 | I | Presigned URL downloads within 5 min; another tenant's file id → 404 |
| TS-FIL-06 | A | Delete an attached file → 409 FILE_IN_USE |
| TS-FIL-07 | I | Orphan cleanup removes files unattached for more than 24 h |
| TS-AUD-01 | I | Create/update/delete contact → 3 audit entries with changed fields only |
| TS-AUD-02 | A | Audit entries filterable; a Member → 403 |
| TS-RPT-01 | I | Dashboard counts exclude soft-deleted rows; period MONTH bounds use the workspace time zone |
| TS-RPT-02 | A | Member → 403 |
| TS-NOT-01 | I | Adding a user to a project → notification row + STOMP message to that user |
| TS-NOT-02 | A | Unread count, mark read, read-all |
| TS-OPS-HEALTH | I | Readiness DOWN when the DB is down; DEGRADED (still UP) when Redis is down |
| TS-ARCH-01 | U | Spring Modulith `verify()`: no illegal cross-module dependencies |
| TS-ARCH-02 | U | ArchUnit: controllers don't access repositories; domain doesn't depend on Spring Web |

## Frontend (Vitest + RTL + MSW, Playwright)
| ID | Lvl | Scenario |
|---|---|---|
| TS-FE-01 | U | Login form validation and error mapping from Problem Details |
| TS-FE-02 | U | Refresh interceptor: concurrent 401s trigger one refresh and replay the requests |
| TS-FE-03 | U | Permission-gated buttons hidden for a Viewer |
| TS-FE-04 | E | Sign up → create contact → create location → create project → task appears on the board |
| TS-FE-05 | E | Drag a task across columns persists after reload |
| TS-FE-06 | E | Record a transaction → project finance KPIs update |
| TS-FE-07 | U | axe accessibility checks on Login, Contacts list, Board |
