# Compatibility Differences (intentional changes vs legacy behavior)

The new system is a clean redesign for a new client (Q3), so wire compatibility isn't a goal. This document lists the **behavioral** differences a legacy user would notice.

| Area | Legacy | New | Reason / ref |
|---|---|---|---|
| Identity | Users came from an external system; no sign-up | Self sign-up, workspaces, invites, roles | D1/D2 |
| API shape | `{message, status, error, pagination, data}` envelope (inconsistent) | Plain resources, Page objects, Problem Details errors | ADR-0011 |
| Pagination | 1-based `page`, `limit` (default 10, unbounded) | 0-based `page`, `size` (default 20, max 100) | KI-07 |
| Filtering | Any field via query string | Documented filters per endpoint | KI-07 |
| IDs | 24-hex ids | UUIDs | ADR-0002 |
| Enums | lower/camel case (`inProgress`, `received`, `bank_transfer`) | UPPER_SNAKE (`IN_PROGRESS`, `RECEIVED`, `BANK_TRANSFER`) | Consistency |
| Real-time | Socket.IO events, mutations over the socket | STOMP, read-only delta events; mutations via REST | ADR-0006 |
| Status codes | 403 for bad login; 200 for some creates | 401; 201 | API conventions |
| Notes | Optional parents; description limited to 100 characters | Exactly one parent; body up to 5000 | BR-030/031 |
| Kanban column delete | Tasks orphaned | Must relocate tasks | BR-072 |
| Automation delay | Offset from run start | Offset from previous step | BR-104 (Q-16) |
| Automation commands | 19 names (some unimplemented) | 15 validated commands (DELAY, WAIT_UNTIL, …) | AUTOMATION_SPEC §3 |
| Automation trigger | Tags only | Tags and statuses | NF-02 |
| Finance | Rollups stored; overpay rejected; no corrections | Rollups computed; overpay allowed; void | ADR-0008, NF-03 |
| Transaction amount | ≥ 0 | > 0 | BR-090 |
| Flowcharts | Reference to an external diagram URL | Diagram stored and edited in the app | NF-01 |
| Files | Public URLs; HTML allowed; separate "general" upload | Private + presigned; images/documents only; one files API | ADR-0009 |
| Geocoding failure | Save fails (400) | Save succeeds with a warning | BR-023 |
| Phone format | US 10-digit | E.164 | Q-21 |
| Team member access level | Stored, unused | Removed (workspace roles apply) | Q-25 |
| Contact activity, chat channels, usage metering | Present (host features) | Removed | Q-18 |
| Notifications | Host push + DB records | In-app notifications + WebSocket push | Q-18 |
| Retention | Finished automation runs purged after 7 days | 30 days | BR-108 |
