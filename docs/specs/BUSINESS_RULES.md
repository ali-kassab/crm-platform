# Business Rules

Labels: **[E]** existing intended behavior · **[C]** intentional change from the legacy behavior (see COMPATIBILITY_DIFFERENCES) · **[N]** new feature.
All rules apply within the active workspace.

## General
| ID | Rule |
|---|---|
| BR-001 [E] | Every business record belongs to exactly one workspace and is visible only inside it. **[C]** This is now enforced everywhere. |
| BR-002 [E] | Deleting is **soft** (the record is hidden, but kept for audit and history) unless a rule says otherwise. |
| BR-003 [C] | A name that must be unique (category, status, tag, procedure, board template) is unique **per workspace**, case-insensitive, among non-deleted records. |
| BR-004 [C] | Partial update (PATCH) changes only the fields present in the request. A field sent as `null` is cleared (if it is nullable). Zero, false and empty values are honored. |
| BR-005 [E] | Records track who created and last updated them, and when. |
| BR-006 [C] | Every create, update and delete of a business record writes an audit entry (see FR-AUD). |

## Reference data (categories, statuses, tags)
| ID | Rule |
|---|---|
| BR-010 [E] | A **status** and a **tag** each belong to one category. |
| BR-011 [E] | Status name is 3–25 characters. Category and tag names are 1–50 characters. |
| BR-012 [E] | Statuses and tags have display colors (background default `#000000`, text default `#FFFFFF`, as `#RRGGBB`) and an icon name (default `check-check`). |
| BR-013 [E] | A tag or status may reference a **start procedure** and/or a **stop procedure**. They must be different. |
| BR-014 [E] | A procedure can be the start procedure of at most **one** tag or status. |
| BR-015 [C] | A category that is still used by a status or tag can't be deleted (409 `IN_USE`). Deleting a tag or status removes it from the entities that use it and stops their automation runs. |
| BR-016 [E] | New workspaces are seeded with categories Client, Lead and Partner, and statuses "New Client", "New Lead" and "New Partner". |

## Contacts & locations
| ID | Rule |
|---|---|
| BR-020 [E] | Contact type is one of CLIENT, PROSPECT, PARTNER, LEAD (required). |
| BR-021 [E] | A contact has a name (title, first, middle, last; first and last required), an optional company, industry, position, address, image, category, status, tags, representative (a workspace member), "contract signed" flag, active flag (default true), and free-form metadata (key/value). |
| BR-022 [C] | Contact email is required and unique per workspace (case-insensitive). Phone numbers are optional and valid E.164 (formatted for display). |
| BR-023 [E] | When an address is set or changed, coordinates are looked up through the geocoding service. **[C]** A geocoding failure doesn't block saving: the coordinates are left empty and a warning is returned. Coordinates can also be entered manually. |
| BR-024 [E] | Deactivating a workspace member clears them as representative on all contacts. |
| BR-025 [E] | A location belongs to one contact (the owner). Type is one of residential, commercial, industrial, retail, warehouse, office. Name is required. |
| BR-026 [E] | A location has weekly working hours per weekday (open, close, closed). The default is Mon–Fri 09:00–17:00 with Saturday and Sunday closed. Close must be after open when not closed. |
| BR-027 [E] | A location has access instructions (≤ 2000) and a parking-available flag. Its job count is derived from the jobs of its projects. |
| BR-028 [C] | A contact or location that still has active projects can't be deleted (409 `HAS_PROJECTS`). |

## Notes
| ID | Rule |
|---|---|
| BR-030 [C] | A note belongs to exactly one parent: contact, project or job. |
| BR-031 [C] | Title is 3–100 characters, body is 1–5000 characters. An optional attached document. |
| BR-032 [E] | Replacing or removing a note's document deletes the old stored file. |

## Milestones
| ID | Rule |
|---|---|
| BR-040 [E] | A milestone has a name, an optional description, an optional category and an ordered list of **steps**. Each step has: label, order, style (icon, colors), view mode (icon or image) with an optional image, optional start and end dates, completed flag, completedAt, completedBy. |
| BR-041 [E] | A milestone **template** (reusable) needs ≥ 2 steps. A milestone attached to an entity needs ≥ 1 step. Step orders are unique within the milestone. |
| BR-042 [E] | `currentStep` is a 0-based index within the step list. Completing a step sets completedAt and completedBy and advances `currentStep` to the next incomplete step. |
| BR-043 [E] | Progress % = completed steps ÷ total steps, rounded. |
| BR-044 [E] | A contact, location, project, job or task may each have at most one milestone. If a contact milestone is created without steps, two default steps are used: "Initial Contact" and "Contract Signed". |

## Projects & team
| ID | Rule |
|---|---|
| BR-050 [E] | A project needs: name, description, type (commercial, residential, industrial, institutional, infrastructure; default commercial), address (city, state/region, postal code, country required), total area ≥ 0, budget ≥ 0, start date, estimated end date ≥ start date, location, project manager. |
| BR-051 [E] | The project's customer contact is the owner of its location. |
| BR-052 [E] | The project manager and team members are workspace members, and each person appears at most once per project. The manager can't also be listed as a team member. |
| BR-053 [E] | A team member has a position (default "Team Member", or "Project Manager" for the manager) and responsibilities from the fixed list: project_planning, team_management, resource_allocation, reporting. |
| BR-054 [E] | Creating a project also creates its board from the chosen board template, or from the workspace's default template, or from a built-in fallback (To Do, In Progress, Done) if neither exists. **[C]** It no longer fails when no default exists. |
| BR-055 [E] | `remainingBudget` = project budget − Σ budgets of its active jobs. **[C]** This is always derived, so it stays correct when budgets change or jobs are deleted. Over budget means remaining < 0 (allowed, and flagged). |
| BR-056 [C] | Deleting a project soft-deletes its jobs, tasks, board, notes and warranties. Transactions are kept. |
| BR-057 [N] | Adding or removing project members, and changing the manager, notifies the affected users and the project manager in-app. |

## Jobs & warranties
| ID | Rule |
|---|---|
| BR-060 [E] | A job belongs to one project and needs: title, description, start date, estimated end date ≥ start, budget ≥ 0. Optional: team manager, team members (each must be a member of the project team or is added to it), tags, media, documents, milestone. |
| BR-061 [E] | The team manager can't also be a team member of the job. |
| BR-062 [E] | Creating a job creates its own board (same template rules as BR-054). |
| BR-063 [E] | Removing media or documents from a job deletes the stored files. |
| BR-064 [E] | A warranty needs a title (3–100), provider, start date and end date. **[C]** The end date must be ≥ the start date. It links optionally to a project and/or a job (the job must belong to the project if both are given). Status is ACTIVE, EXPIRED or CANCELLED. |
| BR-065 [N] | A daily job marks ACTIVE warranties whose end date has passed as EXPIRED. |

## Boards & tasks
| ID | Rule |
|---|---|
| BR-070 [E] | Each project and each job has exactly one board. A board has ordered columns (title 1–30, one of them may be the default column). |
| BR-071 [E] | Column positions are contiguous, 1..n. Reordering a column renumbers all columns. |
| BR-072 [C] | A column that still contains tasks can't be deleted unless a `moveTasksTo` column is given. Its tasks are then appended to that column. |
| BR-073 [E] | A task belongs to exactly one of a project or a job, and lives in one column of that parent's board. |
| BR-074 [E] | A task needs: title, description, start date, end date ≥ start date. Priority is LOW, MEDIUM (default) or HIGH. Status is PENDING (default), IN_PROGRESS, ON_HOLD, COMPLETED or CANCELLED. Optional: tags, assignees (team members of the parent project), milestone. |
| BR-075 [E] | A new task is placed at the end of the requested column, or of the default column, or of the first column. If the board has no columns, a "Backlog" default column is created. |
| BR-076 [E] | Moving a task (same column or another column) to position p shifts the other tasks so that positions stay contiguous 1..n in both columns. |
| BR-077 [C] | Moves use optimistic concurrency. A stale board version → 409 with the current board state. |
| BR-078 [C] | Setting status COMPLETED sets `completedAt`. Leaving COMPLETED clears it. |
| BR-079 [E] | Task comments (notes) have an author, text (1–2000) and an optional image. |
| BR-080 [E] | Board templates belong to a workspace and have a title (≤ 100), a description (≤ 500) and ordered columns. At most one template per workspace is the default. Marking a template as default un-marks the previous one. |

## Flowcharts
| ID | Rule |
|---|---|
| BR-085 [E] | A flowchart has a title (3–100), a description (≤ 500), an optional thumbnail image, a favorite flag, tags, an optional project and an owner. |
| BR-086 [N] | **NF-01:** the diagram itself (nodes and edges) is stored as JSON (≤ 1 MB) and edited in the app. |

## Finance
| ID | Rule |
|---|---|
| BR-090 [E] | A transaction records money RECEIVED (from the customer) or PAID (a cost). Amount > 0 with 2 decimals. **[C]** Amount 0 is not allowed. |
| BR-091 [E] | A transaction references a job **and** its project, plus a contact (defaults to the project's customer contact). **[C]** The job must belong to the project. |
| BR-092 [E] | Required: title (1–255), payee (1–255), method (CASH, CHECK, BANK_TRANSFER, CREDIT_CARD, OTHER), commitment date, due date ≥ commitment date. Bank name is required for CHECK, BANK_TRANSFER and CREDIT_CARD. Check number is required for CHECK. Optional: document notes (≤ 1000), document number (≤ 100), document date, attachment. |
| BR-093 [E] | Transactions are immutable once created. **[N] NF-03:** a transaction can be **voided** with a reason (≥ 5 characters). Voided transactions are excluded from the rollups. |
| BR-094 [E] | Rollups for a job, project or contact: **contract** (job budget / project budget / Σ budgets of the contact's projects), **received** (Σ RECEIVED), **actualCost** (Σ PAID), **remaining** = contract − received, **profitLoss** = contract − actualCost. **[C]** Remaining may be negative (overpaid). |

## Automation
| ID | Rule |
|---|---|
| BR-100 [E] | A procedure has a name (3–60, unique), a description (3–200), an optional category, a layout direction (HORIZONTAL or VERTICAL), a visual flow graph (JSON) and ≥ 1 ordered action. |
| BR-101 [E] | Adding a tag (or **[N] NF-02** setting a status) to a contact, project or job that has a start procedure starts a run of that procedure for that entity. If one is already active for the same (entity, procedure), no new run is created. |
| BR-102 [E] | Adding a tag or status with a stop procedure cancels the entity's active runs of that procedure. Removing a tag (or changing away from a status) cancels the runs it started. |
| BR-103 [E] | A run executes its steps strictly in order. A step that waits (delay or timer) blocks the following steps. |
| BR-104 [C] | A DELAY waits relative to when the **previous step finished** (or the run started, for the first step). |
| BR-105 [E] | A TIMER waits until a date taken from the entity (e.g. the project's estimated end date) plus or minus an offset. |
| BR-106 [E] | A failed step records its error, and the run **continues** with the next step. The run ends as COMPLETED (all succeeded) or COMPLETED_WITH_ERRORS. |
| BR-107 [C] | Automation-caused changes may trigger further procedures up to a depth of 5. Deeper triggers are skipped and logged. |
| BR-108 [E] | Finished runs older than 30 days are purged. **[C]** Previously 7 days for successful runs and an unclear rule for failed ones. |
| BR-109 [E] | Templated text `{{path}}` in action parameters is replaced with entity values. Dates render in the workspace's locale. A missing value renders as an empty string and is reported in the step result. |

## Notifications & audit
| ID | Rule |
|---|---|
| BR-110 [N] | Users get in-app notifications for: being added to or removed from a project, becoming project manager, project deletion, and assignment to a task. |
| BR-111 [E] | The audit log is append-only. Entries hold the actor, action, entity type and id, a related project/job, and a snapshot of the changed fields. |
