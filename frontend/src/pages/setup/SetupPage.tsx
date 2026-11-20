import { zodResolver } from '@hookform/resolvers/zod'
import { Cctv, Check, CircleCheck, Circle, HardHat, Wrench, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldContent, FieldDescription, FieldError, FieldGroup, FieldLabel, FieldTitle } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'
import { applyServerErrors } from '@/lib/forms'
import { useFirstStepsDone, useSaveWorkspace, useWorkspaceSettings, type WorkspaceSettings } from './api'
import { FIRST_STEPS, SECTORS, isSector, type FirstStep, type Sector } from './sectors'

const SECTOR_ICONS: Record<Sector, LucideIcon> = { FIT_OUT: HardHat, SECURITY_SYSTEMS: Cctv, MAINTENANCE: Wrench }
const STEP_LINKS: Record<FirstStep, string> = {
  customer: '/customers',
  equipment: '/customers', // equipment is registered on the customer's page, at one of their sites
  plan: '/maintenance',
  job: '/jobs',
  visit: '/jobs', // visits are scheduled from a work order
}
const FIELDS = ['sector', 'name', 'defaultLanguage', 'currency', 'timeZone'] as const
const STAGES = ['sector', 'details', 'firstSteps'] as const
type Stage = (typeof STAGES)[number]

function validTimeZone(zone: string) {
  try {
    new Intl.DateTimeFormat('en', { timeZone: zone })
    return true
  } catch {
    return false
  }
}

/** Workspace setup: sector → regional settings → the sector's first steps. Owners and admins only. */
export function SetupPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const workspace = useWorkspaceSettings()
  if (!can('workspace:manage')) return <Navigate to="/" replace />

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">{t('setup.title')}</h1>
        <p className="text-muted-foreground">{t('setup.subtitle')}</p>
      </div>
      {workspace.isPending ? (
        <Skeleton className="h-72" />
      ) : workspace.isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between gap-2">
            {t('errors.generic')}
            <Button size="sm" variant="outline" onClick={() => void workspace.refetch()}>
              {t('common.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      ) : (
        <SetupWizard workspace={workspace.data} />
      )}
    </div>
  )
}

function SetupWizard({ workspace }: { workspace: WorkspaceSettings }) {
  const { t } = useTranslation()
  const { updateWorkspace } = useAuth()
  const save = useSaveWorkspace()
  const [stage, setStage] = useState<Stage>('sector')
  const [formError, setFormError] = useState<string | null>(null)
  const required = t('common.required')
  const schema = z.object({
    sector: z.enum(SECTORS, { error: t('setup.chooseSector') }),
    name: z.string().trim().min(2, required).max(80),
    defaultLanguage: z.enum(['ar', 'en']),
    currency: z.string().trim().regex(/^[A-Za-z]{3}$/, t('setup.currencyInvalid')),
    timeZone: z.string().trim().min(1, required).max(60).refine(validTimeZone, t('setup.timeZoneInvalid')),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      sector: isSector(workspace.sector) ? workspace.sector : undefined,
      name: workspace.name,
      defaultLanguage: workspace.defaultLanguage === 'en' ? 'en' : 'ar',
      currency: workspace.currency,
      timeZone: workspace.timeZone,
    },
  })
  const sector = useWatch({ control: form.control, name: 'sector' })

  async function next() {
    if (await form.trigger('sector')) setStage('details')
  }

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      const saved = await save.mutateAsync({ ...values, currency: values.currency.toUpperCase() })
      updateWorkspace({ name: saved.name, defaultLanguage: saved.defaultLanguage, currency: saved.currency, sector: saved.sector })
      toast.success(t('setup.saved'))
      setStage('firstSteps')
    } catch (e) {
      const message = applyServerErrors(e, form.setError, FIELDS, t('errors.generic'))
      if (form.getFieldState('sector').error) setStage('sector')
      setFormError(message)
    }
  }

  const reached = STAGES.indexOf(stage)
  return (
    <div className="flex flex-col gap-6">
      <ol className="flex flex-wrap gap-x-6 gap-y-2" aria-label={t('setup.progress')}>
        {STAGES.map((s, i) => (
          <li key={s} className="flex items-center gap-2" aria-current={s === stage ? 'step' : undefined}>
            <span
              className={cn(
                'flex size-7 items-center justify-center rounded-full border text-sm font-medium tabular-nums',
                i < reached && 'border-primary bg-primary text-primary-foreground',
                i === reached && 'border-primary text-primary',
                i > reached && 'text-muted-foreground',
              )}
            >
              {i < reached ? <Check className="size-4" aria-hidden /> : i + 1}
            </span>
            <span className={cn('text-sm', i === reached ? 'font-medium' : 'text-muted-foreground')}>
              {t(`setup.stages.${s}`)}
            </span>
          </li>
        ))}
      </ol>

      {stage === 'firstSteps' && isSector(sector) ? (
        <FirstSteps sector={sector} onBack={() => setStage('details')} />
      ) : (
        <form id="workspace-setup" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            {stage === 'sector' ? (
              <>
                <Controller
                  name="sector"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel id="sector-label">{t('setup.sectorQuestion')}</FieldLabel>
                      <RadioGroup
                        value={field.value ?? ''}
                        onValueChange={field.onChange}
                        aria-labelledby="sector-label"
                        aria-invalid={fieldState.invalid}
                        className="gap-3"
                      >
                        {SECTORS.map((s) => {
                          const Icon = SECTOR_ICONS[s]
                          return (
                            <FieldLabel key={s} htmlFor={`sector-${s}`}>
                              <Field orientation="horizontal">
                                <Icon className="size-6 shrink-0 text-primary" aria-hidden />
                                <FieldContent>
                                  <FieldTitle>{t(`auth.sectors.${s}`)}</FieldTitle>
                                  <FieldDescription>{t(`setup.sectorHints.${s}`)}</FieldDescription>
                                </FieldContent>
                                <RadioGroupItem value={s} id={`sector-${s}`} />
                              </Field>
                            </FieldLabel>
                          )
                        })}
                      </RadioGroup>
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
                <div className="flex justify-end">
                  <Button type="button" onClick={() => void next()}>
                    {t('common.next')}
                  </Button>
                </div>
              </>
            ) : (
              <>
                <Controller
                  name="name"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="ws-name">{t('auth.workspaceName')}</FieldLabel>
                      <Input {...field} id="ws-name" aria-invalid={fieldState.invalid} />
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
                <Controller
                  name="defaultLanguage"
                  control={form.control}
                  render={({ field }) => (
                    <Field>
                      <FieldLabel htmlFor="ws-language">{t('setup.defaultLanguage')}</FieldLabel>
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger id="ws-language">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ar">العربية</SelectItem>
                          <SelectItem value="en">English</SelectItem>
                        </SelectContent>
                      </Select>
                      <FieldDescription>{t('setup.defaultLanguageHint')}</FieldDescription>
                    </Field>
                  )}
                />
                <div className="grid gap-4 sm:grid-cols-2">
                  <Controller
                    name="currency"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <Field data-invalid={fieldState.invalid}>
                        <FieldLabel htmlFor="ws-currency">{t('setup.currency')}</FieldLabel>
                        <Input {...field} id="ws-currency" dir="ltr" maxLength={3} autoCapitalize="characters" aria-invalid={fieldState.invalid} />
                        <FieldError errors={[fieldState.error]} />
                      </Field>
                    )}
                  />
                  <Controller
                    name="timeZone"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <Field data-invalid={fieldState.invalid}>
                        <FieldLabel htmlFor="ws-zone">{t('setup.timeZone')}</FieldLabel>
                        <Input {...field} id="ws-zone" dir="ltr" placeholder="Africa/Cairo" aria-invalid={fieldState.invalid} />
                        <FieldError errors={[fieldState.error]} />
                      </Field>
                    )}
                  />
                </div>
                <div className="flex justify-between gap-2">
                  <Button type="button" variant="outline" onClick={() => setStage('sector')}>
                    {t('common.previous')}
                  </Button>
                  <Button type="submit" disabled={form.formState.isSubmitting}>
                    {form.formState.isSubmitting && <Spinner />}
                    {t('setup.saveAndContinue')}
                  </Button>
                </div>
              </>
            )}
          </FieldGroup>
        </form>
      )}
    </div>
  )
}

function FirstSteps({ sector, onBack }: { sector: Sector; onBack: () => void }) {
  const { t } = useTranslation()
  const steps = FIRST_STEPS[sector]
  const done = useFirstStepsDone(steps)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('setup.firstStepsTitle', { sector: t(`auth.sectors.${sector}`) })}</CardTitle>
        <CardDescription>{t('setup.firstStepsHint')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ol className="flex flex-col divide-y">
          {steps.map((step, i) => {
            const isDone = done[i]?.data === true
            return (
              <li key={step} className="flex items-center gap-3 py-3">
                {isDone ? (
                  <CircleCheck className="size-5 shrink-0 text-primary" aria-label={t('setup.done')} />
                ) : (
                  <Circle className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                )}
                <div className="min-w-0 flex-1">
                  <p className={cn('font-medium', isDone && 'text-muted-foreground line-through')}>{t(`setup.steps.${step}.title`)}</p>
                  <p className="text-sm text-muted-foreground">{t(`setup.steps.${step}.hint`)}</p>
                </div>
                <Button asChild size="sm" variant={isDone ? 'ghost' : 'outline'}>
                  <Link to={STEP_LINKS[step]}>{t(`setup.steps.${step}.action`)}</Link>
                </Button>
              </li>
            )
          })}
        </ol>
        <div className="flex justify-between gap-2">
          <Button type="button" variant="outline" onClick={onBack}>
            {t('common.previous')}
          </Button>
          <Button asChild>
            <Link to="/">{t('setup.finish')}</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
