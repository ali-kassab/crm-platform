# Database Specification (PostgreSQL 17)

Conventions (ADR-0002):
- Every table has `id uuid PK`.
- **Tenant tables** also have `workspace_id uuid NOT NULL FK → workspaces`, `created_at timestamptz NOT NULL`, `created_by uuid`, `updated_at timestamptz NOT NULL`, `updated_by uuid`, and `version bigint NOT NULL DEFAULT 0`.
- **Soft-deletable** tables also have `deleted_at timestamptz`, `deleted_by uuid`.

Every FK is indexed. Enums are stored as `varchar` with a CHECK constraint (easy to evolve via Flyway).

Migration plan:
- `V1__identity.sql`
- `V2__reference_data.sql`
- `V3__crm.sql`
- `V4__work.sql`
- `V5__operations.sql`
- `V6__finance.sql`
- `V7__automation.sql`
- `V8__files_audit_notifications.sql`
- `V9__shedlock_modulith.sql`
- plus repeatable `R__demo_seed.sql`, loaded only by the `demo` profile through Flyway locations.

## identity
| Table | Columns (beyond conventions) | Constraints / indexes |
|---|---|---|
| users | email citext, password_hash, first_name, last_name, job_title, phone, avatar_file_id, active bool, super_admin bool, last_login_at, last_workspace_id | UNIQUE(email) |
| workspaces | name, slug, locale default 'en-US', time_zone default 'UTC', deleted_at | UNIQUE(slug) |
| memberships | workspace_id, user_id, role CHECK IN (OWNER, ADMIN, MEMBER, VIEWER), active, joined_at | UNIQUE(workspace_id, user_id) |
| invitations | workspace_id, email, role, token_hash, expires_at, invited_by, accepted_at, revoked_at | UNIQUE(token_hash); partial UNIQUE(workspace_id, email) WHERE accepted_at IS NULL AND revoked_at IS NULL |
| password_reset_tokens | user_id, token_hash, expires_at, used_at | UNIQUE(token_hash) |
| refresh_tokens | user_id, workspace_id, family_id, token_hash, expires_at, absolute_expires_at, rotated_at, revoked_at, user_agent, ip | UNIQUE(token_hash); idx(family_id); idx(user_id) |

## reference data
| Table | Columns | Constraints |
|---|---|---|
| categories | name, description | partial UNIQUE(workspace_id, lower(name)) WHERE deleted_at IS NULL |
| statuses | name (3–25), category_id FK, description, background_color, text_color, icon, start_procedure_id FK, stop_procedure_id FK | unique name per workspace; CHECK start ≠ stop |
| tags | same columns as statuses | unique name per workspace; CHECK start ≠ stop |
| — | — | Partial UNIQUE(start_procedure_id) across tags ∪ statuses is enforced in the service plus a deferred trigger check (BR-014) |

## crm
| Table | Columns | Constraints |
|---|---|---|
| contacts | type, name_title, first_name, middle_name, last_name, company, industry, position, email citext, phone, mobile, work_phone, address_line1, address_line2, city, region, postal_code, country, latitude numeric(9,6), longitude numeric(9,6), image_file_id, category_id, status_id, representative_user_id, contract_signed, active, metadata jsonb | partial UNIQUE(workspace_id, email); idx(status_id), idx(category_id), idx(representative_user_id); GIN trigram index on the (first_name, last_name, email, company) search vector |
| contact_tags | contact_id, tag_id | PK(contact_id, tag_id) |
| locations | contact_id FK NOT NULL, type, name, address columns, lat/lng, access_instructions, parking_available, working_hours jsonb (`{"MONDAY":{"open":"09:00","close":"17:00","closed":false},…}`) | idx(contact_id), idx(city) |
| notes | contact_id, project_id, job_id, title, body, document_file_id | CHECK exactly one of the three parents is non-null |

## work
| Table | Columns | Constraints |
|---|---|---|
| milestone_templates | name, description, category_id, steps jsonb | — |
| milestones | owner_type CHECK IN (CONTACT, LOCATION, PROJECT, JOB, TASK), owner_id, name, description, category_id, current_step int | UNIQUE(owner_type, owner_id) |
| milestone_steps | milestone_id FK ON DELETE CASCADE, step_order, label, icon, background_color, text_color, view_mode, image_file_id, start_date, end_date, completed, completed_at, completed_by | UNIQUE(milestone_id, step_order) |
| projects | name, description, type, address columns, total_area numeric(12,2), budget numeric(19,2), start_date, estimated_end_date, location_id FK, contact_id FK, project_manager_member_id | CHECK estimated_end_date ≥ start_date; CHECK budget ≥ 0 |
| project_tags | project_id, tag_id | PK |
| project_documents | project_id, file_id | PK |
| team_members | project_id FK, user_id FK, position, is_manager | partial UNIQUE(project_id, user_id) WHERE deleted_at IS NULL; partial UNIQUE(project_id) WHERE is_manager |
| team_member_responsibilities | team_member_id, responsibility | PK |
| team_member_notes | team_member_id, author_user_id, text | — |
| boards | owner_type CHECK IN (PROJECT, JOB), owner_id, title, workflow, max_wip | partial UNIQUE(owner_type, owner_id) WHERE deleted_at IS NULL |
| board_columns | board_id FK, title (≤ 30), position int, is_default | UNIQUE(board_id, position) DEFERRABLE INITIALLY DEFERRED; partial UNIQUE(board_id) WHERE is_default |
| tasks | project_id, job_id, board_id, column_id, position, title, description, start_date, end_date, priority, status, completed_at | CHECK (project_id IS NULL) <> (job_id IS NULL); CHECK end_date ≥ start_date; UNIQUE(column_id, position) DEFERRABLE (where not deleted, enforced in the service); idx(status), idx(end_date) |
| task_tags, task_assignees | task_id, tag_id / team_member_id | PK |
| task_comments | task_id, author_user_id, text, image_file_id | idx(task_id) |
| board_templates | title, description, is_default | partial UNIQUE(workspace_id) WHERE is_default AND deleted_at IS NULL |
| board_template_columns | template_id, title, position, is_default | UNIQUE(template_id, position) |
| flowcharts | title, description, thumbnail_file_id, favorite, project_id, owner_user_id, diagram jsonb, diagram_size int | CHECK diagram_size ≤ 1 MB |
| flowchart_tags | flowchart_id, tag_id | PK |

## operations
| Table | Columns | Constraints |
|---|---|---|
| jobs | project_id FK NOT NULL, title, description, start_date, estimated_end_date, budget, team_manager_member_id | CHECK dates; CHECK budget ≥ 0; idx(project_id) |
| job_team_members | job_id, team_member_id | PK |
| job_tags | job_id, tag_id | PK |
| job_files | job_id, file_id, kind (MEDIA, DOCUMENT) | PK(job_id, file_id) |
| warranties | project_id, job_id, title, provider, status, note, start_date, end_date | CHECK end ≥ start; idx(status, end_date) |
| warranty_tags, warranty_documents | — | PK |

## finance
| Table | Columns | Constraints |
|---|---|---|
| transactions | job_id FK NOT NULL, project_id FK NOT NULL, contact_id FK NOT NULL, type CHECK IN (RECEIVED, PAID), amount numeric(19,2) CHECK > 0, title, payee, method, bank_name, check_number, commitment_date, due_date, doc_notes, doc_number, doc_date, attachment_file_id, voided_at, voided_by, void_reason | CHECK due_date ≥ commitment_date; CHECK method-specific fields; idx(job_id) INCLUDE(type, amount) WHERE voided_at IS NULL; same for project_id and contact_id; **no UPDATE except the void columns** (enforced in the service, and by a trigger that rejects other column updates) |

## automation
| Table | Columns | Constraints |
|---|---|---|
| procedures | name, description, category, layout_direction, flow jsonb | unique name per workspace |
| procedure_actions | procedure_id FK ON DELETE CASCADE, action_order, command, params jsonb, component jsonb (UI hints) | UNIQUE(procedure_id, action_order) |
| automation_runs | procedure_id, entity_type, entity_id, trigger_type (TAG, STATUS, MANUAL), trigger_id, status, chain_depth, started_at, finished_at | partial UNIQUE(entity_type, entity_id, procedure_id) WHERE status = 'RUNNING'; idx(status) |
| automation_run_steps | run_id FK, step_order, command, params jsonb, status, due_at, attempts, last_error, result_message, idempotency_key, finished_at | UNIQUE(run_id, step_order); idx(status, due_at) WHERE status IN ('PENDING','WAITING') |
| shedlock | standard ShedLock table | — |

## files, audit, notifications
| Table | Columns | Constraints |
|---|---|---|
| stored_files | kind (IMAGE, DOCUMENT), storage_key, original_name, content_type, size_bytes, checksum_sha256, uploaded_by, attached bool | UNIQUE(storage_key); idx(attached, created_at) |
| audit_entries | actor_user_id (nullable for automation), actor_label, action (CREATE, UPDATE, DELETE, VOID, MOVE), entity_type, entity_id, project_id, job_id, changes jsonb, occurred_at | idx(workspace_id, occurred_at DESC); idx(entity_type, entity_id). Append-only (no updated_* or version columns). |
| notifications | recipient_user_id, type, title, body, link, read_at | idx(recipient_user_id, read_at) |
| event_publication | Spring Modulith event registry | — |

## Data protection
- Password and token values are stored only as hashes.
- PII columns (contact names, emails, phones, addresses) are protected by RDS encryption at rest (AWS design) and are never logged.
- DB roles: the app user owns no DDL in production (Flyway runs with a migration user). The read-replica user is optional.

## Seed data
- **Workspace defaults** on sign-up (BR-016, F1).
- **Demo profile:** workspace "Acme Construction", one user per role (passwords from env `DEMO_PASSWORD`), 25 contacts, 10 locations, 6 projects, 15 jobs, 80 tasks, 30 transactions, 3 procedures, 4 warranties, 2 flowcharts.
