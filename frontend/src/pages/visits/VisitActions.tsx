import { CheckCircle2, Play } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import type { Visit } from '@/pages/jobs/api'
import { OUTCOMES, useCompleteVisit, useStartVisit } from './api'

/** Start / finish buttons: big enough for a phone in the field. */
export function VisitActions({ visit, disabled }: { visit: Visit; disabled?: boolean }) {
  const { t } = useTranslation()
  const start = useStartVisit()
  const complete = useCompleteVisit()
  const [open, setOpen] = useState(false)
  const [outcome, setOutcome] = useState<string>('RESOLVED')
  const [notes, setNotes] = useState('')
  const [completeJob, setCompleteJob] = useState(true)

  const fail = (e: unknown) => toast.error(e instanceof ApiError ? e.message : t('errors.generic'))

  if (visit.status === 'SCHEDULED' || visit.status === 'MISSED') {
    return (
      <Button
        size="sm"
        disabled={disabled || start.isPending}
        onClick={() =>
          start.mutate({ id: visit.id }, { onSuccess: () => toast.success(t('visits.started')), onError: fail })
        }
      >
        {start.isPending ? <Spinner /> : <Play />}
        {t('visits.start')}
      </Button>
    )
  }
  if (visit.status !== 'IN_PROGRESS') return null
  return (
    <>
      <Button size="sm" disabled={disabled} onClick={() => setOpen(true)}>
        <CheckCircle2 />
        {t('visits.complete')}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('visits.complete')}</DialogTitle>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="v-outcome">{t('visits.outcome')}</FieldLabel>
              <Select value={outcome} onValueChange={setOutcome}>
                <SelectTrigger id="v-outcome">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {OUTCOMES.map((o) => (
                    <SelectItem key={o} value={o}>
                      {t(`visits.outcomes.${o}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="v-notes">{t('visits.notes')}</FieldLabel>
              <Textarea id="v-notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Field>
            <div className="flex items-center gap-2">
              <Checkbox
                id="v-complete-job"
                checked={completeJob}
                onCheckedChange={(v) => setCompleteJob(v === true)}
              />
              <Label htmlFor="v-complete-job">{t('visits.completeJob')}</Label>
            </div>
          </FieldGroup>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              disabled={complete.isPending}
              onClick={() =>
                complete.mutate(
                  { id: visit.id, body: { outcome, notes: notes || undefined, completeJob } },
                  {
                    onSuccess: () => {
                      toast.success(t('visits.completed'))
                      setOpen(false)
                    },
                    onError: fail,
                  },
                )
              }
            >
              {complete.isPending && <Spinner />}
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
