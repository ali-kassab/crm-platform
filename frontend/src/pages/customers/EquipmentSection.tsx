import { zodResolver } from '@hookform/resolvers/zod'
import { Cctv, Plus } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { applyServerErrors } from '@/lib/forms'
import { formatDate } from '@/lib/format'
import type { Site } from './api'
import { EquipmentDetailsSheet } from './EquipmentDetailsSheet'
import { EQUIPMENT_CATEGORIES, useCreateEquipment, useEquipment, type EquipmentCategory } from './equipment-api'

const FIELDS = ['locationId', 'category', 'name', 'manufacturer', 'model', 'serialNumber', 'installedOn'] as const

function NewEquipmentDialog({ contactId, sites, disabled }: { contactId: string; sites: Site[]; disabled?: boolean }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const create = useCreateEquipment(contactId)
  const required = t('common.required')
  const schema = z.object({
    locationId: z.string().min(1, required),
    category: z.enum(EQUIPMENT_CATEGORIES),
    name: z.string().trim().min(1, required).max(150),
    manufacturer: z.string().trim().max(100),
    model: z.string().trim().max(100),
    serialNumber: z.string().trim().max(100),
    installedOn: z.string(),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      locationId: sites.length === 1 ? sites[0].id : '',
      category: 'CCTV_CAMERA',
      name: '',
      manufacturer: '',
      model: '',
      serialNumber: '',
      installedOn: '',
    },
  })

  async function onSubmit(v: Values) {
    setFormError(null)
    const opt = (s: string) => s || undefined
    try {
      await create.mutateAsync({
        locationId: v.locationId,
        category: v.category,
        name: v.name,
        manufacturer: opt(v.manufacturer),
        model: opt(v.model),
        serialNumber: opt(v.serialNumber),
        installedOn: opt(v.installedOn),
      })
      toast.success(t('equipment.created'))
      form.reset({ ...v, name: '', serialNumber: '' })
      setOpen(false)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, FIELDS, t('errors.generic')))
    }
  }

  const text = (name: 'name' | 'manufacturer' | 'model' | 'serialNumber' | 'installedOn', label: string, type = 'text') => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`e-${name}`}>{label}</FieldLabel>
          <Input
            {...field}
            id={`e-${name}`}
            type={type}
            dir={name === 'serialNumber' || type === 'date' ? 'ltr' : undefined}
            aria-invalid={fieldState.invalid}
          />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )
  const select = (name: 'locationId' | 'category', label: string, options: { value: string; label: string }[]) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`e-${name}`}>{label}</FieldLabel>
          <Select value={field.value} onValueChange={(v) => field.onChange(v as EquipmentCategory)}>
            <SelectTrigger id={`e-${name}`} aria-invalid={fieldState.invalid}>
              <SelectValue placeholder={t('jobs.pickSite')} />
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

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" variant="outline" size="sm" disabled={disabled}>
          <Plus />
          {t('equipment.add')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('equipment.add')}</DialogTitle>
        </DialogHeader>
        <form id="new-equipment" onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {formError ? (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {select('locationId', t('jobs.site'), sites.map((s) => ({ value: s.id, label: s.name })))}
            {select('category', t('equipment.category'), EQUIPMENT_CATEGORIES.map((c) => ({ value: c, label: t(`equipment.categories.${c}`) })))}
            {text('name', t('equipment.name'))}
            <div className="grid gap-4 sm:grid-cols-2">
              {text('manufacturer', t('equipment.manufacturer'))}
              {text('model', t('equipment.model'))}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              {text('serialNumber', t('equipment.serial'))}
              {text('installedOn', t('equipment.installedOn'), 'date')}
            </div>
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="new-equipment" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Devices installed at the customer's sites (cameras, recorders, AC units…) with serial numbers. */
export function EquipmentSection({ contactId, sites, canWrite, showAdd }: { contactId: string; sites: Site[]; canWrite: boolean; showAdd: boolean }) {
  const { t } = useTranslation()
  const equipment = useEquipment(contactId)
  const siteName = new Map(sites.map((s) => [s.id, s.name]))
  return (
    <section aria-labelledby="equipment-heading" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id="equipment-heading" className="text-lg font-semibold">
          {t('equipment.title')}
        </h2>
        <div className="flex-1" />
        {showAdd && sites.length > 0 ? <NewEquipmentDialog contactId={contactId} sites={sites} disabled={!canWrite} /> : null}
      </div>
      {equipment.isPending ? (
        <Skeleton className="h-20 w-full" />
      ) : equipment.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : equipment.data.content.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Cctv />
            </EmptyMedia>
            <EmptyTitle>{t('equipment.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{sites.length === 0 ? t('equipment.needSite') : t('equipment.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ul className="divide-y rounded-md border">
          {equipment.data.content.map((e) => (
            <li key={e.id} className="flex flex-col gap-1 px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <EquipmentDetailsSheet equipment={e} siteName={siteName.get(e.locationId)} canWrite={canWrite} />
                <Badge variant="secondary">{t(`equipment.categories.${e.category}`)}</Badge>
                {e.status !== 'ACTIVE' ? <Badge variant="destructive">{t(`equipment.statuses.${e.status}`)}</Badge> : null}
              </div>
              <p className="text-sm text-muted-foreground">
                {[siteName.get(e.locationId), [e.manufacturer, e.model].filter(Boolean).join(' '), e.installedOn ? t('equipment.installedOnDate', { date: formatDate(e.installedOn) }) : null]
                  .filter(Boolean)
                  .join('، ')}
              </p>
              {e.serialNumber ? (
                <p className="text-sm text-muted-foreground">
                  {t('equipment.serial')}: <span dir="ltr">{e.serialNumber}</span>
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
