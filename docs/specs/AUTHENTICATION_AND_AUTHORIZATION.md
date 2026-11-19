# Authentication & Authorization

See AUTH_FLOWS.md for step-by-step flows and ADR-0003/0004/0005 for the rationale.

## 1. Identity model
| Entity | Fields | Rules |
|---|---|---|
| User | id, email (unique, case-insensitive), passwordHash, firstName, lastName, jobTitle?, phone?, avatarFileId?, active, superAdmin, lastLoginAt, failedLoginCount | Email stored in lower case. Disabled users can't log in. |
| Workspace | id, name (2–80), slug (unique), createdAt | Created on sign-up |
| Membership | workspaceId, userId, role, joinedAt | Unique (workspace, user). Exactly ≥ 1 Owner per workspace. |
| Invitation | id, workspaceId, email, role (Admin/Member/Viewer), tokenHash, expiresAt (7 days), invitedBy, acceptedAt?, revokedAt? | One pending invite per (workspace, email) |
| PasswordResetToken | userId, tokenHash, expiresAt (30 min), usedAt? | Single use. Requesting a new one invalidates older ones. |
| RefreshToken | id, userId, workspaceId, familyId, tokenHash, expiresAt, absoluteExpiresAt, rotatedAt?, revokedAt?, userAgent, ip | Rotation, reuse detection |

## 2. Roles → permissions

| Permission | Owner | Admin | Member | Viewer |
|---|:-:|:-:|:-:|:-:|
| `workspace:manage` (rename workspace) | ✅ | ✅ | | |
| `workspace:delete` | ✅ | | | |
| `members:manage` (invite, change role, remove) | ✅ | ✅¹ | | |
| `refdata:read` (categories, statuses, tags) | ✅ | ✅ | ✅ | ✅ |
| `refdata:manage` | ✅ | ✅ | | |
| `crm:read` (contacts, locations, notes) | ✅ | ✅ | ✅ | ✅ |
| `crm:write` | ✅ | ✅ | ✅ | |
| `crm:delete` | ✅ | ✅ | ✅² | |
| `work:read` (projects, team, boards, tasks, milestones, flowcharts, templates) | ✅ | ✅ | ✅ | ✅ |
| `work:write` | ✅ | ✅ | ✅ | |
| `work:delete` | ✅ | ✅ | | |
| `templates:manage` (board and milestone templates) | ✅ | ✅ | | |
| `ops:read` (jobs, warranties) | ✅ | ✅ | ✅ | ✅ |
| `ops:write` | ✅ | ✅ | ✅ | |
| `ops:delete` | ✅ | ✅ | | |
| `finance:read` | ✅ | ✅ | ✅ | |
| `finance:write` (create and void transactions) | ✅ | ✅ | | |
| `automation:read` | ✅ | ✅ | ✅ | |
| `automation:manage` (procedures, cancel runs) | ✅ | ✅ | | |
| `audit:read` | ✅ | ✅ | | |
| `reports:read` | ✅ | ✅ | | |
| `files:upload` | ✅ | ✅ | ✅ | |

¹ An Admin can't change or remove an Owner and can't grant Owner.
² Members may delete contacts, locations and notes they created. Admins and Owners may delete any.

**Platform super-admin** (`superAdmin=true`): may call `GET /api/v1/admin/stats` (cross-tenant aggregate counts). It gets no access to tenant records.

## 3. Resource-level rules
| Rule | Detail |
|---|---|
| AZ-01 | Notes on tasks, team members and entities can be edited only by their author. They can be deleted by the author or an Admin+. |
| AZ-02 | Flowcharts: everyone with `work:read` can read them. Only the owner or an Admin+ can update or delete them. |
| AZ-03 | The last Owner can't leave, be removed or be demoted (409 `LAST_OWNER`). |
| AZ-04 | A user can't change their own role. |
| AZ-05 | Ownership transfer: an Owner may promote another member to Owner and then demote themself. |

## 4. Enforcement
- Every endpoint except the public auth ones (register, login, refresh, logout, password forgot/reset, invitation lookup/accept, health) requires a valid access token.
- The required permission is declared per endpoint (see API_SPECIFICATION.md). Missing permission → **403** `FORBIDDEN`.
- A cross-tenant id → **404**.
- WebSocket: CONNECT needs `Authorization: Bearer <access token>` in the STOMP headers. SUBSCRIBE to a board topic needs `work:read` and the board must belong to the token's workspace.
- Permissions are re-evaluated from the database (via a 5-minute cache, evicted when a role changes) on each request. A role change therefore takes effect within seconds; token claims are only hints for the UI.

## 5. Session & token parameters (configurable)
| Setting | Default |
|---|---|
| Access token TTL | 15 min |
| Refresh sliding TTL / absolute | 14 days / 60 days |
| Lockout | 5 failures → 15 min |
| Rate limits | login 10/min per IP + 5/min per email; register 5/hour per IP; forgot-password 3/hour per email; upload 30/min per user |
| Password policy | 10–128 characters, at least 1 letter and 1 digit, not equal to the email, not in a 10k-common-passwords list |
