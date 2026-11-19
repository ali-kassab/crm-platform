# Real-time Specification (STOMP over WebSocket)

## Connection
- Endpoint: `wss://<host>/ws` (plain WebSocket. A SockJS fallback isn't required.)
- The STOMP `CONNECT` frame must contain the header `Authorization: Bearer <accessToken>`. Invalid or expired → `ERROR` frame `UNAUTHORIZED` and the connection closes.
- Heartbeat 10 s / 10 s. The client reconnects with exponential backoff (1 s → 30 s) and refreshes the access token before reconnecting.
- Access expiry: the server doesn't drop live sessions at token expiry. It does re-check membership when the user subscribes again.

## Destinations
| Destination | Direction | Authorization | Content |
|---|---|---|---|
| `/topic/boards/{boardId}` | server → clients | `work:read` + the board belongs to the active workspace (checked on SUBSCRIBE; otherwise `ERROR` `FORBIDDEN`) | Board events |
| `/user/queue/notifications` | server → one user | authenticated | Notification events |

Clients **never send mutations over STOMP**. All changes go through REST (single validation and authorization path). STOMP `SEND` to any destination is rejected.

## Board event envelope

```json
{
  "type": "TASK_MOVED",
  "boardId": "…",
  "boardVersion": 42,
  "occurredAt": "2026-11-18T10:15:00Z",
  "actor": { "userId": "…", "name": "Sam Rivera" },
  "payload": { … }
}
```

| type | payload |
|---|---|
| `TASK_CREATED` | full task card |
| `TASK_UPDATED` | full task card |
| `TASK_MOVED` | `{taskId, fromColumnId, toColumnId, position}`; positions of the other tasks are implied by BR-076 |
| `TASK_DELETED` | `{taskId, columnId}` |
| `TASK_COMMENT_ADDED` / `TASK_COMMENT_DELETED` | `{taskId, commentCount}` |
| `COLUMN_CREATED` / `COLUMN_UPDATED` | column `{id, title, position, isDefault}` |
| `COLUMN_MOVED` | `{columnId, position}` |
| `COLUMN_DELETED` | `{columnId, movedTasksTo?}` |
| `BOARD_RESYNC` | `{}`: clients must refetch `GET /boards/{id}` |

## Ordering & consistency
- `boardVersion` increases by 1 with every committed board mutation.
- A client applies an event if `event.boardVersion == local + 1`. If the version jumps (a gap), it refetches the board. If `event.boardVersion <= local`, it ignores the event (its own echo).
- Events are published **after the transaction commits** (transactional event listener) and fanned out across instances through Redis pub/sub channel `crm:board-events`.
- The client that made the change already has the result from the REST response. It still receives the event and dedupes by version.

## Notification event
`{ "id", "type": "PROJECT_MEMBER_ADDED|PROJECT_MEMBER_REMOVED|PROJECT_MANAGER_ASSIGNED|PROJECT_DELETED|TASK_ASSIGNED", "title", "body", "link", "createdAt" }`

## Edge cases
- The board is deleted while users are subscribed → `BOARD_RESYNC` is sent. The refetch returns 404, so the client leaves the board view.
- A user's membership is removed → their next SUBSCRIBE or REST call fails. Already-open subscriptions are closed by the server within 60 s (periodic session sweep).
- Redis is unavailable → events are delivered only to clients on the same instance, a warning is logged, and readiness reports DEGRADED.
