import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { applyServerErrors } from '@/lib/forms'
import { AuthLayout } from './AuthLayout'

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const schema = z.object({
    email: z.email(t('common.required')),
    password: z.string().min(1, t('common.required')),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await login(values.email, values.password)
      const from = (location.state as { from?: string } | null)?.from ?? '/'
      void navigate(from, { replace: true })
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, ['email', 'password'], t('errors.generic')))
    }
  }

  return (
    <AuthLayout title={t('auth.loginTitle')} subtitle={t('auth.loginSubtitle')}>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <FieldGroup>
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}
          <Controller
            name="email"
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <FieldLabel htmlFor="email">{t('auth.email')}</FieldLabel>
                <Input {...field} id="email" type="email" dir="ltr" autoComplete="email" aria-invalid={fieldState.invalid} />
                <FieldError errors={[fieldState.error]} />
              </Field>
            )}
          />
          <Controller
            name="password"
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <FieldLabel htmlFor="password">{t('auth.password')}</FieldLabel>
                <Input
                  {...field}
                  id="password"
                  type="password"
                  dir="ltr"
                  autoComplete="current-password"
                  aria-invalid={fieldState.invalid}
                />
                <FieldError errors={[fieldState.error]} />
              </Field>
            )}
          />
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <Spinner />}
            {t('auth.login')}
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            {t('auth.noAccount')}{' '}
            <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('auth.register')}
            </Link>
          </p>
        </FieldGroup>
      </form>
    </AuthLayout>
  )
}
