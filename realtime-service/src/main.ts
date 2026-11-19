import 'reflect-metadata'
import { NestFactory } from '@nestjs/core'
import { IoAdapter } from '@nestjs/platform-socket.io'
import Redis from 'ioredis'
import { AppModule } from './app.module'
import { EVENT_SOURCE, TICKET_STORE } from './ports'
import { RedisEventSource, RedisTicketStore } from './redis'

async function bootstrap() {
  const url = process.env.REDIS_URL ?? 'redis://localhost:6379'
  const channel = process.env.REALTIME_CHANNEL ?? 'rt:events'
  const commands = new Redis(url, { lazyConnect: false })
  const subscriber = new Redis(url)
  const app = await NestFactory.create(
    AppModule.with([
      { provide: TICKET_STORE, useValue: new RedisTicketStore(commands) },
      { provide: EVENT_SOURCE, useValue: new RedisEventSource(subscriber, channel) },
    ]),
  )
  app.useWebSocketAdapter(new IoAdapter(app))
  app.enableShutdownHooks()
  await app.listen(Number(process.env.PORT ?? 3001))
}

void bootstrap()
