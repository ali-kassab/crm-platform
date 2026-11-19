# Edge Cases

| ID | Area | Case | Expected behavior |
|---|---|---|---|
| EC-01 | Tenancy | A valid id from another workspace is used in a path or body | 404 (path) / 400 REFERENCE_NOT_FOUND (body); nothing revealed |
| EC-02 | Auth | Two tabs refresh at the same time with the same refresh token | The client single-flights. If both reach the server, the second one sees the token as rotated: within a **10 s grace window** the server returns the same new pair (idempotent), after that it is treated as reuse |
| EC-03 | Auth | A user is removed from the active workspace while logged in | The next request fails with 403 NO_WORKSPACE on permission reload. The SPA sends them to the workspace switcher |
| EC-04 | Auth | A user is demoted from Admin to Member mid-session | Effective within the permission cache TTL (evicted immediately on change) |
| EC-05 | Members | Removing or demoting the last Owner | 409 LAST_OWNER |
| EC-06 | Invites | An invite is accepted by a different logged-in user | 401; the email must match |
| EC-07 | Contacts | Email differs only by case from an existing contact | 409 EMAIL_TAKEN (case-insensitive) |
| EC-08 | Contacts | The geocoding service is down | Saved without coordinates; warning GEOCODING_FAILED |
| EC-09 | Contacts | Batch delete with a mix of valid, unknown and blocked ids | 200 with a per-id outcome; valid ones deleted |
| EC-10 | Contacts | A tag is added and removed in the same PATCH | Net diff only (no run started) |
| EC-11 | Ref data | A tag is deleted while entities use it | Removed from those entities; runs it started are cancelled |
| EC-12 | Projects | Project created with no board templates in the workspace | Built-in fallback columns (To Do, In Progress, Done) |
| EC-13 | Projects | Creation fails halfway (e.g. a team member id is invalid) | Nothing is persisted (single transaction) |
| EC-14 | Projects | Manager changed to someone who is currently a team member | The member is promoted to manager and removed from the team list (no duplicate) |
| EC-15 | Projects | Budget lowered below the sum of the job budgets | Allowed; remainingBudget < 0 → overBudget = true |
| EC-16 | Boards | Two users move the same task at the same time | The first wins. The second gets 409 BOARD_CONFLICT with the current board and its UI re-renders |
| EC-17 | Boards | Move to a position beyond the column end | Clamped to the end |
| EC-18 | Boards | Delete the default column | Allowed only with moveTasksTo (if not empty). The target column becomes the default if no default remains |
| EC-19 | Boards | The last remaining column is deleted | 409 (a board needs ≥ 1 column) |
| EC-20 | Tasks | Task re-parented from a project to a job | Moved to the job board's default column, at the end |
| EC-21 | Tasks | Status set to COMPLETED then back to IN_PROGRESS | completedAt set, then cleared |
| EC-22 | Tasks | An assignee isn't on the project team | 400 ASSIGNEE_NOT_IN_TEAM |
| EC-23 | Jobs | Job deleted | Its budget no longer counts toward the project's remaining budget. Its tasks and board are soft-deleted. Its transactions remain and still appear in the project rollup |
| EC-24 | Finance | Payment received exceeds the contract | Allowed; remaining negative, overpaid = true |
| EC-25 | Finance | Void an already voided transaction | 409 ALREADY_VOIDED |
| EC-26 | Finance | Transaction contact omitted | Defaults to the project's contact |
| EC-27 | Automation | A procedure adds tag B whose procedure adds tag A (loop) | Stops at depth 5; an audit warning is written |
| EC-28 | Automation | The same tag is re-added while a run is active | No duplicate run |
| EC-29 | Automation | The entity is deleted mid-run | Run CANCELLED, remaining steps SKIPPED |
| EC-30 | Automation | Two app instances run the scheduler | ShedLock plus SKIP LOCKED: each step executes once |
| EC-31 | Automation | Instance crashes after sending an email but before marking the step | On retry the idempotency key prevents a duplicate send if the port supports it. Otherwise at-least-once is documented |
| EC-32 | Automation | WAIT_UNTIL date is in the past when reached | Continue immediately |
| EC-33 | Automation | A procedure is edited while runs are active | Active runs keep their own copy of the steps |
| EC-34 | Automation | A placeholder resolves to empty for an email recipient | Step FAILED with INVALID_RECIPIENT |
| EC-35 | Files | An uploaded file is never attached | Deleted after 24 h |
| EC-36 | Files | A PNG renamed to .pdf, uploaded as DOCUMENT | 415 (content sniffing) |
| EC-37 | Files | A file referenced by two entities (e.g. reused image) | Not allowed. A file attaches to one owner (409 FILE_IN_USE) |
| EC-38 | Warranties | End date passes | Status flips to EXPIRED by the daily job; a CANCELLED warranty stays CANCELLED |
| EC-39 | Lists | size=1000 | 400 VALIDATION_ERROR (max 100) |
| EC-40 | Search | `q` contains `%`, `_` or regex characters | Treated literally |
| EC-41 | Redis down | Login during an outage | Works; the rate limit fails open, lockout counting is in-memory per instance, a warning is logged |
| EC-42 | WebSocket | A client misses events while disconnected | On reconnect it refetches the board (version gap) |
| EC-43 | Time | Workspace time zone vs UTC | Stored as UTC instants; calendar dates are dates; automation offsets are computed in the workspace time zone |
