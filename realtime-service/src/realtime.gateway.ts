import { Inject, type OnModuleDestroy, type OnModuleInit } from '@nestjs/common'
import {
  type OnGatewayConnection,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets'
import type { Server, Socket } from 'socket.io'
import { EVENT_SOURCE, TICKET_STORE, type EventSource, type RelayMessage, type TicketStore } from './ports'

export const SOCKET_PATH = '/realtime/socket.io'

const userRoom = (userId: string, workspaceId: string) => `user:${userId}:${workspaceId}`

/**
 * Browsers connect with a one-time ticket (handshake `auth.ticket`) and are placed in their
 * workspace room and their own user room — nothing else. Events carry ids only; clients refetch
 * through the Java API, which applies their permissions (ADR-0015).
 */
@WebSocketGateway({ path: SOCKET_PATH, cors: { origin: (process.env.ALLOWED_ORIGINS ?? 'http://localhost:5173').split(',') } })
export class RealtimeGateway implements OnGatewayConnection, OnModuleInit, OnModuleDestroy {

  @WebSocketServer()
  server!: Server

  constructor(
    @Inject(TICKET_STORE) private readonly tickets: TicketStore,
    @Inject(EVENT_SOURCE) private readonly events: EventSource,
  ) {}

  async onModuleInit() {
    await this.events.subscribe((m) => this.dispatch(m))
  }

  async onModuleDestroy() {
    await this.events.close()
  }

  async handleConnection(client: Socket) {
    const ticket = (client.handshake.auth as { ticket?: unknown } | undefined)?.ticket
    const claims = typeof ticket === 'string' ? await this.tickets.consume(ticket).catch(() => null) : null
    if (!claims) {
      client.emit('unauthorized')
      client.disconnect(true)
      return
    }
    client.data.claims = claims
    await client.join([`ws:${claims.workspaceId}`, userRoom(claims.userId, claims.workspaceId)])
    client.emit('ready')
  }

  dispatch(message: RelayMessage) {
    if (message.kind === 'notification') {
      // per user *and* workspace: a user signed in to two workspaces only hears about the right one
      const rooms = message.userIds.map((u) => userRoom(u, message.workspaceId))
      if (rooms.length > 0) this.server.to(rooms).emit('notification')
      return
    }
    const { entityType, entityId, jobId, action } = message
    this.server.to(`ws:${message.workspaceId}`).emit('changed', { entityType, entityId, jobId, action })
  }
}
