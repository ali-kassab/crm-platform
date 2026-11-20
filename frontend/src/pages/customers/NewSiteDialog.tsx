import { zodResolver } from '@hookform/resolvers/zod'
import { MapPinPlus } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/lib/forms'
import { SITE_TYPES, useCreateSite, type Site, type SiteType } from './api'

const FIELDS = ['name', 'type', 'city', 'line1', 'accessInstructions'] as const

/** Adds a site (branch, building, home) to a customer; every work order is for a site. */
export function NewSiteDialog({
  contactId,
  disabled,
  size,
  onCreated,
}: {
  contactId: string
  disabled?: boolean
  size?: 'sm' | 'default'
  onCreated?: (site: Site) => void
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const create = useCreateSite(contactId)
  const required = t('common.required')
  const schema = z.object({
    name: z.string().trim().min(1, required).max(150),
    type: z.enum(SITE_TYPES),
    city: z.string().trim().max(100).optional(),
    line1: z.string().trim().max(200).optional(),
    accessInstructions: z.string().trim().max(2000).optional(),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', type: 'COMMERCIAL', city: '', line1: '', accessInstructions: '' },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      const site = await create.mutateAsync(values)
      toast.success(t('sites.created'))
      form.reset()
      setOpen(false)
      onCreated?.(site)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const text = (name: 'name' | 'city' | 'line1', label: string) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`s-${name}`}>{label}</FieldLabel>
          <Input {...field} id={`s-${name}`} aria-invalid={fieldState.invalid} />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" variant="outline" size={size} disabled={disabled}>
          <MapPinPlus />
          {t('sites.add')}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('sites.add')}</DialogTitle>
          <DialogDescription>{t('sites.addHint')}</DialogDescription>
        </DialogHeader>
        <form
          id="new-site"
          onSubmit={(e) => {
            e.stopPropagation() // the dialog can be opened from inside the work-order form
            void form.handleSubmit(onSubmit)(e)
          }}
          noValidate
        >
          <FieldGroup>
            {formError ? (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {text('name', t('sites.name'))}
            <Controller
              name="type"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="s-type">{t('sites.type')}</FieldLabel>
                  <Select value={field.value} onValueChange={(v) => field.onChange(v as SiteType)}>
                    <SelectTrigger id="s-type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {SITE_TYPES.map((v) => (
                        <SelectItem key={v} value={v}>
                          {t(`sites.types.${v}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
              )}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              {text('city', t('sites.city'))}
              {text('line1', t('sites.address'))}
            </div>
            <Controller
              name="accessInstructions"
              control={form.control}
              render={({ field }) => (
                <Field>
                  <FieldLabel htmlFor="s-access">{t('sites.access')}</FieldLabel>
                  <Textarea {...field} id="s-access" rows={2} />
                </Field>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-site" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
