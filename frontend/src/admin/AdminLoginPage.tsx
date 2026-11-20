import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { ApiError } from '@/lib/api'
import { AuthLayout } from '@/pages/auth/AuthLayout'
import { useAdmin } from './admin-context'

export function AdminLoginPage() {
  const { t } = useTranslation()
  const { signIn } = useAdmin()
  const [error, setError] = useState<string | null>(null)
  const required = t('common.required')
  const schema = z.object({ email: z.email(required), password: z.string().min(1, required).max(128) })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })

  async function onSubmit(v: Values) {
    setError(null)
    try {
      await signIn(v.email, v.password)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('errors.generic'))
    }
  }

  return (
    <AuthLayout title={t('admin.loginTitle')} subtitle={t('admin.loginSubtitle')}>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <FieldGroup>
          {error ? (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}
          {(['email', 'password'] as const).map((name) => (
            <Controller
              key={name}
              name={name}
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor={`admin-${name}`}>{t(`auth.${name}`)}</FieldLabel>
                  <Input {...field} id={`admin-${name}`} type={name} dir="ltr" autoComplete={name === 'email' ? 'username' : 'current-password'} aria-invalid={fieldState.invalid} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          ))}
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('auth.login')}
          </Button>
        </FieldGroup>
      </form>
    </AuthLayout>
  )
}
