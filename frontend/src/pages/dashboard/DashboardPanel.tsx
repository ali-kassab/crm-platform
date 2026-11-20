import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api'
import { formatMoney, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { PERIODS, useDashboard, type Period } from './api'

interface Tile {
  label: string
  value: string
  /** Counts that need someone's attention are highlighted only while they are above zero. */
  attention?: boolean
  to?: string
}

/** Workspace overview for owners, admins and finance (reports:read), R12. */
export default function DashboardPanel() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const [period, setPeriod] = useState<Period>('MONTH')
  const { data, isPending, isError, error, isPlaceholderData } = useDashboard(period)
  const currency = session?.workspace.currency

  const groups: { title: string; tiles: Tile[] }[] = data
    ? [
        {
          title: t('dashboard.jobs'),
          tiles: [
            { label: t('dashboard.openJobs'), value: formatNumber(data.jobs.open), to: '/jobs' },
            { label: t('dashboard.slaBreached'), value: formatNumber(data.jobs.slaBreached), attention: data.jobs.slaBreached > 0, to: '/jobs' },
            { label: t('dashboard.jobsCreated'), value: formatNumber(data.jobs.created) },
            { label: t('dashboard.jobsCompleted'), value: formatNumber(data.jobs.completed) },
          ],
        },
        {
          title: t('dashboard.visits'),
          tiles: [
            { label: t('dashboard.visitsToday'), value: formatNumber(data.visits.today), to: '/visits' },
            { label: t('dashboard.visitsOverdue'), value: formatNumber(data.visits.overdue), attention: data.visits.overdue > 0 },
            { label: t('dashboard.visitsCompleted'), value: formatNumber(data.visits.completed) },
            { label: t('dashboard.visitsMissed'), value: formatNumber(data.visits.missed), attention: data.visits.missed > 0 },
          ],
        },
        {
          title: t('dashboard.maintenance'),
          tiles: [
            { label: t('dashboard.maintenanceOverdue'), value: formatNumber(data.maintenance.overdue), attention: data.maintenance.overdue > 0 },
            { label: t('dashboard.warrantiesExpiring'), value: formatNumber(data.maintenance.warrantiesExpiringSoon) },
          ],
        },
        ...(data.finance
          ? [
              {
                title: t('dashboard.finance'),
                tiles: [
                  { label: t('dashboard.received'), value: formatMoney(data.finance.received, currency) },
                  { label: t('dashboard.paid'), value: formatMoney(data.finance.paid, currency) },
                  { label: t('dashboard.outstanding'), value: formatMoney(data.finance.outstanding, currency), to: '/finance' },
                ],
              },
            ]
          : []),
      ]
    : []

  return (
    <section aria-labelledby="dashboard-title" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="dashboard-title" className="text-lg font-semibold">
          {t('dashboard.title')}
        </h2>
        <Tabs value={period} onValueChange={(v) => setPeriod(v as Period)}>
          <TabsList>
            {PERIODS.map((p) => (
              <TabsTrigger key={p} value={p}>
                {t(`dashboard.periods.${p}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {isError ? (
        <Alert variant="destructive">
          <AlertDescription>{error instanceof ApiError ? error.message : t('errors.generic')}</AlertDescription>
        </Alert>
      ) : isPending ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : (
        <div className={cn('flex flex-col gap-5 transition-opacity', isPlaceholderData && 'opacity-60')} aria-busy={isPlaceholderData}>
          {groups.map((g) => (
            <div key={g.title} className="flex flex-col gap-2">
              <h3 className="text-sm font-medium text-muted-foreground">{g.title}</h3>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {g.tiles.map((tile) => (
                  <TileCard key={tile.label} tile={tile} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function TileCard({ tile }: { tile: Tile }) {
  const body = (
    <Card size="sm" className={cn('h-full', tile.to && 'transition-colors hover:bg-muted/50')}>
      <CardHeader>
        <CardTitle className={cn('text-sm font-normal text-muted-foreground', tile.attention && 'text-destructive')}>
          {tile.label}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className={cn('text-2xl font-semibold tabular-nums', tile.attention && 'text-destructive')}>{tile.value}</p>
      </CardContent>
    </Card>
  )
  return tile.to ? (
    <Link to={tile.to} className="rounded-xl focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
      {body}
    </Link>
  ) : (
    body
  )
}
