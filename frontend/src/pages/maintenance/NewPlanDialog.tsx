import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { applyServerErrors } from '@/lib/forms'
import { useCustomerOptions, useSiteOptions } from '@/pages/jobs/api'
import { FREQUENCIES, useCreatePlan, type Frequency } from './api'

const FIELDS = ['contactId', 'locationId', 'title', 'frequency', 'intervalDays', 'startDate', 'endDate', 'responseHours', 'includedVisits'] as const

const today = () => new Date().toISOString().slice(0, 10)
const optionalInt = (min: number, max: number, message: string) =>
  z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= min && Number(v) <= max), message)

/** A preventive-maintenance contract: the system opens a work order before each due date. */
export function NewPlanDialog({ disabled }: { disabled?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const create = useCreatePlan()
  const required = t('common.required')
  const schema = z
    .object({
      contactId: z.string().min(1, required),
      locationId: z.string().min(1, required),
      title: z.string().trim().min(1, required).max(150),
      frequency: z.enum(FREQUENCIES),
      intervalDays: optionalInt(1, 730, t('maintenance.intervalInvalid')),
      startDate: z.string().min(1, required),
      endDate: z.string(),
      responseHours: optionalInt(1, 720, t('maintenance.responseInvalid')),
      includedVisits: optionalInt(1, 1000, t('maintenance.visitsInvalid')),
    })
    .superRefine((v, ctx) => {
      if (v.frequency === 'CUSTOM' && !v.intervalDays) ctx.addIssue({ code: 'custom', path: ['intervalDays'], message: required })
      if (v.endDate && v.endDate < v.startDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: t('maintenance.endBeforeStart') })
    })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { contactId: '', locationId: '', title: '', frequency: 'QUARTERLY', intervalDays: '', startDate: today(), endDate: '', responseHours: '', includedVisits: '' },
  })
  const contactId = useWatch({ control: form.control, name: 'contactId' })
  const frequency = useWatch({ control: form.control, name: 'frequency' })
  const customers = useCustomerOptions()
  const sites = useSiteOptions(contactId || undefined)

  async function onSubmit(v: Values) {
    setFormError(null)
    const num = (s: string) => (s ? Number(s) : undefined)
    try {
      await create.mutateAsync({
        contactId: v.contactId,
        locationId: v.locationId,
        title: v.title,
        frequency: v.frequency,
        intervalDays: v.frequency === 'CUSTOM' ? num(v.intervalDays) : undefined,
        startDate: v.startDate,
        endDate: v.endDate || undefined,
        responseHours: num(v.responseHours),
        includedVisits: num(v.includedVisits),
      })
      toast.success(t('maintenance.created'))
      form.reset()
      setOpen(false)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const select = (name: 'contactId' | 'locationId' | 'frequency', label: string, options: { value: string; label: string }[], placeholder?: string, onPicked?: () => void) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`mp-${name}`}>{label}</FieldLabel>
          <Select
            value={field.value}
            onValueChange={(v) => {
              field.onChange(v)
              onPicked?.()
            }}
          >
            <SelectTrigger id={`mp-${name}`} aria-invalid={fieldState.invalid}>
              <SelectValue placeholder={placeholder} />
            </SelectTrigger>
            <SelectContent>
              {options.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
  const input = (name: 'title' | 'intervalDays' | 'startDate' | 'endDate' | 'responseHours' | 'includedVisits', label: string, type = 'text', hint?: string) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`mp-${name}`}>{label}</FieldLabel>
          <Input
            {...field}
            id={`mp-${name}`}
            type={type}
            inputMode={type === 'number' ? 'numeric' : undefined}
            dir={type === 'text' ? undefined : 'ltr'}
            aria-invalid={fieldState.invalid}
          />
          {hint ? <FieldDescription>{hint}</FieldDescription> : null}
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button disabled={disabled}>
          <Plus />
          {t('maintenance.add')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('maintenance.add')}</DialogTitle>
          <DialogDescription>{t('maintenance.addHint')}</DialogDescription>
        </DialogHeader>
        <form id="new-plan" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError ? (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {select('contactId', t('jobs.customer'), (customers.data?.content ?? []).map((c) => ({ value: c.id, label: c.fullName })), t('jobs.pickCustomer'), () =>
              form.setValue('locationId', ''),
            )}
            {select('locationId', t('jobs.site'), (sites.data?.content ?? []).map((s) => ({ value: s.id, label: s.name })), t('jobs.pickSite'))}
            {input('title', t('maintenance.planTitle'))}
            <div className="grid gap-4 sm:grid-cols-2">
              {select('frequency', t('maintenance.frequency'), FREQUENCIES.map((f: Frequency) => ({ value: f, label: t(`maintenance.frequencies.${f}`) })))}
              {frequency === 'CUSTOM' ? input('intervalDays', t('maintenance.intervalDays'), 'number') : null}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              {input('startDate', t('maintenance.startDate'), 'date')}
              {input('endDate', t('maintenance.endDate'), 'date', t('maintenance.endDateHint'))}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              {input('responseHours', t('maintenance.responseHours'), 'number', t('maintenance.responseHint'))}
              {input('includedVisits', t('maintenance.includedVisits'), 'number', t('maintenance.includedHint'))}
            </div>
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-plan" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
