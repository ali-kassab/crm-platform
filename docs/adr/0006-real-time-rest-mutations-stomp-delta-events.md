# ADR-0006: Real-time: REST mutations + STOMP delta events

- Status: Accepted (2026-11-18)
- All board mutations go through REST with optimistic versioning.
- After commit, the server publishes delta events to `/topic/workspaces/{wid}/boards/{boardId}` over STOMP.
- The JWT is checked on CONNECT. SUBSCRIBE is authorized per board (the board must belong to the token's workspace).
- Multiple instances: events are relayed through Redis pub/sub, and each instance forwards them to its local simple broker.

