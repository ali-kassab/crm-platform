import { useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import { useEffect } from 'react'
import type { Socket } from 'socket.io-client'
import { api } from '@/lib/api'

/**
 * Live "something changed" hints from the NestJS realtime service (ADR-0015).
 *
 * Hints carry ids only and may be lost (Redis pub/sub); they never carry data. The client only
 * invalidates queries, so everything shown still comes from the Java API with the caller's
 * permissions. After every reconnect all active queries are refetched to catch up on missed hints.
 * If the realtime service or the ticket endpoint is unavailable the app keeps working on plain REST.
 */

const SOCKET_PATH = '/realtime/socket.io'
const MIN_RETRY_MS = 5_000
const MAX_RETRY_MS = 5 * 60_000
const BATCH_MS = 250

interface Changed {
  entityType: string
  entityId: string | null
  jobId: string | null
  action: string
}

/** Operational types that affect work orders, visits and the money rolled up from them. */
const OPERATIONS = new Set(['JOB', 'VISIT', 'MATERIAL', 'APPROVAL', 'EQUIPMENT', 'MAINTENANCE_PLAN'])

/** Query keys to refresh for one hint (prefix match, like `invalidateQueries`). */
export function keysFor(e: Changed): QueryKey[] {
  if (e.entityType === 'BOARD') return e.entityId ? [['board', e.entityId]] : [['board']]
  if (!OPERATIONS.has(e.entityType)) return []
  const keys: QueryKey[] = [['jobs'], ['visits'], ['outstanding']]
  if (e.jobId) keys.push(['job', e.jobId])
  if (e.entityType === 'JOB' && e.entityId && e.entityId !== e.jobId) keys.push(['job', e.entityId])
  return keys
}

async function fetchTicket(): Promise<string | null> {
  try {
    const r = await api<{ ticket: string }>('/realtime/ticket', { method: 'POST' })
    return typeof r?.ticket === 'string' ? r.ticket : null
  } catch {
    return null // realtime disabled / not deployed / signed out: stay on plain REST
  }
}

/** Collects keys for a short moment so a burst of hints causes one refetch per query. */
function batcher(client: QueryClient) {
  const pending = new Map<string, QueryKey>()
  let timer: ReturnType<typeof setTimeout> | undefined
  return {
    add(keys: QueryKey[]) {
      keys.forEach((k) => pending.set(JSON.stringify(k), k))
      timer ??= setTimeout(() => {
        timer = undefined
        const keys = [...pending.values()]
        pending.clear()
        keys.forEach((queryKey) => void client.invalidateQueries({ queryKey }))
      }, BATCH_MS)
    },
    cancel() {
      if (timer) clearTimeout(timer)
      pending.clear()
    },
  }
}

/** Keeps one socket per signed-in user and workspace; pass nulls to stay disconnected. */
export function useRealtime(userId: string | null, workspaceId: string | null) {
  const client = useQueryClient()

  useEffect(() => {
    if (!userId || !workspaceId) return
    let stopped = false
    let socket: Socket | null = null
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let delay = MIN_RETRY_MS
    let connectedBefore = false
    const batch = batcher(client)

    const scheduleRetry = () => {
      if (stopped) return
      clearTimeout(retryTimer)
      retryTimer = setTimeout(() => void start(), delay)
      delay = Math.min(delay * 2, MAX_RETRY_MS)
    }

    const start = async () => {
      // Check the backend first: no ticket (404 / 401 / Redis down) → no socket, try later.
      const first = await fetchTicket()
      if (stopped) return
      if (!first) return scheduleRetry()
      const { io } = await import('socket.io-client')
      if (stopped) return
      let initial: string | null = first
      socket?.close()
      const s = io({
        path: SOCKET_PATH,
        transports: ['websocket'],
        // every (re)connection attempt gets a fresh one-time ticket
        auth: (cb) => {
          const use = initial
          initial = null
          if (use) return cb({ ticket: use })
          void fetchTicket().then((t) => cb(t ? { ticket: t } : {}))
        },
        reconnectionDelay: MIN_RETRY_MS,
        reconnectionDelayMax: MAX_RETRY_MS,
      })
      socket = s
      s.on('ready', () => {
        delay = MIN_RETRY_MS
        // missed hints are not replayed: refetch whatever is on screen
        if (connectedBefore) void client.invalidateQueries()
        connectedBefore = true
      })
      s.on('notification', () => batch.add([['notifications']]))
      s.on('changed', (e: Changed) => batch.add(keysFor(e)))
      s.on('disconnect', (reason) => {
        // the server rejected the ticket (or closed us): socket.io will not retry on its own
        if (reason === 'io server disconnect') {
          s.close()
          scheduleRetry()
        }
      })
    }

    void start()
    return () => {
      stopped = true
      clearTimeout(retryTimer)
      batch.cancel()
      socket?.close()
    }
  }, [client, userId, workspaceId])
}
