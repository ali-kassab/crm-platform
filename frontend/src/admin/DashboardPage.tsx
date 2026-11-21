import { Activity as ActivityIcon, RefreshCw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime, formatMoney } from '@/lib/format'
import { formatBytes, useDashboard, type Activity } from './api'
import { QueryState, Stat } from './parts'

/** Platform overview: real counts from the database, nothing estimated. */
export function DashboardPage() {
  const { t } = useTranslation()
  const dashboard = useDashboard()
  const d = dashboard.data

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">{t('admin.dashboard.title')}</h1>
          {d ? <p className="text-sm text-muted-foreground">{t('admin.dashboard.generated', { date: formatDateTime(d.generatedAt) })}</p> : null}
        </div>
        <Button variant="outline" size="sm" onClick={() => void dashboard.refetch()} disabled={dashboard.isFetching}>
          <RefreshCw className={dashboard.isFetching ? 'animate-spin' : undefined} />
          {t('admin.refresh')}
        </Button>
      </div>

      <QueryState pending={dashboard.isPending} error={dashboard.error} empty={false} emptyIcon={ActivityIcon} emptyTitle="" onRetry={() => void dashboard.refetch()}>
        {d ? (
          <>
            <section className="flex flex-col gap-3" aria-labelledby="dash-companies">
              <h2 id="dash-companies" className="text-lg font-medium">
                {t('admin.dashboard.companies')}
              </h2>
              <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                <Stat label={t('admin.dashboard.totalCompanies')} value={d.companies.total} hint={t('admin.dashboard.newIn30', { count: d.companies.newLast30Days })} />
                <Stat label={t('admin.dashboard.operational')} value={d.companies.operational} hint={t('admin.dashboard.operationalHint', { trial: d.companies.trial, paid: d.companies.paid, grace: d.companies.grace })} />
                <Stat label={t('admin.dashboard.suspended')} value={d.companies.suspended} tone={d.companies.suspended ? 'bad' : undefined} />
                <Stat label={t('admin.dashboard.expired')} value={d.companies.expired} tone={d.companies.expired ? 'warn' : undefined} hint={t('admin.dashboard.endingSoon', { count: d.companies.endingWithin7Days })} />
              </div>
              <p className="text-xs text-muted-foreground">{t('admin.dashboard.pendingNote')}</p>
            </section>

            <section className="flex flex-col gap-3" aria-labelledby="dash-users">
              <h2 id="dash-users" className="text-lg font-medium">
                {t('admin.dashboard.usersAndBilling')}
              </h2>
              <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                <Stat label={t('admin.dashboard.totalUsers')} value={d.users.total} hint={t('admin.dashboard.newIn30', { count: d.users.newLast30Days })} />
                <Stat label={t('admin.dashboard.activeUsers')} value={d.users.active} hint={t('admin.dashboard.signedIn30', { count: d.users.signedInLast30Days })} />
                <Stat label={t('admin.dashboard.pendingPayments')} value={d.payments.pending} tone={d.payments.pending ? 'warn' : undefined} hint={t('admin.dashboard.approvedPayments', { count: d.payments.approved })} />
                <div className="flex flex-col gap-1 rounded-lg border bg-background p-4">
                  <span className="text-sm text-muted-foreground">{t('admin.dashboard.revenue')}</span>
                  {d.payments.revenue.length === 0 ? (
                    <span className="text-base text-muted-foreground">{t('admin.dashboard.noRevenue')}</span>
                  ) : (
                    d.payments.revenue.map((r) => (
                      <span key={r.currency} className="flex flex-col">
                        <span className="text-2xl font-semibold tabular-nums">{formatMoney(r.total, r.currency)}</span>
                        <span className="text-xs text-muted-foreground">{t('admin.dashboard.revenue30', { amount: formatMoney(r.last30Days, r.currency) })}</span>
                      </span>
                    ))
                  )}
                </div>
              </div>
              <p className="text-xs text-muted-foreground">{t('admin.dashboard.revenueNote')}</p>
            </section>

            <section className="flex flex-col gap-3" aria-labelledby="dash-usage">
              <div className="flex items-center justify-between gap-2">
                <h2 id="dash-usage" className="text-lg font-medium">
                  {t('admin.dashboard.usage')}
                </h2>
                <Link to="/admin/usage" className="text-sm text-primary underline-offset-4 hover:underline">
                  {t('admin.dashboard.perCompany')}
                </Link>
              </div>
              <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
                <Stat label={t('admin.usage.customers')} value={d.usage.customers} />
                <Stat label={t('admin.usage.projects')} value={d.usage.projects} />
                <Stat label={t('admin.usage.workOrders')} value={d.usage.workOrders} />
                <Stat label={t('admin.usage.storage')} value={formatBytes(d.usage.storageBytes)} hint={t('admin.usage.filesCount', { count: d.usage.files })} />
                <Stat label={t('admin.usage.apiRequests')} value={d.usage.apiRequests30d} />
              </div>
            </section>

            <Card>
              <CardHeader>
                <CardTitle>{t('admin.dashboard.activity')}</CardTitle>
                <CardDescription>{t('admin.dashboard.activityHint')}</CardDescription>
              </CardHeader>
              <CardContent>
                <ActivityList items={d.recentActivity} />
              </CardContent>
            </Card>
          </>
        ) : null}
      </QueryState>
    </div>
  )
}

export function ActivityList({ items }: { items: Activity[] }) {
  const { t } = useTranslation()
  if (items.length === 0) return <p className="text-sm text-muted-foreground">{t('common.empty')}</p>
  return (
    <ol className="flex flex-col divide-y text-sm">
      {items.map((a, i) => (
        <li key={`${a.occurredAt}-${a.type}-${i}`} className="flex flex-wrap items-center gap-x-2 gap-y-1 py-2">
          <span className="font-medium">{t(`admin.activity.${a.type}`, { defaultValue: a.type })}</span>
          {a.companyId ? (
            <Link to={`/admin/companies/${a.companyId}`} className="underline-offset-4 hover:underline">
              {a.companyName}
            </Link>
          ) : null}
          {a.fromStatus && a.toStatus && a.fromStatus !== a.toStatus ? (
            <span className="text-muted-foreground">
              {t('admin.statusChange', { from: t(`subscription.status.${a.fromStatus}`), to: t(`subscription.status.${a.toStatus}`) })}
            </span>
          ) : null}
          <span className="text-muted-foreground" dir={a.actorEmail ? 'ltr' : undefined}>
            {a.actorEmail ?? t(`admin.actors.${a.actorKind}`, { defaultValue: a.actorKind })}
          </span>
          <span className="ms-auto text-muted-foreground">{formatDateTime(a.occurredAt)}</span>
        </li>
      ))}
    </ol>
  )
}
