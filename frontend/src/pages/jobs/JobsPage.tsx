import { ClipboardList, Search } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate } from '@/lib/format'
import { useSubscription } from '@/lib/subscription'
import { JOB_STATUSES, useJobs } from './api'
import { PriorityBadge, StatusBadge } from './JobBadges'
import { NewJobDialog } from './NewJobDialog'

export function JobsPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { data: subscription } = useSubscription()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const query = useDeferredValue(q)
  const { data, isPending, isError, refetch } = useJobs({ q: query, status: status === 'ALL' ? '' : status, page })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('jobs.title')}</h1>
        <div className="flex-1" />
        {can('ops:write') && <NewJobDialog disabled={subscription?.readOnly} />}
      </div>
      <div className="flex flex-wrap gap-2">
        <div className="relative w-full max-w-sm">
          <Search className="pointer-events-none absolute start-2.5 top-2.5 size-4 text-muted-foreground" />
          <Input
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setPage(0)
            }}
            placeholder={t('jobs.searchPlaceholder')}
            aria-label={t('common.search')}
            className="ps-8"
          />
        </div>
        <Select
          value={status}
          onValueChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-44" aria-label={t('jobs.status')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('jobs.all')}</SelectItem>
            {JOB_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`jobs.statuses.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center gap-3">
            {t('errors.generic')}
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              {t('common.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      ) : isPending ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <ClipboardList />
            </EmptyMedia>
            <EmptyTitle>{t('jobs.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{t('jobs.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('jobs.workTitle')}</TableHead>
                  <TableHead>{t('jobs.customer')}</TableHead>
                  <TableHead>{t('jobs.status')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('jobs.priority')}</TableHead>
                  <TableHead className="hidden lg:table-cell">{t('jobs.start')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((j) => (
                  <TableRow key={j.id}>
                    <TableCell className="font-medium">
                      <Link to={`/jobs/${j.id}`} className="hover:underline">
                        {j.title}
                      </Link>
                      {j.sla?.breached && (
                        <Badge variant="destructive" className="ms-2">
                          {t('jobs.slaBreached')}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>{j.customer?.name}</TableCell>
                    <TableCell>
                      <StatusBadge status={j.status} />
                    </TableCell>
                    <TableCell className="hidden md:table-cell">
                      <PriorityBadge priority={j.priority} />
                    </TableCell>
                    <TableCell className="hidden lg:table-cell">{formatDate(j.startDate)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-2 text-sm">
              <span className="text-muted-foreground">
                {t('common.page', { page: data.page + 1, pages: data.totalPages })}
              </span>
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                {t('common.previous')}
              </Button>
              <Button size="sm" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
                {t('common.next')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
