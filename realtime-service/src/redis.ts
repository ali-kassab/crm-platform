import Redis from 'ioredis'
import { isUuid, parseMessage, type Claims, type EventSource, type RelayMessage, type TicketStore } from './ports'

const TICKET_PREFIX = 'rt:ticket:'
const TICKET_FORMAT = /^[A-Za-z0-9_-]{40,64}$/

export class RedisTicketStore implements TicketStore {
  constructor(private readonly redis: Redis) {}

  async consume(ticket: string): Promise<Claims | null> {
    if (!TICKET_FORMAT.test(ticket)) return null
    const raw = await this.redis.getdel(TICKET_PREFIX + ticket) // atomic: a ticket works once
    if (!raw) return null
    try {
      const c = JSON.parse(raw) as Partial<Claims>
      // claims become room names: accept only well-formed ids
      return isUuid(c.userId) && isUuid(c.workspaceId)
        ? { userId: c.userId, workspaceId: c.workspaceId }
        : null
    } catch {
      return null
    }
  }
}

/** Pub/sub needs its own connection (a subscribed connection can't run other commands). */
export class RedisEventSource implements EventSource {
  constructor(
    private readonly subscriber: Redis,
    private readonly channel: string,
  ) {}

  async subscribe(handler: (message: RelayMessage) => void): Promise<void> {
    this.subscriber.on('message', (channel: string, raw: string) => {
      if (channel !== this.channel) return
      const message = parseMessage(raw)
      if (message) handler(message)
    })
    await this.subscriber.subscribe(this.channel)
  }

  async close(): Promise<void> {
    await this.subscriber.quit()
  }
}
