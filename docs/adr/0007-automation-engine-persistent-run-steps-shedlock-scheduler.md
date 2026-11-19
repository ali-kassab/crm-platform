# ADR-0007: Automation engine: persistent run steps + ShedLock scheduler

- Status: Accepted (2026-11-18)
- Runs and steps are persisted with status and `due_at`.
- A scheduler (every 5 s, ShedLock-protected) claims due steps with `FOR UPDATE SKIP LOCKED` in batches of 50.
- Each command is a strategy (`ActionHandler`). The commands are validated when a procedure is saved.
- Loop guard: the chain depth is tracked per triggering event (max 5), and at most one active run is allowed per (entity, procedure).

