import { ArrowRight } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { statusVariant, usePlans, useWorkspace, useWorkspaceAction, useWorkspaces, type SubscriptionView } from './api'

type Action = 'EXTEND' | 'SUSPEND' | 'REACTIVATE' | 'CHANGE_PLAN'

export function WorkspaceDetailPage() {
  const { id = '' } = useParams()
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const detail = useWorkspace(id)
  const row = useWorkspaces('', '').data?.find((w) => w.id === id)

  if (detail.isPending) return <Skeleton className="h-64 w-full" />
  if (detail.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{detail.error instanceof ApiError ? detail.error.message : t('errors.generic')}</AlertDescription>
      </Alert>
    )
  }
  const { subscription: s, history, requests } = detail.data
  const usage = [
    [t('subscriptionPage.users'), s.usage.users, s.plan.limits.maxUsers],
    [t('subscriptionPage.jobsThisMonth'), s.usage.jobsThisMonth, s.plan.limits.maxJobsPerMonth],
    [t('subscriptionPage.storage'), s.usage.storageMb, s.plan.limits.maxStorageMb],
  ] as const

  return (
    <div className="flex flex-col gap-4">
      <Link to="/admin/workspaces" className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowRight className="size-4 ltr:rotate-180" aria-hidden />
        {t('admin.workspaces.title')}
      </Link>
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold">{row?.name ?? t('admin.workspaces.workspace')}</h1>
        <Badge variant={statusVariant(s.status)}>{t(`subscription.status.${s.status}`)}</Badge>
        {s.readOnly ? <Badge variant="destructive">{t('admin.workspaces.readOnly')}</Badge> : null}
      </div>
      {row?.ownerEmail ? (
        <p className="-mt-2 text-sm text-muted-foreground">
          <span dir="ltr">{row.ownerEmail}</span>
        </p>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('admin.workspaces.subscription')}</CardTitle>
            <CardDescription>
              {[ar ? s.plan.nameAr : s.plan.nameEn, s.billingPeriod ? t(`subscriptionPage.${s.billingPeriod === 'YEARLY' ? 'yearly' : 'monthly'}`) : null]
                .filter(Boolean)
                .join('، ')}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <p>
              {s.currentPeriodEnd ? t('admin.workspaces.periodEnds', { date: formatDate(s.currentPeriodEnd) }) : null}
              {s.graceEndsAt ? ` — ${t('admin.workspaces.graceEnds', { date: formatDate(s.graceEndsAt) })}` : null}
            </p>
            <ul className="flex flex-col gap-1">
              {usage.map(([label, used, max]) => (
                <li key={label} className="flex justify-between gap-2">
                  <span className="text-muted-foreground">{label}</span>
                  <span className="tabular-nums">
                    {used} / {max ?? t('subscriptionPage.unlimited')}
                  </span>
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-2 pt-2">
              <ActionDialog workspaceId={id} action="EXTEND" />
              <ActionDialog workspaceId={id} action="CHANGE_PLAN" current={s} />
              {s.status === 'SUSPENDED' ? <ActionDialog workspaceId={id} action="REACTIVATE" /> : <ActionDialog workspaceId={id} action="SUSPEND" />}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('admin.workspaces.requests')}</CardTitle>
          </CardHeader>
          <CardContent>
            {requests.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('admin.requests.empty')}</p>
            ) : (
              <ul className="flex flex-col divide-y text-sm">
                {requests.map((r) => (
                  <li key={r.id} className="flex flex-wrap items-center gap-2 py-2">
                    <Badge variant="outline">{t(`subscriptionPage.requestStatuses.${r.status}`)}</Badge>
                    <span>{formatMoney(r.amount, r.currency)}</span>
                    <span className="text-muted-foreground">{formatDateTime(r.createdAt)}</span>
                    {r.reviewNote ? <span className="w-full text-muted-foreground">{r.reviewNote}</span> : null}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('admin.workspaces.history')}</CardTitle>
        </CardHeader>
        <CardContent>
          {history.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('common.empty')}</p>
          ) : (
            <ol className="flex flex-col divide-y text-sm">
              {history.map((e, i) => (
                <li key={`${e.occurredAt}-${i}`} className="flex flex-wrap items-center gap-2 py-2">
                  <span className="font-medium">{t(`admin.events.${e.type}`, { defaultValue: e.type })}</span>
                  {e.fromStatus && e.toStatus && e.fromStatus !== e.toStatus ? (
                    <span className="text-muted-foreground">
                      {t('admin.statusChange', { from: t(`subscription.status.${e.fromStatus}`), to: t(`subscription.status.${e.toStatus}`) })}
                    </span>
                  ) : null}
                  <span className="text-muted-foreground">{t(`admin.actors.${e.actorKind}`, { defaultValue: e.actorKind })}</span>
                  <span className="ms-auto text-muted-foreground">{formatDateTime(e.occurredAt)}</span>
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function ActionDialog({ workspaceId, action, current }: { workspaceId: string; action: Action; current?: SubscriptionView }) {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const [open, setOpen] = useState(false)
  const [days, setDays] = useState('30')
  const [planId, setPlanId] = useState(current?.plan.id ?? '')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const plans = usePlans()
  const act = useWorkspaceAction(workspaceId)
  const daysNumber = Number(days)
  const noteMissing = action === 'SUSPEND' && !note.trim()
  const daysInvalid = action === 'EXTEND' && !(Number.isInteger(daysNumber) && daysNumber >= 1 && daysNumber <= 366)

  function submit() {
    setError(null)
    if (daysInvalid || noteMissing) return
    act.mutate(
      {
        action,
        days: action === 'EXTEND' ? daysNumber : undefined,
        planId: action === 'CHANGE_PLAN' ? planId : undefined,
        note: note.trim() || undefined,
      },
      {
        onSuccess: () => {
          toast.success(t(`admin.actions.${action}.done`))
          setOpen(false)
        },
        onError: (e) => setError(e instanceof ApiError ? e.message : t('errors.generic')),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={action === 'SUSPEND' ? 'destructive' : 'outline'}>
          {t(`admin.actions.${action}.label`)}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`admin.actions.${action}.label`)}</DialogTitle>
          <DialogDescription>{t(`admin.actions.${action}.hint`)}</DialogDescription>
        </DialogHeader>
        <FieldGroup>
          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          {action === 'EXTEND' ? (
            <Field data-invalid={daysInvalid}>
              <FieldLabel htmlFor={`days-${workspaceId}`}>{t('admin.actions.days')}</FieldLabel>
              <Input id={`days-${workspaceId}`} type="number" min={1} max={366} dir="ltr" value={days} onChange={(e) => setDays(e.target.value)} aria-invalid={daysInvalid} />
              {daysInvalid ? <FieldError>{t('admin.actions.daysInvalid')}</FieldError> : null}
            </Field>
          ) : null}
          {action === 'CHANGE_PLAN' ? (
            <Field>
              <FieldLabel htmlFor={`plan-${workspaceId}`}>{t('admin.requests.plan')}</FieldLabel>
              <Select value={planId} onValueChange={setPlanId}>
                <SelectTrigger id={`plan-${workspaceId}`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(plans.data ?? []).map((p) => (
                    <SelectItem key={p.plan.id} value={p.plan.id}>
                      {ar ? p.plan.nameAr : p.plan.nameEn}
                      {p.active ? '' : ` (${t('admin.plans.inactive')})`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          ) : null}
          <Field>
            <FieldLabel htmlFor={`note-${action}-${workspaceId}`}>{t(action === 'SUSPEND' ? 'admin.actions.reason' : 'admin.requests.note')}</FieldLabel>
            <Textarea id={`note-${action}-${workspaceId}`} rows={2} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant={action === 'SUSPEND' ? 'destructive' : 'default'} onClick={submit} disabled={act.isPending || noteMissing || (action === 'CHANGE_PLAN' && !planId)}>
            {act.isPending ? <Spinner /> : null}
            {t(`admin.actions.${action}.label`)}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
