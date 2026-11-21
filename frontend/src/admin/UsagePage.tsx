import { BarChart3, CheckCircle2, CircleSlash } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime, formatNumber } from '@/lib/format'
import { formatBytes, useUsage } from './api'
import { CompanyStatus, Pager, QueryState, Stat } from './parts'

const SORTS = ['storage,desc', 'users,desc', 'customers,desc', 'workOrders,desc', 'projects,desc'] as const

/** Aggregate resource use per company: counts and sizes only, never the records themselves. */
export function UsagePage() {
  const { t } = useTranslation()
  const [q, setQ] = useState('')
  const [sort, setSort] = useState<string>(SORTS[0])
  const [page, setPage] = useState(0)
  const query = useDeferredValue(q.trim())
  const usage = useUsage({ q: query, page, sort })
  const data = usage.data

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold">{t('admin.usage.title')}</h1>
        <p className="text-sm text-muted-foreground">{t('admin.usage.hint')}</p>
      </div>
      <QueryState pending={usage.isPending} error={usage.error} empty={false} emptyIcon={BarChart3} emptyTitle="" onRetry={() => void usage.refetch()}>
        {data ? (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
              <Stat label={t('admin.usage.users')} value={data.totals.activeUsers} />
              <Stat label={t('admin.usage.customers')} value={data.totals.customers} />
              <Stat label={t('admin.usage.projects')} value={data.totals.projects} />
              <Stat label={t('admin.usage.workOrders')} value={data.totals.workOrders} hint={t('admin.usage.thisMonth', { count: data.totals.workOrdersThisMonth })} />
              <Stat label={t('admin.usage.storage')} value={formatBytes(data.totals.storageBytes)} hint={t('admin.usage.filesCount', { count: data.totals.files })} />
              <Stat label={t('admin.usage.apiRequests')} value={data.totals.apiRequests30d} />
            </div>

            <div className="flex flex-wrap gap-2">
              <Input
                value={q}
                onChange={(e) => {
                  setQ(e.target.value)
                  setPage(0)
                }}
                placeholder={t('admin.workspaces.search')}
                aria-label={t('common.search')}
                className="max-w-xs bg-background"
              />
              <Select
                value={sort}
                onValueChange={(v) => {
                  setSort(v)
                  setPage(0)
                }}
              >
                <SelectTrigger className="w-52 bg-background" aria-label={t('admin.sortBy')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SORTS.map((s) => (
                    <SelectItem key={s} value={s}>
                      {t(`admin.usage.sorts.${s.split(',')[0]}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {data.companies.content.length === 0 ? (
              <p className="rounded-md border bg-background p-6 text-center text-sm text-muted-foreground">{t('admin.workspaces.empty')}</p>
            ) : (
              <div className="overflow-x-auto rounded-md border bg-background">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('admin.companies.company')}</TableHead>
                      <TableHead>{t('admin.workspaces.status')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.users')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.customers')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.projects')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.workOrders')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.storage')}</TableHead>
                      <TableHead className="text-end">{t('admin.usage.apiRequests')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.companies.content.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          <Link to={`/admin/companies/${c.id}`} className="font-medium underline-offset-4 hover:underline">
                            {c.name}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <CompanyStatus status={c.status} deleted={!!c.deletedAt} />
                        </TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(c.usage.activeUsers)}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(c.usage.customers)}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(c.usage.projects)}</TableCell>
                        <TableCell className="text-end tabular-nums">{formatNumber(c.usage.workOrders)}</TableCell>
                        <TableCell className="text-end tabular-nums" dir="ltr">
                          {formatBytes(c.usage.storageBytes)}
                        </TableCell>
                        <TableCell className="text-end tabular-nums">
                          {c.usage.apiRequests30d == null ? t('admin.notMeasured') : formatNumber(c.usage.apiRequests30d)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
            <Pager page={page} pages={data.companies.totalPages} total={data.companies.totalElements} onPage={setPage} />

            <Card>
              <CardHeader>
                <CardTitle>{t('admin.usage.metricsTitle')}</CardTitle>
                <CardDescription>{t('admin.usage.metricsHint', { date: formatDateTime(data.generatedAt) })}</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="flex flex-col gap-2 text-sm">
                  {data.metrics.map((m) => (
                    <li key={m.key} className="flex items-start gap-2">
                      {m.available ? (
                        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-label={t('admin.usage.measured')} />
                      ) : (
                        <CircleSlash className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-label={t('admin.notMeasured')} />
                      )}
                      <span>
                        <span className="font-medium">{t(`admin.usage.metric.${m.key}`, { defaultValue: m.key })}</span>
                        {' — '}
                        <span className="text-muted-foreground">{t(`admin.usage.metricHelp.${m.key}`, { defaultValue: m.description })}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </>
        ) : null}
      </QueryState>
    </div>
  )
}
