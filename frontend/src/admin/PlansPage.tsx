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
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/lib/forms'
import { formatMoney } from '@/lib/format'
import { usePlans, useSavePlan, type AdminPlan } from './api'

const LIMITS = ['maxUsers', 'maxActiveProjects', 'maxJobsPerMonth', 'maxStorageMb'] as const
const TEXTS = ['nameAr', 'nameEn', 'descriptionAr', 'descriptionEn'] as const
const FIELDS = ['code', ...TEXTS, 'priceMonthly', 'priceYearly', ...LIMITS, 'sortOrder'] as const

export function PlansPage() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const plans = usePlans()
  const limit = (n: number | null) => (n == null ? t('subscriptionPage.unlimited') : String(n))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t('admin.plans.title')}</h1>
          <p className="text-sm text-muted-foreground">{t('admin.plans.hint')}</p>
        </div>
        <PlanDialog />
      </div>
      {plans.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : plans.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : (
        <ul className="flex flex-col divide-y rounded-md border bg-background">
          {plans.data.map((p) => (
            <li key={p.plan.id} className="flex flex-wrap items-start gap-3 p-4">
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{ar ? p.plan.nameAr : p.plan.nameEn}</span>
                  <Badge variant="outline" dir="ltr">
                    {p.plan.code}
                  </Badge>
                  {p.active ? null : <Badge variant="secondary">{t('admin.plans.inactive')}</Badge>}
                </div>
                <p className="text-sm">
                  {formatMoney(p.plan.priceMonthly, p.plan.currency)} {t('subscriptionPage.perMonth')}، {formatMoney(p.plan.priceYearly, p.plan.currency)}{' '}
                  {t('subscriptionPage.perYear')}
                </p>
                <p className="text-sm text-muted-foreground">
                  {LIMITS.map((k) => `${t(`admin.plans.${k}`)}: ${limit(p.plan.limits[k])}`).join('، ')}
                </p>
              </div>
              <PlanDialog plan={p} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const money = z.string().trim().regex(/^\d{1,15}(\.\d{1,2})?$/)
const count = z.string().trim().regex(/^\d{0,9}$/)

function PlanDialog({ plan }: { plan?: AdminPlan }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const save = useSavePlan(plan?.plan.id)
  const required = t('common.required')
  const schema = z.object({
    code: plan ? z.string() : z.string().trim().regex(/^[A-Za-z0-9_]{2,30}$/, t('admin.plans.codeInvalid')),
    nameAr: z.string().trim().min(1, required).max(100),
    nameEn: z.string().trim().min(1, required).max(100),
    descriptionAr: z.string().max(1000),
    descriptionEn: z.string().max(1000),
    priceMonthly: money,
    priceYearly: money,
    maxUsers: count,
    maxActiveProjects: count,
    maxJobsPerMonth: count,
    maxStorageMb: count,
    sortOrder: count,
    active: z.boolean(),
  })
  type Values = z.infer<typeof schema>
  const str = (n: number | null | undefined) => (n == null ? '' : String(n))
  const initial = (): Values => ({
    code: plan?.plan.code ?? '',
    nameAr: plan?.plan.nameAr ?? '',
    nameEn: plan?.plan.nameEn ?? '',
    descriptionAr: plan?.plan.descriptionAr ?? '',
    descriptionEn: plan?.plan.descriptionEn ?? '',
    priceMonthly: plan?.plan.priceMonthly ?? '',
    priceYearly: plan?.plan.priceYearly ?? '',
    maxUsers: str(plan?.plan.limits.maxUsers),
    maxActiveProjects: str(plan?.plan.limits.maxActiveProjects),
    maxJobsPerMonth: str(plan?.plan.limits.maxJobsPerMonth),
    maxStorageMb: str(plan?.plan.limits.maxStorageMb),
    sortOrder: str(plan?.sortOrder ?? 0),
    active: plan?.active ?? true,
  })
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: initial() })

  function onOpenChange(next: boolean) {
    if (next) {
      form.reset(initial())
      setError(null)
    }
    setOpen(next)
  }

  async function onSubmit(v: Values) {
    setError(null)
    const n = (s: string) => (s === '' ? null : Number(s))
    try {
      await save.mutateAsync({
        ...(plan ? {} : { code: v.code.toUpperCase() }),
        nameAr: v.nameAr,
        nameEn: v.nameEn,
        descriptionAr: v.descriptionAr || null,
        descriptionEn: v.descriptionEn || null,
        priceMonthly: v.priceMonthly,
        priceYearly: v.priceYearly,
        maxUsers: n(v.maxUsers),
        maxActiveProjects: n(v.maxActiveProjects),
        maxJobsPerMonth: n(v.maxJobsPerMonth),
        maxStorageMb: n(v.maxStorageMb),
        sortOrder: n(v.sortOrder) ?? 0,
        active: v.active,
      })
      toast.success(t('admin.plans.saved'))
      setOpen(false)
    } catch (e) {
      setError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const input = (name: Exclude<(typeof FIELDS)[number], (typeof TEXTS)[2] | (typeof TEXTS)[3]>, ltr = false, placeholder?: string) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`plan-${name}`}>{t(`admin.plans.${name}`)}</FieldLabel>
          <Input {...field} id={`plan-${name}`} dir={ltr ? 'ltr' : undefined} inputMode={ltr ? 'decimal' : undefined} placeholder={placeholder} aria-invalid={fieldState.invalid} />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
  const area = (name: 'descriptionAr' | 'descriptionEn') => (
    <Controller
      name={name}
      control={form.control}
      render={({ field }) => (
        <Field>
          <FieldLabel htmlFor={`plan-${name}`}>{t(`admin.plans.${name}`)}</FieldLabel>
          <Textarea {...field} id={`plan-${name}`} rows={2} dir={name === 'descriptionEn' ? 'ltr' : 'rtl'} />
        </Field>
      )}
    />
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        {plan ? (
          <Button size="sm" variant="outline">
            <Pencil />
            {t('admin.edit')}
          </Button>
        ) : (
          <Button>
            <Plus />
            {t('admin.plans.add')}
          </Button>
        )}
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{plan ? t('admin.plans.edit') : t('admin.plans.add')}</DialogTitle>
        </DialogHeader>
        <form id={`plan-form-${plan?.plan.id ?? 'new'}`} onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            {plan ? null : input('code', true, 'TEAM')}
            <div className="grid gap-4 sm:grid-cols-2">
              {input('nameAr')}
              {input('nameEn')}
              {area('descriptionAr')}
              {area('descriptionEn')}
              {input('priceMonthly', true)}
              {input('priceYearly', true)}
            </div>
            <p className="text-sm text-muted-foreground">{t('admin.plans.limitsHint')}</p>
            <div className="grid gap-4 sm:grid-cols-2">
              {LIMITS.map((k) => (
                <div key={k}>{input(k, true)}</div>
              ))}
              {input('sortOrder', true)}
            </div>
            <Controller
              name="active"
              control={form.control}
              render={({ field }) => (
                <div className="flex items-center gap-2">
                  <Checkbox id={`plan-active-${plan?.plan.id ?? 'new'}`} checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
                  <Label htmlFor={`plan-active-${plan?.plan.id ?? 'new'}`}>{t('admin.plans.active')}</Label>
                </div>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={`plan-form-${plan?.plan.id ?? 'new'}`} disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
