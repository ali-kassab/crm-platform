# ADR-0015 — Realtime via a NestJS service, Redis pub/sub and one-time socket tickets

- Status: accepted (2026-11-19, roadmap R11; resolves decision D4's open evaluation).
  Approved by the owner on 2026-11-20 as a deliberate deviation from `PRODUCT_AUDIT.md` D4: keep
  Redis pub/sub (no Redis Streams, no outbox), keep one-time tickets, no RS256 migration for
  realtime. Lost hints are accepted; clients recover by refetching from the Java API. Realtime is
  never used for critical business operations or persistence.
- Supersedes the STOMP part of the earlier realtime plan (Q10).

## Context

The directive requires a **separate NestJS service** for genuinely real-time features while the
Java backend stays the only source of truth. D4 asked to evaluate STOMP vs Redis Streams and
HS256-shared-secret vs RS256 before changing anything.

What has to be real-time today: a user's new in-app notifications, and "something changed" hints for
work orders and visits so open screens refresh. Notifications are already **durable** in PostgreSQL
(R5); the realtime channel is only a hint to refetch.

## Decision

1. **Transport Java → NestJS: Redis pub/sub** on channel `rt:events`, published by the Java
   `realtime` module **after commit** (`@TransactionalEventListener`) from two sources:
   `NotificationsCreated` (per-user) and the existing `AuditRecorded` stream (per-workspace, for
   JOB, VISIT, MATERIAL, APPROVAL, EQUIPMENT, MAINTENANCE_PLAN), plus `WorkEvents.BoardChanged`
   (per-workspace, entity type `BOARD`, the board id and the change type only). Rolled-back changes
   publish nothing.
   - Not STOMP-in-Java: the directive moves realtime out of the monolith.
   - Not Redis Streams / an outbox *for now*: a missed hint costs nothing — the data is in
     PostgreSQL and clients refetch on reconnect. Upgrade path if a feature ever needs guaranteed
     delivery: write the same payload to an outbox table and relay it to a Redis Stream.
2. **Socket authentication: one-time tickets, not shared JWT secrets.** The signed-in client calls
   `POST /api/v1/realtime/ticket`; Java stores `rt:ticket:<random 256-bit>` → `{userId, workspaceId}`
   in Redis for **60 s**; the client opens the socket with that ticket; NestJS consumes it with an
   atomic `GETDEL` (single use). NestJS never sees a JWT or a signing key, so it cannot mint tokens
   (the HS256 risk), and RS256 is not needed for this.
3. **Least data on the wire.** Workspace-wide events carry only ids, type and action — never titles
   or amounts — so a technician learns nothing beyond "something changed"; the client refetches
   through the normal API, which applies its permissions. Per-user events go only to `user:<id>`.
4. **Rooms**: `ws:<workspaceId>` and `user:<userId>:<workspaceId>` (a user in two workspaces only
   hears each workspace's notifications on that workspace's socket), joined only from the ticket's claims.

## Consequences

- Java needs only Redis (already used for rate limiting and login throttling).
- The realtime service is stateless and horizontally scalable (each instance subscribes to
  `rt:events`; Socket.IO rooms are per instance, which is correct because every instance receives
  every message).
- A disconnected client misses hints; it refetches on reconnect (frontend invalidates its queries).
- Expired/suspended subscriptions do not affect realtime (it is read-only by nature).
