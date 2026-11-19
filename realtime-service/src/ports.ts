/** What a socket ticket proves (written by the Java backend, see ADR-0015). */
export interface Claims {
  userId: string
  workspaceId: string
}

/** Consumes a one-time ticket: returns its claims once, then never again. */
export interface TicketStore {
  consume(ticket: string): Promise<Claims | null>
}

/** Messages published by the Java backend on `rt:events`. */
export type RelayMessage =
  | { kind: 'notification'; workspaceId: string; userIds: string[] }
  | {
      kind: 'changed'
      workspaceId: string
      entityType: string
      entityId: string | null
      jobId: string | null
      action: string
    }

export interface EventSource {
  subscribe(handler: (message: RelayMessage) => void): Promise<void>
  close(): Promise<void>
}

export const TICKET_STORE = Symbol('TICKET_STORE')
export const EVENT_SOURCE = Symbol('EVENT_SOURCE')

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const WORD = /^[A-Z_]{1,40}$/

export const isUuid = (v: unknown): v is string => typeof v === 'string' && UUID.test(v)

/** Strict shape check: anything unexpected on the channel is dropped, never forwarded. */
export function parseMessage(raw: string): RelayMessage | null {
  let m: unknown
  try {
    m = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof m !== 'object' || m === null) return null
  const o = m as Record<string, unknown>
  if (typeof o.workspaceId !== 'string' || !UUID.test(o.workspaceId)) return null
  const optionalId = (v: unknown) => v === null || v === undefined || (typeof v === 'string' && UUID.test(v))
  if (o.kind === 'notification') {
    if (!Array.isArray(o.userIds) || o.userIds.length > 1000) return null
    if (!o.userIds.every((u) => typeof u === 'string' && UUID.test(u))) return null
    return { kind: 'notification', workspaceId: o.workspaceId, userIds: o.userIds as string[] }
  }
  if (o.kind === 'changed') {
    if (typeof o.entityType !== 'string' || !WORD.test(o.entityType)) return null
    if (typeof o.action !== 'string' || !WORD.test(o.action)) return null
    if (!optionalId(o.entityId) || !optionalId(o.jobId)) return null
    return {
      kind: 'changed',
      workspaceId: o.workspaceId,
      entityType: o.entityType,
      entityId: (o.entityId as string | null) ?? null,
      jobId: (o.jobId as string | null) ?? null,
      action: o.action,
    }
  }
  return null
}
