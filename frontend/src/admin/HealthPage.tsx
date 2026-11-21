import { HeartPulse, RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime, formatNumber } from '@/lib/format'
import { formatBytes, useHealth, type HealthStatus } from './api'
import { QueryState, Stat } from './parts'

function statusBadge(status: HealthStatus) {
  return status === 'UP' ? 'secondary' : status === 'NOT_CHECKED' ? 'outline' : 'destructive'
}

function duration(seconds: number) {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return [d ? `${d}d` : '', h ? `${h}h` : '', `${m}m`].filter(Boolean).join(' ')
}

/** Live checks made by the backend on each refresh (every 30 s while open). */
export function HealthPage() {
  const { t } = useTranslation()
  const health = useHealth()
  const h = health.data

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">{t('admin.health.title')}</h1>
          {h ? <p className="text-sm text-muted-foreground">{t('admin.health.checked', { date: formatDateTime(h.checkedAt) })}</p> : null}
        </div>
        <Button variant="outline" size="sm" onClick={() => void health.refetch()} disabled={health.isFetching}>
          <RefreshCw className={health.isFetching ? 'animate-spin' : undefined} />
          {t('admin.refresh')}
        </Button>
      </div>
      <QueryState pending={health.isPending} error={health.error} empty={false} emptyIcon={HeartPulse} emptyTitle="" onRetry={() => void health.refetch()}>
        {h ? (
          <>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('admin.health.overall')}</span>
              <Badge variant={statusBadge(h.overall)}>{t(`admin.health.status.${h.overall}`)}</Badge>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {h.components.map((c) => (
                <Card key={c.name}>
                  <CardHeader className="flex flex-row items-center justify-between gap-2">
                    <CardTitle className="text-base">{t(`admin.health.components.${c.name}`, { defaultValue: c.name })}</CardTitle>
                    <Badge variant={statusBadge(c.status)}>{t(`admin.health.status.${c.status}`)}</Badge>
                  </CardHeader>
                  <CardContent className="text-sm text-muted-foreground">
                    {c.latencyMs != null && c.name !== 'application' ? t('admin.health.latency', { ms: c.latencyMs }) : null}
                    {c.name === 'application' ? t('admin.health.uptime', { value: duration(h.runtime.uptimeSeconds) }) : null}
                    {c.status === 'NOT_CHECKED' ? t('admin.health.notChecked') : c.status === 'DOWN' ? ` — ${t('admin.health.unreachable')}` : null}
                  </CardContent>
                </Card>
              ))}
            </div>

            <h2 className="pt-2 text-lg font-medium">{t('admin.health.metrics')}</h2>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Stat label={t('admin.health.requests')} value={h.requests.total} hint={t('admin.health.sinceStart', { date: formatDateTime(h.runtime.startedAt) })} />
              <Stat label={t('admin.health.serverErrors')} value={h.requests.serverErrors} tone={h.requests.serverErrors ? 'bad' : undefined} />
              <Stat label={t('admin.health.clientErrors')} value={h.requests.clientErrors} hint={t('admin.health.clientErrorsHint')} />
              <Stat
                label={t('admin.health.memory')}
                value={formatBytes(h.runtime.heapUsedBytes)}
                hint={h.runtime.heapMaxBytes ? t('admin.health.of', { value: formatBytes(h.runtime.heapMaxBytes) }) : undefined}
              />
              {h.database ? (
                <>
                  <Stat label={t('admin.health.schema')} value={h.database.schemaVersion ?? '—'} />
                  <Stat label={t('admin.health.connections')} value={h.database.connections} />
                  <Stat label={t('admin.health.pendingEvents')} value={h.database.pendingEvents} tone={h.database.pendingEvents > 50 ? 'warn' : undefined} hint={t('admin.health.pendingEventsHint')} />
                </>
              ) : null}
              <Stat label={t('admin.health.threads')} value={h.runtime.threads} hint={t('admin.health.processors', { count: h.runtime.processors })} />
            </div>
            <p className="text-xs text-muted-foreground">{t('admin.health.note', { count: formatNumber(h.components.length) })}</p>
          </>
        ) : null}
      </QueryState>
    </div>
  )
}
