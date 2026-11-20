import { AlarmClock, Pause, Play, Wrench } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAuth } from '@/auth/auth-context'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { useSubscription } from '@/lib/subscription'
import { useCustomerOptions } from '@/pages/jobs/api'
import { usePlans, useOverdue, useSetPlanStatus, type Plan } from './api'
import { NewPlanDialog } from './NewPlanDialog'

const STATUS_FILTERS = ['ACTIVE', 'PAUSED', 'ENDED', ''] as const

function OverdueAlert() {
  const { t } = useTranslation()
  const overdue = useOverdue()
  const items = overdue.data ?? []
  if (items.length === 0) return null
  return (
    <Alert variant="destructive">
      <AlarmClock />
      <AlertTitle>{t('maintenance.overdueTitle', { count: items.length })}</AlertTitle>
      <AlertDescription>
        <ul className="flex flex-col gap-1">
          {items.map((o) => (
            <li key={o.id}>
              {o.jobId ? (
                <Link to={`/jobs/${o.jobId}`} className="underline underline-offset-4">
                  {o.planTitle}
                </Link>
              ) : (
                o.planTitle
              )}{' '}
              — {t('maintenance.dueOn', { date: formatDate(o.dueDate) })}
            </li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  )
}

function StatusToggle({ plan, disabled }: { plan: Plan; disabled: boolean }) {
  const { t } = useTranslation()
  const setStatus = useSetPlanStatus()
  if (plan.status === 'ENDED') return null
  const next = plan.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
  return (
    <Button
      size="sm"
      variant="outline"
      disabled={disabled || setStatus.isPending}
      onClick={() =>
        setStatus.mutate(
          { id: plan.id, status: next },
          {
            onSuccess: () => toast.success(t(next === 'PAUSED' ? 'maintenance.paused' : 'maintenance.resumed')),
            onError: (e) => toast.error(e instanceof ApiError ? e.message : t('errors.generic')),
          },
        )
      }
    >
      {next === 'PAUSED' ? <Pause /> : <Play />}
      {t(next === 'PAUSED' ? 'maintenance.pause' : 'maintenance.resume')}
    </Button>
  )
}

export function MaintenancePage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { data: subscription } = useSubscription()
  const [status, setStatus] = useState<string>('ACTIVE')
  const [page, setPage] = useState(0)
  const plans = usePlans(status, page)
  const customers = useCustomerOptions()
  const canWrite = can('ops:write') && !subscription?.readOnly
  const customerName = new Map((customers.data?.content ?? []).map((c) => [c.id, c.fullName]))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('maintenance.title')}</h1>
        <div className="flex-1" />
        {can('ops:write') ? <NewPlanDialog disabled={!canWrite} /> : null}
      </div>
      <OverdueAlert />
      <Tabs
        value={status}
        onValueChange={(v) => {
          setStatus(v)
          setPage(0)
        }}
      >
        <TabsList>
          {STATUS_FILTERS.map((s) => (
            <TabsTrigger key={s || 'all'} value={s}>
              {s ? t(`maintenance.statuses.${s}`) : t('notifications.all')}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {plans.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : plans.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : plans.data.content.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Wrench />
            </EmptyMedia>
            <EmptyTitle>{t('maintenance.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{t('maintenance.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('maintenance.planTitle')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('jobs.customer')}</TableHead>
                  <TableHead>{t('maintenance.frequency')}</TableHead>
                  <TableHead>{t('maintenance.nextDue')}</TableHead>
                  <TableHead className="hidden sm:table-cell">{t('jobs.status')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {plans.data.content.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{p.title}</TableCell>
                    <TableCell className="hidden md:table-cell">
                      <Link to={`/customers/${p.contactId}`} className="underline-offset-4 hover:underline">
                        {customerName.get(p.contactId) ?? '—'}
                      </Link>
                    </TableCell>
                    <TableCell>
                      {p.frequency === 'CUSTOM'
                        ? t('maintenance.everyDays', { count: p.intervalDays ?? 0 })
                        : t(`maintenance.frequencies.${p.frequency}`)}
                    </TableCell>
                    <TableCell>{p.nextDueDate ? formatDate(p.nextDueDate) : '—'}</TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <Badge variant={p.status === 'ACTIVE' ? 'default' : 'secondary'}>
                        {t(`maintenance.statuses.${p.status}`)}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-end">
                      {can('ops:write') ? <StatusToggle plan={p} disabled={!canWrite} /> : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          {plans.data.totalPages > 1 ? (
            <div className="flex items-center justify-end gap-2 text-sm">
              <span className="text-muted-foreground">
                {t('common.page', { page: plans.data.page + 1, pages: plans.data.totalPages })}
              </span>
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((x) => x - 1)}>
                {t('common.previous')}
              </Button>
              <Button size="sm" variant="outline" disabled={page + 1 >= plans.data.totalPages} onClick={() => setPage((x) => x + 1)}>
                {t('common.next')}
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
