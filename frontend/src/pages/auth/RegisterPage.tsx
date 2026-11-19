import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { applyServerErrors } from '@/lib/forms'
import { AuthLayout } from './AuthLayout'

const SECTORS = ['FIT_OUT', 'SECURITY_SYSTEMS', 'MAINTENANCE'] as const
const FIELDS = ['firstName', 'lastName', 'email', 'password', 'workspaceName', 'sector'] as const

export function RegisterPage() {
  const { t } = useTranslation()
  const { register } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)
  const required = t('common.required')
  const schema = z.object({
    firstName: z.string().trim().min(1, required).max(60),
    lastName: z.string().trim().min(1, required).max(60),
    email: z.email(required),
    password: z.string().min(1, required).max(128),
    workspaceName: z.string().trim().min(2, required).max(80),
    sector: z.enum(SECTORS).optional(),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', password: '', workspaceName: '' },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await register(values)
      void navigate('/', { replace: true })
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const text = (name: Exclude<(typeof FIELDS)[number], 'sector'>, type = 'text', ltr = false) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={name}>{t(`auth.${name}`)}</FieldLabel>
          <Input {...field} id={name} type={type} dir={ltr ? 'ltr' : undefined} aria-invalid={fieldState.invalid} />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )

  return (
    <AuthLayout title={t('auth.registerTitle')} subtitle={t('auth.registerSubtitle')}>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <FieldGroup>
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            {text('firstName')}
            {text('lastName')}
          </div>
          {text('workspaceName')}
          <Controller
            name="sector"
            control={form.control}
            render={({ field }) => (
              <Field>
                <FieldLabel htmlFor="sector">{t('auth.sector')}</FieldLabel>
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="sector">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SECTORS.map((s) => (
                      <SelectItem key={s} value={s}>
                        {t(`auth.sectors.${s}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            )}
          />
          {text('email', 'email', true)}
          {text('password', 'password', true)}
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <Spinner />}
            {t('auth.register')}
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            {t('auth.haveAccount')}{' '}
            <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('auth.login')}
            </Link>
          </p>
        </FieldGroup>
      </form>
    </AuthLayout>
  )
}
