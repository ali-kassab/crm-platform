import 'reflect-metadata'
import assert from 'node:assert/strict'
import { after, before, describe, it } from 'node:test'
import type { INestApplication } from '@nestjs/common'
import { NestFactory } from '@nestjs/core'
import { IoAdapter } from '@nestjs/platform-socket.io'
import { io, type Socket } from 'socket.io-client'
import { AppModule } from '../src/app.module'
import { EVENT_SOURCE, parseMessage, TICKET_STORE, type Claims, type EventSource, type RelayMessage, type TicketStore } from '../src/ports'
import { SOCKET_PATH } from '../src/realtime.gateway'
import { RedisTicketStore } from '../src/redis'
import type Redis from 'ioredis'

const W1 = '11111111-1111-4111-8111-111111111111'
const W2 = '22222222-2222-4222-8222-222222222222'
const U1 = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const U2 = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const JOB = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'

class MemoryTickets implements TicketStore {
  readonly tickets = new Map<string, Claims>()
  async consume(ticket: string) {
    const c = this.tickets.get(ticket) ?? null
    this.tickets.delete(ticket) // single use, like GETDEL
    return c
  }
}

class MemoryEvents implements EventSource {
  private handler: (m: RelayMessage) => void = () => {}
  async subscribe(h: (m: RelayMessage) => void) {
    this.handler = h
  }
  publish(raw: string) {
    const m = parseMessage(raw)
    if (m) this.handler(m)
  }
  async close() {}
}

describe('realtime gateway', () => {
  const tickets = new MemoryTickets()
  const events = new MemoryEvents()
  let app: INestApplication
  let url: string
  const sockets: Socket[] = []

  before(async () => {
    app = await NestFactory.create(
      AppModule.with([
        { provide: TICKET_STORE, useValue: tickets },
        { provide: EVENT_SOURCE, useValue: events },
      ]),
      { logger: false },
    )
    app.useWebSocketAdapter(new IoAdapter(app))
    await app.listen(0)
    url = await app.getUrl()
  })

  after(async () => {
    sockets.forEach((s) => s.close())
    await app.close()
  })

  /** Connects with a ticket and resolves once the server accepted (or rejected) it. */
  function connect(ticket: string | undefined): Promise<{ socket: Socket; accepted: boolean }> {
    const socket = io(url, { path: SOCKET_PATH, auth: ticket ? { ticket } : {}, transports: ['websocket'], reconnection: false })
    sockets.push(socket)
    return new Promise((resolve) => {
      socket.on('ready', () => resolve({ socket, accepted: true }))
      socket.on('unauthorized', () => resolve({ socket, accepted: false }))
      socket.on('disconnect', () => resolve({ socket, accepted: false }))
    })
  }

  function collect(socket: Socket, event: string, ms = 300): Promise<unknown[]> {
    const got: unknown[] = []
    socket.on(event, (payload: unknown) => got.push(payload ?? null))
    return new Promise((resolve) => setTimeout(() => resolve(got), ms))
  }

  it('rejects missing, unknown and reused tickets', async () => {
    assert.equal((await connect(undefined)).accepted, false)
    assert.equal((await connect('not-a-ticket')).accepted, false)
    tickets.tickets.set('t-once', { userId: U1, workspaceId: W1 })
    assert.equal((await connect('t-once')).accepted, true)
    assert.equal((await connect('t-once')).accepted, false) // single use
  })

  it('routes workspace hints and per-user notifications only to the right sockets', async () => {
    tickets.tickets.set('a', { userId: U1, workspaceId: W1 })
    tickets.tickets.set('b', { userId: U2, workspaceId: W1 })
    tickets.tickets.set('c', { userId: U1, workspaceId: W2 }) // same user, other workspace
    const a = (await connect('a')).socket
    const b = (await connect('b')).socket
    const c = (await connect('c')).socket

    const changedA = collect(a, 'changed')
    const changedC = collect(c, 'changed')
    const notifA = collect(a, 'notification')
    const notifB = collect(b, 'notification')
    const notifC = collect(c, 'notification')

    events.publish(JSON.stringify({ kind: 'changed', workspaceId: W1, entityType: 'JOB', entityId: JOB, jobId: JOB, action: 'STATUS' }))
    events.publish(JSON.stringify({ kind: 'notification', workspaceId: W1, userIds: [U1] }))
    // garbage on the channel is dropped, never forwarded
    events.publish(JSON.stringify({ kind: 'changed', workspaceId: W1, entityType: '<script>', entityId: JOB, jobId: null, action: 'X' }))
    events.publish('not json')

    assert.deepEqual(await changedA, [{ entityType: 'JOB', entityId: JOB, jobId: JOB, action: 'STATUS' }])
    assert.deepEqual(await changedC, []) // other workspace hears nothing
    assert.equal((await notifA).length, 1)
    assert.equal((await notifB).length, 0) // not addressed to U2
    assert.equal((await notifC).length, 0) // U1, but in another workspace
  })

  it('exposes a health check', async () => {
    const r = await fetch(`${url}/health`)
    assert.equal(r.status, 200)
    assert.deepEqual(await r.json(), { status: 'UP' })
  })
})

describe('redis ticket store', () => {
  /** Stand-in for ioredis: GETDEL returns the value once, then null. */
  function store(values: Record<string, string>) {
    const keys: string[] = []
    const fake = {
      async getdel(key: string) {
        keys.push(key)
        const v = values[key] ?? null
        delete values[key]
        return v
      },
    }
    return { tickets: new RedisTicketStore(fake as unknown as Redis), keys }
  }
  const T = 'A'.repeat(43)

  it('consumes a well-formed ticket exactly once', async () => {
    const { tickets, keys } = store({ ['rt:ticket:' + T]: JSON.stringify({ userId: U1, workspaceId: W1 }) })
    assert.deepEqual(await tickets.consume(T), { userId: U1, workspaceId: W1 })
    assert.equal(await tickets.consume(T), null)
    assert.deepEqual(keys, ['rt:ticket:' + T, 'rt:ticket:' + T])
  })

  it('never queries Redis for malformed tickets', async () => {
    const { tickets, keys } = store({})
    for (const bad of ['', 'short', 'x'.repeat(43) + ':*', 'A'.repeat(65), '../' + T]) {
      assert.equal(await tickets.consume(bad), null)
    }
    assert.deepEqual(keys, [])
  })

  it('rejects claims that are not ids (they become room names)', async () => {
    const { tickets } = store({
      ['rt:ticket:' + T]: JSON.stringify({ userId: U1, workspaceId: 'x:y' }),
      ['rt:ticket:' + 'B'.repeat(43)]: 'not json',
    })
    assert.equal(await tickets.consume(T), null)
    assert.equal(await tickets.consume('B'.repeat(43)), null)
  })
})
