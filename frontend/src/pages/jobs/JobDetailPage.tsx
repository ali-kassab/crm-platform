import { CalendarPlus, Phone } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatDateTime, formatMoney, fromLocalInput, toLocalInput } from '@/lib/format'
import { useSubscription } from '@/lib/subscription'
import { VisitActions } from '@/pages/visits/VisitActions'
import { VisitReportButton } from '@/pages/visits/VisitReportButton'
import { JOB_STATUSES, useChangeJobStatus, useJob, useJobVisits, useScheduleVisit, type JobStatus } from './api'
import { PriorityBadge, StatusBadge } from './JobBadges'

export function JobDetailPage() {
  const { id = '' } = useParams()
  const { t } = useTranslation()
  const { can } = useAuth()
  const { data: subscription } = useSubscription()
  const job = useJob(id)
  const visits = useJobVisits(id)
  const changeStatus = useChangeJobStatus(id)
  const readOnly = subscription?.readOnly ?? false

  if (job.isPending) return <Skeleton className="h-48 w-full" />
  if (job.isError)
    return (
      <Alert variant="destructive">
        <AlertDescription>{job.error instanceof ApiError ? job.error.message : t('errors.generic')}</AlertDescription>
      </Alert>
    )
  const j = job.data
  const phone = j.customer?.phone ?? j.customer?.mobile

  return (
    <div className="flex max-w-5xl flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold">{j.title}</h1>
        <StatusBadge status={j.status} />
        <PriorityBadge priority={j.priority} />
        {j.sla?.breached && <Badge variant="destructive">{t('jobs.slaBreached')}</Badge>}
        <div className="flex-1" />
        {(can('ops:write') || can('ops:assigned')) && (
          <Select
            value={j.status}
            disabled={readOnly || changeStatus.isPending}
            onValueChange={(v) =>
              changeStatus.mutate(v as JobStatus, {
                onSuccess: () => toast.success(t('jobs.statusChanged')),
                onError: (e) => toast.error(e instanceof ApiError ? e.message : t('errors.generic')),
              })
            }
          >
            <SelectTrigger className="w-44" aria-label={t('jobs.changeStatus')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {JOB_STATUSES.map((s) => (
                <SelectItem key={s} value={s}>
                  {t(`jobs.statuses.${s}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('jobs.details')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            <Row label={t('jobs.customer')}>
              {j.customer?.name}
              {phone && (
                <a href={`tel:${phone}`} dir="ltr" className="ms-2 inline-flex items-center gap-1 text-primary">
                  <Phone className="size-3.5" />
                  {phone}
                </a>
              )}
            </Row>
            <Row label={t('jobs.site')}>
              {[j.site?.name, j.site?.addressLine1, j.site?.city].filter(Boolean).join('، ')}
            </Row>
            <Row label={t('jobs.type')}>{t(`jobs.types.${j.type}`)}</Row>
            {j.reportedIssue && <Row label={t('jobs.issue')}>{j.reportedIssue}</Row>}
            {can('finance:read') && <Row label={t('jobs.budget')}>{formatMoney(j.budget)}</Row>}
            {j.sla && <Row label={t('jobs.responseDue')}>{formatDateTime(j.sla.responseDueAt)}</Row>}
            <Row label={t('jobs.assignees')}>{j.assignees.map((a) => a.name).join('، ')}</Row>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle>{t('visits.title')}</CardTitle>
            {(can('ops:write') || can('ops:assigned')) && <ScheduleVisitDialog jobId={id} disabled={readOnly} />}
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {visits.data?.length === 0 && <p className="text-sm text-muted-foreground">{t('visits.emptyTitle')}</p>}
            {visits.data?.map((v) => (
              <div key={v.id} className="flex flex-wrap items-center gap-2 rounded-md border p-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">{formatDateTime(v.scheduledStart)}</p>
                  <p className="text-xs text-muted-foreground">
                    {t(`visits.statuses.${v.status}`)}
                    {v.outcome && ` · ${t(`visits.outcomes.${v.outcome}`)}`}
                  </p>
                </div>
                <VisitReportButton visit={v} />
                <VisitActions visit={v} disabled={readOnly} />
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[8rem_1fr] gap-2">
      <span className="text-muted-foreground">{label}</span>
      <span>{children}</span>
    </div>
  )
}

function ScheduleVisitDialog({ jobId, disabled }: { jobId: string; disabled?: boolean }) {
  const { t } = useTranslation()
  const schedule = useScheduleVisit(jobId)
  const [open, setOpen] = useState(false)
  const tomorrow = new Date(Date.now() + 24 * 3600_000)
  tomorrow.setMinutes(0, 0, 0)
  const [start, setStart] = useState(toLocalInput(tomorrow))
  const [end, setEnd] = useState(toLocalInput(new Date(tomorrow.getTime() + 2 * 3600_000)))
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={disabled}>
          <CalendarPlus />
          {t('visits.schedule')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('visits.schedule')}</DialogTitle>
        </DialogHeader>
        <FieldGroup>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="v-from">{t('visits.from')}</FieldLabel>
              <Input id="v-from" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="v-to">{t('visits.to')}</FieldLabel>
              <Input id="v-to" type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="v-sched-notes">{t('visits.notes')}</FieldLabel>
            <Textarea id="v-sched-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={schedule.isPending}
            onClick={() => {
              setError(null)
              schedule.mutate(
                { scheduledStart: fromLocalInput(start), scheduledEnd: fromLocalInput(end), notes: notes || undefined },
                {
                  onSuccess: () => {
                    toast.success(t('visits.scheduled'))
                    setOpen(false)
                  },
                  onError: (e) => setError(e instanceof ApiError ? e.message : t('errors.generic')),
                },
              )
            }}
          >
            {schedule.isPending && <Spinner />}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
