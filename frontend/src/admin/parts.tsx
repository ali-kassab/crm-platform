import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { statusVariant } from './api'

/** Loading, error and empty states shared by every admin list. */
export function QueryState({
  pending,
  error,
  empty,
  emptyIcon: Icon,
  emptyTitle,
  emptyHint,
  onRetry,
  children,
}: {
  pending: boolean
  error: unknown
  empty: boolean
  emptyIcon: LucideIcon
  emptyTitle: string
  emptyHint?: string
  onRetry?: () => void
  children: ReactNode
}) {
  const { t } = useTranslation()
  if (pending) return <Skeleton className="h-48 w-full" />
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription className="flex flex-wrap items-center gap-3">
          {error instanceof ApiError ? error.message : t('errors.generic')}
          {onRetry ? (
            <Button size="sm" variant="outline" onClick={onRetry}>
              {t('admin.retry')}
            </Button>
          ) : null}
        </AlertDescription>
      </Alert>
    )
  }
  if (empty) {
    return (
      <Empty className="border bg-background">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <Icon />
          </EmptyMedia>
          <EmptyTitle>{emptyTitle}</EmptyTitle>
          {emptyHint ? <EmptyDescription>{emptyHint}</EmptyDescription> : null}
        </EmptyHeader>
      </Empty>
    )
  }
  return <>{children}</>
}

export function Pager({ page, pages, total, onPage }: { page: number; pages: number; total?: number; onPage: (page: number) => void }) {
  const { t } = useTranslation()
  if (pages <= 1 && total == null) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span className="text-sm text-muted-foreground">{total != null ? t('admin.results', { count: total }) : null}</span>
      {pages > 1 ? (
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
            {t('common.previous')}
          </Button>
          <span className="text-sm text-muted-foreground">{t('common.page', { page: page + 1, pages })}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
            {t('common.next')}
          </Button>
        </div>
      ) : null}
    </div>
  )
}

/** A number with a label; `null` means the metric is not measured (shown as such, never as 0). */
export function Stat({ label, value, hint, tone }: { label: string; value: number | string | null; hint?: string; tone?: 'warn' | 'bad' }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-1 rounded-lg border bg-background p-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span
        className={cn(
          'text-2xl font-semibold tabular-nums',
          tone === 'bad' && 'text-destructive',
          tone === 'warn' && 'text-amber-600 dark:text-amber-400',
          value == null && 'text-base font-normal text-muted-foreground',
        )}
      >
        {value == null ? t('admin.notMeasured') : typeof value === 'number' ? formatNumber(value) : value}
      </span>
      {hint ? <span className="text-xs text-muted-foreground">{hint}</span> : null}
    </div>
  )
}

export function CompanyStatus({ status, deleted }: { status: string | null; deleted?: boolean }) {
  const { t } = useTranslation()
  if (deleted) return <Badge variant="destructive">{t('admin.workspaces.deleted')}</Badge>
  if (!status) return <Badge variant="outline">{t('admin.companies.noSubscription')}</Badge>
  return <Badge variant={statusVariant(status)}>{t(`subscription.status.${status}`)}</Badge>
}
