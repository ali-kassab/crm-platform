# Known Issues in the Legacy System (behavioral) and their Resolution

These are described as observable behavior. Decision column: **Fix** (don't reproduce) · **Preserve** · **Drop** (feature removed).

| ID | Severity | Observed legacy behavior | Decision | New behavior (rule) |
|---|---|---|---|---|
| KI-01 | Critical | Any authenticated user can perform any action; permissions are never checked | Fix | Role → permission matrix (AUTHZ §2) |
| KI-02 | Critical | Most lists and lookups return records from every tenant | Fix | BR-001, ADR-0004 |
| KI-03 | Critical | Update requests can set server-owned fields (admin flag, tenant, password in plain text) | Fix | Explicit DTOs; password only via auth flows |
| KI-04 | High | Login tokens never expire; logout doesn't invalidate them | Fix | ADR-0003 |
| KI-05 | High | Real-time board channel needs no authentication | Fix | REALTIME §Connection |
| KI-06 | High | A user-listing endpoint is public | Fix | All endpoints authenticated |
| KI-07 | High | List filters accept any field and operator (including secrets) and unbounded page sizes | Fix | Whitelisted filters, max size 100 |
| KI-08 | High | Stored files are public, HTML files can be uploaded, and any file can be deleted by name | Fix / Drop HTML | ADR-0009 |
| KI-09 | Medium | Search terms are interpreted as regular expressions | Fix | EC-40 |
| KI-10 | High | Project or job creation fails when no default board template exists | Fix | BR-054, EC-12 |
| KI-11 | High | Multi-step creations aren't atomic and leave partial data | Fix | EC-13 |
| KI-12 | High | Deleting a project doesn't hide its jobs, tasks, notes and warranties | Fix | BR-056 |
| KI-13 | Medium | Remaining budget gets out of date when budgets change or jobs are deleted | Fix | BR-055 (derived) |
| KI-14 | High | Financial rollups can be inconsistent; overpayment causes transaction creation to fail; a job from another project is accepted | Fix | BR-091, BR-094, ADR-0008 |
| KI-15 | Medium | Deleted tasks remain on boards; deleting a column orphans its tasks; changing a task's parent doesn't move it | Fix | BR-072, FR-WRK-13/15 |
| KI-16 | High | The REST task-reorder endpoint doesn't work (only the socket path does) | Fix | FR-WRK-14 |
| KI-17 | High | Flowchart update doesn't work | Fix | FR-WRK-19 |
| KI-18 | Medium | Creating a media record always fails and deletes the uploaded file | Fix | FILES module |
| KI-19 | High | Automation: scheduled emails are skipped while later steps still run; procedures can loop forever; some commands never finish; one command name doesn't match | Fix | AUTOMATION_SPEC |
| KI-20 | Medium | Automation runs in every server instance (duplicate execution) | Fix | ADR-0007 |
| KI-21 | Medium | Names (categories, tags, statuses, procedures, contact emails) are unique across all tenants | Fix | BR-003 |
| KI-22 | Low | Clearing a field or setting a number to 0 via update is impossible | Fix | BR-004 |
| KI-23 | Medium | Contact save after a geocoding failure loses the contact image | Fix | BR-023 |
| KI-24 | Medium | Deactivating a user doesn't unassign them as contact representative | Fix | BR-024 |
| KI-25 | Low | Task completion time is never recorded, so the related dashboard metric is always empty | Fix | BR-078 |
| KI-26 | Low | Dashboards count deleted records; the "year" and "YTD" periods are identical | Fix | FR-RPT-01 |
| KI-27 | Low | Warranties can end before they start and never expire | Fix | BR-064, BR-065 |
| KI-28 | Low | Contact activity (calls/SMS) visible only to admins who are also the representative | Drop | Telephony out of scope (Q-18) |
| KI-29 | Low | "Sales reps" listing always returns empty | Drop | Use the members list filtered by role/job title |
| KI-30 | Low | Audit history records only some creates and deletes, no updates | Fix | FR-AUD-01 |
| KI-31 | Low | Response shapes and status codes are inconsistent across endpoints | Fix | API conventions |
| KI-32 | Low | US-only phone, country and date assumptions | Fix | Q-21, VALIDATION §3 |
