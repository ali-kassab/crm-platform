import { Controller, Get, Module, type DynamicModule, type Provider } from '@nestjs/common'
import { RealtimeGateway } from './realtime.gateway'

@Controller()
class HealthController {
  @Get('health')
  health() {
    return { status: 'UP' }
  }
}

/** Ports (ticket store, event source) are injected so tests can swap Redis for in-memory fakes. */
@Module({})
export class AppModule {
  static with(ports: Provider[]): DynamicModule {
    return { module: AppModule, controllers: [HealthController], providers: [...ports, RealtimeGateway] }
  }
}
