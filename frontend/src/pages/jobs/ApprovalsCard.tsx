import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ClipboardCheck, X } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { api } from '@/lib/api'
import { applyServerErrors } from '@/lib/forms'
import { formatDateTime } from '@/lib/format'

const KINDS = ['QUOTATION', 'WORK', 'COMPLETION'] as const
// "" = only record the request; the customer decides later
const OUTCOMES = ['', 'APPROVED', 'REJECTED'] as const

interface Approval {
  id: string
  kind: (typeof KINDS)[number]
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverName: string | null
  decidedAt: string | null
  notes: string | null
  createdAt: string
}

interface Decision {
  status: 'APPROVED' | 'REJECTED'
  approverName: string
  notes?: string
}

// under ['job', id] so the realtime APPROVAL hint for this job refreshes it
const key = (jobId: string) => ['job', jobId, 'approvals']

function useApprovals(jobId: string) {
  return useQuery({ queryKey: key(jobId), queryFn: () => api<Approval[]>(`/jobs/${jobId}/approvals`) })
}

function useRecordApproval(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (b: { kind: string; notes?: string; decision?: Decision }) =>
      api<Approval>(`/jobs/${jobId}/approvals`, { method: 'POST', body: b }),
    onSuccess: () => void client.invalidateQueries({ queryKey: key(jobId) }),
  })
}

function useDecide(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...d }: Decision & { id: string }) =>
      api<Approval>(`/jobs/${jobId}/approvals/${id}/decision`, { method: 'POST', body: d }),
    onSuccess: () => void client.invalidateQueries({ queryKey: key(jobId) }),
  })
}

function RecordApprovalDialog({ jobId, disabled }: { jobId: string; disabled: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const record = useRecordApproval(jobId)
  const required = t('common.required')
  const schema = z
    .object({ kind: z.enum(KINDS), outcome: z.enum(OUTCOMES), approverName: z.string().trim().max(150), notes: z.string().trim().max(2000) })
    .superRefine((v, ctx) => {
      if (v.outcome && !v.approverName) ctx.addIssue({ code: 'custom', path: ['approverName'], message: required })
    })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { kind: 'QUOTATION', outcome: '', approverName: '', notes: '' } })
  const outcome = useWatch({ control: form.control, name: 'outcome' })

  async function onSubmit(v: Values) {
    setFormError(null)
    try {
      await record.mutateAsync({
        kind: v.kind,
        notes: v.notes || undefined,
        decision: v.outcome ? { status: v.outcome, approverName: v.approverName, notes: v.notes || undefined } : undefined,
      })
      toast.success(t('approvals.recorded'))
      form.reset()
      setOpen(false)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, ['kind', 'approverName', 'notes'], t('errors.generic')))
    }
  }

  const select = (name: 'kind' | 'outcome', label: string, options: readonly string[], text: (o: string) => string) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field }) => (
        <Field>
          <FieldLabel htmlFor={`a-${name}`}>{label}</FieldLabel>
          {/* Radix Select cannot hold an empty value, so "no decision yet" is stored as "PENDING" in the control */}
          <Select value={field.value || 'PENDING'} onValueChange={(v) => field.onChange(v === 'PENDING' ? '' : v)}>
            <SelectTrigger id={`a-${name}`}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {options.map((o) => (
                <SelectItem key={o || 'PENDING'} value={o || 'PENDING'}>
                  {text(o)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      )}
    />
  )

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" disabled={disabled}>
          <ClipboardCheck />
          {t('approvals.record')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('approvals.record')}</DialogTitle>
          <DialogDescription>{t('approvals.recordHint')}</DialogDescription>
        </DialogHeader>
        <form id="new-approval" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError ? (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {select('kind', t('approvals.kind'), KINDS, (k) => t(`approvals.kinds.${k}`))}
            {select('outcome', t('approvals.outcome'), OUTCOMES, (o) => t(`approvals.statuses.${o || 'PENDING'}`))}
            {outcome ? (
              <Controller
                name="approverName"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="a-approver">{t('approvals.approverName')}</FieldLabel>
                    <Input {...field} id="a-approver" aria-invalid={fieldState.invalid} />
                    <FieldError errors={[fieldState.error]} />
                  </Field>
                )}
              />
            ) : null}
            <Controller
              name="notes"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="a-notes">{t('approvals.notes')}</FieldLabel>
                  <Textarea {...field} id="a-notes" rows={2} />
                </Field>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-approval" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** The customer answers a pending approval (recorded by staff on their behalf, D14). */
function DecideDialog({ jobId, approval, status, disabled }: { jobId: string; approval: Approval; status: Decision['status']; disabled: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const decide = useDecide(jobId)
  const schema = z.object({ approverName: z.string().trim().min(1, t('common.required')).max(150) })
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { approverName: '' } })
  const label = t(status === 'APPROVED' ? 'approvals.approve' : 'approvals.reject')

  async function onSubmit(v: z.infer<typeof schema>) {
    try {
      await decide.mutateAsync({ id: approval.id, status, approverName: v.approverName })
      toast.success(t(`approvals.decided.${status}`))
      setOpen(false)
    } catch (e) {
      applyServerErrors(e, form.setError, ['approverName'], t('errors.generic'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={status === 'APPROVED' ? 'default' : 'outline'} disabled={disabled}>
          {status === 'APPROVED' ? <Check /> : <X />}
          {label}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {label}: {t(`approvals.kinds.${approval.kind}`)}
          </DialogTitle>
        </DialogHeader>
        <form id={`decide-${approval.id}`} onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <Controller
            name="approverName"
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <FieldLabel htmlFor={`d-${approval.id}`}>{t('approvals.approverName')}</FieldLabel>
                <Input {...field} id={`d-${approval.id}`} aria-invalid={fieldState.invalid} />
                <FieldError errors={[fieldState.error]} />
              </Field>
            )}
          />
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={`decide-${approval.id}`} disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {label}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const BADGE = { PENDING: 'secondary', APPROVED: 'default', REJECTED: 'destructive' } as const

export function ApprovalsCard({ jobId, canEdit, showActions }: { jobId: string; canEdit: boolean; showActions: boolean }) {
  const { t } = useTranslation()
  const approvals = useApprovals(jobId)
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t('approvals.title')}</CardTitle>
        {showActions ? <RecordApprovalDialog jobId={jobId} disabled={!canEdit} /> : null}
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {approvals.data?.length === 0 ? <p className="text-sm text-muted-foreground">{t('approvals.empty')}</p> : null}
        {approvals.data?.map((a) => (
          <div key={a.id} className="flex flex-wrap items-center gap-2 rounded-md border p-3">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-medium">{t(`approvals.kinds.${a.kind}`)}</span>
                <Badge variant={BADGE[a.status]}>{t(`approvals.statuses.${a.status}`)}</Badge>
              </div>
              <p className="text-xs text-muted-foreground">
                {a.decidedAt
                  ? t('approvals.decidedBy', { name: a.approverName, when: formatDateTime(a.decidedAt) })
                  : t('approvals.requestedAt', { when: formatDateTime(a.createdAt) })}
              </p>
              {a.notes ? <p className="text-xs text-muted-foreground">{a.notes}</p> : null}
            </div>
            {showActions && a.status === 'PENDING' ? (
              <div className="flex gap-2">
                <DecideDialog jobId={jobId} approval={a} status="APPROVED" disabled={!canEdit} />
                <DecideDialog jobId={jobId} approval={a} status="REJECTED" disabled={!canEdit} />
              </div>
            ) : null}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
