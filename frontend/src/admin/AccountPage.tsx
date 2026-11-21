import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm, type FieldValues, type Path, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { applyServerErrors } from '@/lib/forms'
import { useAdmin } from './admin-context'
import { useUpdateAccount, type AdminUser } from './api'
import { adminApi } from './session'

function Secret<T extends FieldValues>({ form, prefix, name, label, hint, autoComplete }: { form: UseFormReturn<T>; prefix: string; name: Path<T>; label: string; hint?: string; autoComplete: string }) {
  return (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`${prefix}-${name}`}>{label}</FieldLabel>
          <Input {...field} id={`${prefix}-${name}`} type={name === 'newEmail' ? 'email' : 'password'} dir="ltr" autoComplete={autoComplete} aria-invalid={fieldState.invalid} />
          {hint ? <FieldDescription>{hint}</FieldDescription> : null}
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
}

/** The platform admin's own sign-in details. */
export function AccountPage() {
  const { t } = useTranslation()
  const { admin, updateAdmin } = useAdmin()
  const required = t('common.required')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  const emailSchema = z.object({ currentPassword: z.string().min(1, required), newEmail: z.email(required) })
  const emailForm = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema), defaultValues: { currentPassword: '', newEmail: '' } })
  const passwordSchema = z
    .object({
      currentPassword: z.string().min(1, required),
      newPassword: z.string().min(14, t('admin.account.passwordRule')).max(128),
      confirm: z.string(),
    })
    .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: t('admin.account.mismatch') })
  const passwordForm = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  })

  async function changeEmail(v: z.infer<typeof emailSchema>) {
    setEmailError(null)
    try {
      const user = await adminApi<AdminUser>('/account/email', { method: 'POST', body: v })
      updateAdmin(user)
      emailForm.reset()
      toast.success(t('admin.account.emailChanged'))
    } catch (e) {
      setEmailError(applyServerErrors(e, emailForm.setError, ['currentPassword', 'newEmail'], t('errors.generic')))
    }
  }

  async function changePassword(v: z.infer<typeof passwordSchema>) {
    setPasswordError(null)
    try {
      await adminApi('/account/password', { method: 'POST', body: { currentPassword: v.currentPassword, newPassword: v.newPassword } })
      passwordForm.reset()
      toast.success(t('admin.account.passwordChanged'))
    } catch (e) {
      setPasswordError(applyServerErrors(e, passwordForm.setError, ['currentPassword', 'newPassword'], t('errors.generic')))
    }
  }

  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('admin.nav.account')}</h1>
      <ProfileCard />
      <Card>
        <CardHeader>
          <CardTitle>{t('admin.account.email')}</CardTitle>
          <CardDescription dir="ltr" className="text-start">
            {admin?.email}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={emailForm.handleSubmit(changeEmail)} noValidate>
            <FieldGroup>
              {emailError ? (
                <Alert variant="destructive">
                  <AlertDescription>{emailError}</AlertDescription>
                </Alert>
              ) : null}
              <Secret form={emailForm} prefix="email" name="newEmail" label={t('admin.account.newEmail')} autoComplete="username" />
              <Secret form={emailForm} prefix="email" name="currentPassword" label={t('admin.account.currentPassword')} autoComplete="current-password" />
              <Button type="submit" className="w-fit" disabled={emailForm.formState.isSubmitting}>
                {emailForm.formState.isSubmitting ? <Spinner /> : null}
                {t('admin.account.changeEmail')}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>{t('admin.account.password')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={passwordForm.handleSubmit(changePassword)} noValidate>
            <FieldGroup>
              {passwordError ? (
                <Alert variant="destructive">
                  <AlertDescription>{passwordError}</AlertDescription>
                </Alert>
              ) : null}
              <Secret form={passwordForm} prefix="password" name="currentPassword" label={t('admin.account.currentPassword')} autoComplete="current-password" />
              <Secret form={passwordForm} prefix="password" name="newPassword" label={t('admin.account.newPassword')} hint={t('admin.account.passwordRule')} autoComplete="new-password" />
              <Secret form={passwordForm} prefix="password" name="confirm" label={t('admin.account.confirm')} autoComplete="new-password" />
              <Button type="submit" className="w-fit" disabled={passwordForm.formState.isSubmitting}>
                {passwordForm.formState.isSubmitting ? <Spinner /> : null}
                {t('admin.account.changePassword')}
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

/** Who is signed in, and their display name (email and password have their own checked forms). */
function ProfileCard() {
  const { t } = useTranslation()
  const { admin, updateAdmin } = useAdmin()
  const [error, setError] = useState<string | null>(null)
  const update = useUpdateAccount()
  const required = t('common.required')
  const schema = z.object({ firstName: z.string().trim().min(1, required).max(60), lastName: z.string().trim().min(1, required).max(60) })
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    values: { firstName: admin?.firstName ?? '', lastName: admin?.lastName ?? '' },
  })

  async function save(v: z.infer<typeof schema>) {
    setError(null)
    try {
      const user = await update.mutateAsync(v)
      updateAdmin(user)
      toast.success(t('admin.account.nameSaved'))
    } catch (e) {
      setError(applyServerErrors(e, form.setError, ['firstName', 'lastName'], t('errors.generic')))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin.account.profile')}</CardTitle>
        <CardDescription>{t('admin.account.profileHint')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
          <dt className="text-muted-foreground">{t('admin.account.signInEmail')}</dt>
          <dd dir="ltr" className="text-start">
            {admin?.email}
          </dd>
          <dt className="text-muted-foreground">{t('admin.account.role')}</dt>
          <dd>{t('admin.users.platformAdmin')}</dd>
          <dt className="text-muted-foreground">{t('admin.account.session')}</dt>
          <dd>{t('admin.account.sessionHint')}</dd>
        </dl>
        <form onSubmit={form.handleSubmit(save)} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <div className="grid gap-4 sm:grid-cols-2">
              {(['firstName', 'lastName'] as const).map((name) => (
                <Controller
                  key={name}
                  name={name}
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor={`profile-${name}`}>{t(`admin.account.${name}`)}</FieldLabel>
                      <Input {...field} id={`profile-${name}`} autoComplete={name === 'firstName' ? 'given-name' : 'family-name'} aria-invalid={fieldState.invalid} />
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
              ))}
            </div>
            <Button type="submit" className="w-fit" disabled={form.formState.isSubmitting || !form.formState.isDirty}>
              {form.formState.isSubmitting ? <Spinner /> : null}
              {t('admin.account.saveName')}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}
