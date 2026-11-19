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
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { applyServerErrors } from '@/lib/forms'
import { useCreateCustomer, type CustomerKind } from './api'

const FIELDS = ['firstName', 'lastName', 'company', 'phone', 'email', 'name'] as const

export function NewCustomerDialog({ disabled }: { disabled?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const create = useCreateCustomer()
  const required = t('common.required')
  const schema = z
    .object({
      kind: z.enum(['INDIVIDUAL', 'COMPANY']),
      firstName: z.string().trim().max(60).optional(),
      lastName: z.string().trim().max(60).optional(),
      company: z.string().trim().max(150).optional(),
      phone: z.string().trim().min(6, required).max(30),
      email: z.union([z.email(), z.literal('')]).optional(),
    })
    .superRefine((v, ctx) => {
      if (v.kind === 'INDIVIDUAL' && !v.firstName) ctx.addIssue({ code: 'custom', path: ['firstName'], message: required })
      if (v.kind === 'COMPANY' && !v.company) ctx.addIssue({ code: 'custom', path: ['company'], message: required })
    })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { kind: 'COMPANY', firstName: '', lastName: '', company: '', phone: '', email: '' },
  })
  const kind = useWatch({ control: form.control, name: 'kind' })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await create.mutateAsync({ ...values, kind: values.kind as CustomerKind })
      toast.success(t('customers.created'))
      form.reset()
      setOpen(false)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const text = (name: 'firstName' | 'lastName' | 'company' | 'phone' | 'email', label: string, ltr = false) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`c-${name}`}>{label}</FieldLabel>
          <Input {...field} id={`c-${name}`} dir={ltr ? 'ltr' : undefined} aria-invalid={fieldState.invalid} />
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
          {t('customers.add')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('customers.add')}</DialogTitle>
          <DialogDescription>{t('customers.emptyHint')}</DialogDescription>
        </DialogHeader>
        <form id="new-customer" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <Tabs value={kind} onValueChange={(v) => form.setValue('kind', v as CustomerKind)}>
              <TabsList>
                <TabsTrigger value="COMPANY">{t('customers.COMPANY')}</TabsTrigger>
                <TabsTrigger value="INDIVIDUAL">{t('customers.INDIVIDUAL')}</TabsTrigger>
              </TabsList>
            </Tabs>
            {kind === 'COMPANY' ? (
              text('company', t('customers.company'))
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                {text('firstName', t('auth.firstName'))}
                {text('lastName', t('auth.lastName'))}
              </div>
            )}
            {text('phone', t('customers.phone'), true)}
            {text('email', t('customers.email'), true)}
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-customer" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <Spinner />}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
