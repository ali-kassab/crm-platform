import { zodResolver } from '@hookform/resolvers/zod'
import { Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/lib/forms'
import { METHOD_KINDS, useMethods, useSaveMethod, type AdminMethod } from './api'

const FIELDS = ['kind', 'labelAr', 'labelEn', 'account', 'instructionsAr', 'instructionsEn', 'sortOrder'] as const

/** Where tenants send manual payments (Vodafone Cash, InstaPay, bank transfer); shown on their subscription page. */
export function MethodsPage() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const methods = useMethods()
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t('admin.methods.title')}</h1>
          <p className="text-sm text-muted-foreground">{t('admin.methods.hint')}</p>
        </div>
        <MethodDialog />
      </div>
      {methods.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : methods.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : methods.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('admin.methods.empty')}</p>
      ) : (
        <ul className="flex flex-col divide-y rounded-md border bg-background">
          {methods.data.map((m) => (
            <li key={m.method.id} className="flex flex-wrap items-start gap-3 p-4">
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{ar ? m.method.labelAr : m.method.labelEn}</span>
                  <Badge variant="outline">{t(`admin.methods.kinds.${m.method.kind}`)}</Badge>
                  {m.active ? null : <Badge variant="secondary">{t('admin.plans.inactive')}</Badge>}
                </div>
                <p className="text-sm" dir="ltr">
                  {m.method.account}
                </p>
                {(ar ? m.method.instructionsAr : m.method.instructionsEn) ? (
                  <p className="text-sm whitespace-pre-line text-muted-foreground">{ar ? m.method.instructionsAr : m.method.instructionsEn}</p>
                ) : null}
              </div>
              <MethodDialog method={m} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function MethodDialog({ method }: { method?: AdminMethod }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const save = useSaveMethod(method?.method.id)
  const required = t('common.required')
  const schema = z.object({
    kind: z.enum(METHOD_KINDS),
    labelAr: z.string().trim().min(1, required).max(100),
    labelEn: z.string().trim().min(1, required).max(100),
    account: z.string().trim().min(1, required).max(150),
    instructionsAr: z.string().max(2000),
    instructionsEn: z.string().max(2000),
    sortOrder: z.string().trim().regex(/^\d{0,9}$/),
    active: z.boolean(),
  })
  type Values = z.infer<typeof schema>
  const initial = (): Values => ({
    kind: method?.method.kind ?? 'VODAFONE_CASH',
    labelAr: method?.method.labelAr ?? '',
    labelEn: method?.method.labelEn ?? '',
    account: method?.method.account ?? '',
    instructionsAr: method?.method.instructionsAr ?? '',
    instructionsEn: method?.method.instructionsEn ?? '',
    sortOrder: String(method?.sortOrder ?? 0),
    active: method?.active ?? true,
  })
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: initial() })
  const formId = `method-form-${method?.method.id ?? 'new'}`

  function onOpenChange(next: boolean) {
    if (next) {
      form.reset(initial())
      setError(null)
    }
    setOpen(next)
  }

  async function onSubmit(v: Values) {
    setError(null)
    try {
      await save.mutateAsync({
        ...v,
        instructionsAr: v.instructionsAr || null,
        instructionsEn: v.instructionsEn || null,
        sortOrder: v.sortOrder === '' ? 0 : Number(v.sortOrder),
      })
      toast.success(t('admin.methods.saved'))
      setOpen(false)
    } catch (e) {
      setError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const input = (name: 'labelAr' | 'labelEn' | 'account' | 'sortOrder', ltr = false) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`${formId}-${name}`}>{t(`admin.methods.${name}`)}</FieldLabel>
          <Input {...field} id={`${formId}-${name}`} dir={ltr ? 'ltr' : undefined} aria-invalid={fieldState.invalid} />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
  const area = (name: 'instructionsAr' | 'instructionsEn') => (
    <Controller
      name={name}
      control={form.control}
      render={({ field }) => (
        <Field>
          <FieldLabel htmlFor={`${formId}-${name}`}>{t(`admin.methods.${name}`)}</FieldLabel>
          <Textarea {...field} id={`${formId}-${name}`} rows={3} dir={name === 'instructionsEn' ? 'ltr' : 'rtl'} />
        </Field>
      )}
    />
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        {method ? (
          <Button size="sm" variant="outline">
            <Pencil />
            {t('admin.edit')}
          </Button>
        ) : (
          <Button>
            <Plus />
            {t('admin.methods.add')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{method ? t('admin.methods.edit') : t('admin.methods.add')}</DialogTitle>
        </DialogHeader>
        <form id={formId} onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Controller
              name="kind"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor={`${formId}-kind`}>{t('admin.methods.kind')}</FieldLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id={`${formId}-kind`}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {METHOD_KINDS.map((k) => (
                        <SelectItem key={k} value={k}>
                          {t(`admin.methods.kinds.${k}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
              )}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              {input('labelAr')}
              {input('labelEn')}
            </div>
            {input('account', true)}
            <div className="grid gap-4 sm:grid-cols-2">
              {area('instructionsAr')}
              {area('instructionsEn')}
            </div>
            {input('sortOrder', true)}
            <Controller
              name="active"
              control={form.control}
              render={({ field }) => (
                <div className="flex items-center gap-2">
                  <Checkbox id={`${formId}-active`} checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
                  <Label htmlFor={`${formId}-active`}>{t('admin.methods.active')}</Label>
                </div>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={formId} disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
