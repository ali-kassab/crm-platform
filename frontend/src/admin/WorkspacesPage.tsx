import { Building2 } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { statusVariant, SUBSCRIPTION_STATUSES, useWorkspaces } from './api'

const ALL = 'ALL'

export function WorkspacesPage() {
  const { t } = useTranslation()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState(ALL)
  const query = useDeferredValue(q.trim())
  const workspaces = useWorkspaces(status === ALL ? '' : status, query)

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('admin.workspaces.title')}</h1>
      <div className="flex flex-wrap gap-2">
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={t('admin.workspaces.search')}
          aria-label={t('common.search')}
          className="max-w-xs bg-background"
        />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-44 bg-background" aria-label={t('admin.workspaces.status')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.workspaces.allStatuses')}</SelectItem>
            {SUBSCRIPTION_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`subscription.status.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {workspaces.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : workspaces.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : workspaces.data.length === 0 ? (
        <Empty className="border bg-background">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Building2 />
            </EmptyMedia>
            <EmptyTitle>{t('admin.workspaces.empty')}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      ) : (
        <ul className="flex flex-col divide-y rounded-md border bg-background">
          {workspaces.data.map((w) => (
            <li key={w.id} className="flex flex-col gap-1 px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <Link to={`/admin/workspaces/${w.id}`} className="font-medium underline-offset-4 hover:underline">
                  {w.name}
                </Link>
                {w.status ? <Badge variant={statusVariant(w.status)}>{t(`subscription.status.${w.status}`)}</Badge> : null}
                {w.planCode ? <Badge variant="outline">{w.planCode}</Badge> : null}
                {w.deletedAt ? <Badge variant="destructive">{t('admin.workspaces.deleted')}</Badge> : null}
              </div>
              <p className="text-sm text-muted-foreground">
                {[
                  w.ownerEmail,
                  t('admin.workspaces.members', { count: w.members }),
                  w.currentPeriodEnd ? t('admin.workspaces.periodEnds', { date: formatDate(w.currentPeriodEnd) }) : null,
                  t('admin.workspaces.created', { date: formatDate(w.createdAt) }),
                ]
                  .filter(Boolean)
                  .join('، ')}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
