# ADR-0005: Authorization: fixed roles → permissions

- Status: Accepted (2026-11-18)
- Roles are Owner, Admin, Member and Viewer, mapped to permission constants (see AUTHENTICATION_AND_AUTHORIZATION.md). Endpoints use `@PreAuthorize("hasAuthority('crm:write')")`.
- Resource-level ownership rules (e.g. only the note's author may edit it) live in the application services.

