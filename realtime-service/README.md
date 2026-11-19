# realtime-service

Small NestJS (Socket.IO) service that pushes **"something changed" hints** from the Java backend to
browsers. Design and the owner's approval: [`docs/adr/0015-realtime-nestjs-redis-tickets.md`](../docs/adr/0015-realtime-nestjs-redis-tickets.md).

- The Java backend stays the **only source of truth**. Hints carry ids and a change type only —
  never titles, amounts or other business data. Browsers react by refetching through the Java API,
  which applies the caller's permissions.
- Transport is **Redis pub/sub** (`rt:events`). Hints sent while a browser is disconnected are
  **lost by design**; the frontend refetches everything on screen after each reconnect. Never use
  this service for anything that must be delivered or persisted.
- If this service (or Redis) is down, the app keeps working on plain REST — screens just refresh on
  navigation instead of live.

## How a browser connects

1. Signed-in client calls `POST /api/v1/realtime/ticket` on the Java API (needs a normal access
   token and a workspace membership).
2. Java stores `rt:ticket:<256-bit random>` → `{userId, workspaceId}` in Redis for **60 s** and
   returns the ticket.
3. Client opens `io({ path: '/realtime/socket.io', auth: { ticket } })`.
4. This service consumes the ticket with an atomic **`GETDEL`** (single use; malformed tickets and
   claims that are not UUIDs are rejected), then joins the socket to exactly two rooms:
   `ws:<workspaceId>` and `user:<userId>:<workspaceId>`. A rejected socket gets `unauthorized` and
   is disconnected. Every reconnect needs a fresh ticket.

This service never sees access tokens, refresh tokens or signing keys, so it cannot mint or verify
sessions. A socket stays open after the user signs out elsewhere until it drops; this is accepted
because it only ever receives ids for that workspace (the frontend closes it on sign-out).

## Messages

Published by Java on `rt:events` after the database commit (rolled-back changes publish nothing).
Anything that does not match these shapes is dropped (`src/ports.ts` → `parseMessage`).

| From Java | To browsers |
|---|---|
| `{kind:"notification", workspaceId, userIds[]}` | `notification` (no payload) to each `user:<id>:<workspaceId>` room |
| `{kind:"changed", workspaceId, entityType, entityId, jobId, action}` | `changed` `{entityType, entityId, jobId, action}` to `ws:<workspaceId>` |

`entityType` is one of `JOB`, `VISIT`, `MATERIAL`, `APPROVAL`, `EQUIPMENT`, `MAINTENANCE_PLAN`
(from the audit stream) or `BOARD` (task board changes; `action` = `TASK_CREATED`, `TASK_MOVED`,
`COLUMN_UPDATED`, …).

Socket events from server to client: `ready` (accepted), `unauthorized`, `notification`, `changed`.
Clients never send anything.

## Run

Requires Node 24 and pnpm (`export PATH="/d/Workspace/tools/node-v24.22.3-win-x64:$PATH"` on the dev machine).

```bash
pnpm install
pnpm build
pnpm start          # node dist/src/main.js
pnpm test           # compiles, then node --test (in-memory ticket store and event source, no Redis needed)
```

`GET /health` → `{"status":"UP"}`.

| Env var | Default | Meaning |
|---|---|---|
| `PORT` | `3001` | HTTP + WebSocket port |
| `REDIS_URL` | `redis://localhost:6379` | Same Redis as the Java backend |
| `REALTIME_CHANNEL` | `rt:events` | Must match `app.realtime.channel` in the backend |
| `ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated CORS origins for the socket handshake |

Java side: `REALTIME_ENABLED=false` stops publishing (tickets are still issued; REST is unaffected).

In development the Vite dev server proxies `/realtime` (WebSocket) to `http://localhost:3001`
(override with `VITE_REALTIME_PROXY`). In production put this service behind the same origin under
`/realtime/` so no CORS is needed. Instances are stateless: each subscribes to the channel and
serves its own sockets, so it can run more than one replica without a Socket.IO adapter.

Not done yet (R13): Dockerfile and a docker-compose entry.
