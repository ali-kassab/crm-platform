import { ScrollText } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatDateTime } from '@/lib/format'
import { useAuditActions, useAuditLog, type AuditRow } from './api'
import { Pager, QueryState } from './parts'

const ALL = 'ALL'
const ENTITY_TYPES = ['PLATFORM_ADMIN', 'USER', 'SUBSCRIPTION', 'SUBSCRIPTION_REQUEST', 'PLAN', 'PAYMENT_METHOD'] as const

/** Platform-level audit trail (append-only in the database; nothing here edits or deletes it). */
export function AuditPage() {
  const { t } = useTranslation()
  const [action, setAction] = useState(ALL)
  const [entityType, setEntityType] = useState(ALL)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const actions = useAuditActions()
  const log = useAuditLog({
    action: action === ALL ? '' : action,
    entityType: entityType === ALL ? '' : entityType,
    from: from ? new Date(`${from}T00:00:00`).toISOString() : '',
    to: to ? new Date(new Date(`${to}T00:00:00`).getTime() + 86_400_000).toISOString() : '',
    page,
  })
  const data = log.data
  const reset = <T,>(set: (v: T) => void) => (v: T) => {
    set(v)
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold">{t('admin.audit.title')}</h1>
        <p className="text-sm text-muted-foreground">{t('admin.audit.hint')}</p>
      </div>
      <div className="flex flex-wrap items-end gap-2">
        <Select value={action} onValueChange={reset(setAction)}>
          <SelectTrigger className="w-56 bg-background" aria-label={t('admin.audit.action')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.audit.allActions')}</SelectItem>
            {(actions.data ?? []).map((a) => (
              <SelectItem key={a} value={a}>
                {t(`admin.activity.${a}`, { defaultValue: a })}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={entityType} onValueChange={reset(setEntityType)}>
          <SelectTrigger className="w-48 bg-background" aria-label={t('admin.audit.entity')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.audit.allEntities')}</SelectItem>
            {ENTITY_TYPES.map((e) => (
              <SelectItem key={e} value={e}>
                {t(`admin.audit.entities.${e}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="flex flex-col gap-1">
          <Label htmlFor="audit-from" className="text-xs text-muted-foreground">
            {t('admin.audit.from')}
          </Label>
          <Input id="audit-from" type="date" value={from} onChange={(e) => reset(setFrom)(e.target.value)} className="w-40 bg-background" dir="ltr" />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="audit-to" className="text-xs text-muted-foreground">
            {t('admin.audit.to')}
          </Label>
          <Input id="audit-to" type="date" value={to} onChange={(e) => reset(setTo)(e.target.value)} className="w-40 bg-background" dir="ltr" />
        </div>
      </div>

      <QueryState pending={log.isPending} error={log.error} empty={!!data && data.content.length === 0} emptyIcon={ScrollText} emptyTitle={t('admin.audit.empty')} onRetry={() => void log.refetch()}>
        {data ? (
          <>
            <ol className="flex flex-col divide-y rounded-md border bg-background">
              {data.content.map((e) => (
                <AuditItem key={e.id} entry={e} />
              ))}
            </ol>
            <Pager page={page} pages={data.totalPages} total={data.totalElements} onPage={setPage} />
          </>
        ) : null}
      </QueryState>
    </div>
  )
}

function AuditItem({ entry: e }: { entry: AuditRow }) {
  const { t } = useTranslation()
  const changes = Object.entries(e.changes ?? {})
  return (
    <li className="flex flex-col gap-1 px-4 py-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{t(`admin.activity.${e.action}`, { defaultValue: e.action })}</span>
        <Badge variant="outline">{t(`admin.audit.entities.${e.entityType}`, { defaultValue: e.entityType })}</Badge>
        <span className="text-muted-foreground" dir={e.actorEmail ? 'ltr' : undefined}>
          {e.actorEmail ?? (e.actorLabel ? t(`admin.audit.labels.${e.actorLabel}`, { defaultValue: e.actorLabel }) : t('admin.actors.SYSTEM'))}
        </span>
        <span className="ms-auto text-muted-foreground">{formatDateTime(e.occurredAt)}</span>
      </div>
      {changes.length ? (
        <dl className="grid gap-x-3 gap-y-0.5 text-xs text-muted-foreground sm:grid-cols-[max-content_1fr]">
          {changes.map(([key, value]) => (
            <div key={key} className="contents">
              <dt className="font-medium" dir="ltr">
                {key}
              </dt>
              <dd className="break-all" dir="ltr">
                {describe(value)}
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
    </li>
  )
}

/** `{from, to}` diffs as "a → b"; anything else as compact JSON. */
function describe(value: unknown): string {
  if (value && typeof value === 'object' && 'from' in value && 'to' in value) {
    const v = value as { from: unknown; to: unknown }
    return `${String(v.from ?? '—')} → ${String(v.to ?? '—')}`
  }
  return typeof value === 'string' ? value : JSON.stringify(value)
}
