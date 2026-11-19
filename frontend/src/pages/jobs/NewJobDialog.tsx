import { zodResolver } from '@hookform/resolvers/zod'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
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
import { applyServerErrors } from '@/lib/forms'
import { NewSiteDialog } from '@/pages/customers/NewSiteDialog'
import { JOB_TYPES, PRIORITIES, useCreateJob, useCustomerOptions, useSiteOptions } from './api'

const FIELDS = ['contactId', 'locationId', 'title', 'type', 'priority', 'reportedIssue', 'budget'] as const

export function NewJobDialog({ disabled }: { disabled?: boolean }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const create = useCreateJob()
  const required = t('common.required')
  const schema = z.object({
    contactId: z.string().min(1, required),
    locationId: z.string().min(1, required),
    title: z.string().trim().min(1, required).max(150),
    type: z.enum(JOB_TYPES),
    priority: z.enum(PRIORITIES),
    reportedIssue: z.string().max(2000).optional(),
    budget: z.string().regex(/^\d{0,15}(\.\d{1,2})?$/).optional(),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { contactId: '', locationId: '', title: '', type: 'CORRECTIVE_REPAIR', priority: 'NORMAL', reportedIssue: '', budget: '' },
  })
  const contactId = useWatch({ control: form.control, name: 'contactId' })
  const customers = useCustomerOptions()
  const sites = useSiteOptions(contactId || undefined)

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      const job = await create.mutateAsync(values)
      toast.success(t('jobs.created'))
      setOpen(false)
      form.reset()
      void navigate(`/jobs/${job.id}`)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  function picker(
    name: 'contactId' | 'locationId' | 'type' | 'priority',
    label: string,
    options: { value: string; label: string }[],
    placeholder?: string,
    onPicked?: () => void,
  ) {
    return (
      <Controller
        name={name}
        control={form.control}
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor={`j-${name}`}>{label}</FieldLabel>
            <Select
              value={field.value}
              onValueChange={(v) => {
                field.onChange(v)
                onPicked?.()
              }}
            >
              <SelectTrigger id={`j-${name}`} aria-invalid={fieldState.invalid}>
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
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button disabled={disabled}>
          <Plus />
          {t('jobs.add')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('jobs.add')}</DialogTitle>
        </DialogHeader>
        <form id="new-job" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            {picker(
              'contactId',
              t('jobs.customer'),
              (customers.data?.content ?? []).map((c) => ({ value: c.id, label: c.fullName })),
              t('jobs.pickCustomer'),
              () => form.setValue('locationId', ''),
            )}
            {picker(
              'locationId',
              t('jobs.site'),
              (sites.data?.content ?? []).map((s) => ({ value: s.id, label: s.name })),
              t('jobs.pickSite'),
            )}
            {contactId && sites.data && sites.data.content.length === 0 ? (
              <div className="-mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                <span>{t('sites.noneForCustomer')}</span>
                <NewSiteDialog
                  contactId={contactId}
                  size="sm"
                  onCreated={(site) => form.setValue('locationId', site.id, { shouldValidate: true })}
                />
              </div>
            ) : null}
            <Controller
              name="title"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="j-title">{t('jobs.workTitle')}</FieldLabel>
                  <Input {...field} id="j-title" aria-invalid={fieldState.invalid} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              {picker('type', t('jobs.type'), JOB_TYPES.map((v) => ({ value: v, label: t(`jobs.types.${v}`) })))}
              {picker('priority', t('jobs.priority'), PRIORITIES.map((v) => ({ value: v, label: t(`jobs.priorities.${v}`) })))}
            </div>
            <Controller
              name="reportedIssue"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="j-issue">{t('jobs.issue')}</FieldLabel>
                  <Textarea {...field} id="j-issue" rows={3} />
                </Field>
              )}
            />
            <Controller
              name="budget"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="j-budget">{t('jobs.budget')}</FieldLabel>
                  <Input {...field} id="j-budget" inputMode="decimal" dir="ltr" aria-invalid={fieldState.invalid} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-job" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <Spinner />}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
