import { zodResolver } from '@hookform/resolvers/zod'
import { ImagePlus, Plus, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/auth/auth-context'
import { ApiError } from '@/lib/api'
import { applyServerErrors } from '@/lib/forms'
import { formatDateTime, formatMoney } from '@/lib/format'
import type { Visit } from '@/pages/jobs/api'
import {
  PHOTO_PURPOSES,
  useAddMaterial,
  useAddPhotos,
  useDeleteMaterial,
  useMaterials,
  useSaveReport,
  useThumbnail,
  useVisitFiles,
  useVisitReport,
  type PhotoPurpose,
  type VisitFile,
  type VisitReport,
} from './work-api'

const SECTIONS = ['reportedProblem', 'findings', 'diagnosis', 'workPerformed', 'recommendations', 'notes'] as const

function errorText(e: unknown, fallback: string) {
  return e instanceof ApiError ? e.message : fallback
}

// ------------------------------------------------------------------ report

function ReportForm({ visitId, initial, canEdit }: { visitId: string; initial: VisitReport | null; canEdit: boolean }) {
  const { t } = useTranslation()
  const save = useSaveReport(visitId)
  const [formError, setFormError] = useState<string | null>(null)
  const schema = z.object({
    reportedProblem: z.string().max(2000),
    findings: z.string().max(4000),
    diagnosis: z.string().max(4000),
    workPerformed: z.string().max(4000),
    recommendations: z.string().max(4000),
    notes: z.string().max(4000),
    customerName: z.string().trim().max(150),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      reportedProblem: initial?.reportedProblem ?? '',
      findings: initial?.findings ?? '',
      diagnosis: initial?.diagnosis ?? '',
      workPerformed: initial?.workPerformed ?? '',
      recommendations: initial?.recommendations ?? '',
      notes: initial?.notes ?? '',
      customerName: initial?.customerName ?? '',
    },
  })

  async function onSubmit(v: Values) {
    setFormError(null)
    const blank = (s: string) => (s.trim() ? s : null)
    try {
      await save.mutateAsync({
        reportedProblem: blank(v.reportedProblem),
        findings: blank(v.findings),
        diagnosis: blank(v.diagnosis),
        workPerformed: blank(v.workPerformed),
        recommendations: blank(v.recommendations),
        notes: blank(v.notes),
        customerName: blank(v.customerName),
        customerSignatureFileId: initial?.customerSignatureFileId ?? null,
      })
      toast.success(t('work.reportSaved'))
      form.reset(v)
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, [...SECTIONS, 'customerName'], t('errors.generic')))
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <FieldGroup>
        {formError ? (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        ) : null}
        {SECTIONS.map((name) => (
          <Controller
            key={name}
            name={name}
            control={form.control}
            render={({ field, fieldState }) => (
              <Field data-invalid={fieldState.invalid}>
                <FieldLabel htmlFor={`r-${name}`}>{t(`work.sections.${name}`)}</FieldLabel>
                <Textarea {...field} id={`r-${name}`} rows={name === 'workPerformed' ? 4 : 2} disabled={!canEdit} />
                <FieldError errors={[fieldState.error]} />
              </Field>
            )}
          />
        ))}
        <Controller
          name="customerName"
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor="r-customerName">{t('work.customerName')}</FieldLabel>
              <Input {...field} id="r-customerName" disabled={!canEdit} />
              <FieldDescription>
                {initial?.customerConfirmedAt
                  ? t('work.confirmedAt', { when: formatDateTime(initial.customerConfirmedAt) })
                  : t('work.customerNameHint')}
              </FieldDescription>
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        {canEdit ? (
          <Button type="submit" className="self-start" disabled={form.formState.isSubmitting || !form.formState.isDirty}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t('work.saveReport')}
          </Button>
        ) : null}
      </FieldGroup>
    </form>
  )
}

function ReportTab({ visitId, canEdit }: { visitId: string; canEdit: boolean }) {
  const { t } = useTranslation()
  const report = useVisitReport(visitId)
  if (report.isPending) return <Skeleton className="h-64 w-full" />
  if (report.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{t('errors.generic')}</AlertDescription>
      </Alert>
    )
  }
  return <ReportForm visitId={visitId} initial={report.data} canEdit={canEdit} />
}

// ------------------------------------------------------------------ photos

function Thumbnail({ file }: { file: VisitFile }) {
  const { t } = useTranslation()
  const thumb = useThumbnail(file.id, file.hasThumbnail)
  return (
    <figure className="flex flex-col gap-1">
      <div className="aspect-square overflow-hidden rounded-md border bg-muted">
        {thumb.data ? (
          <img src={thumb.data.url} alt={file.name} loading="lazy" className="size-full object-cover" />
        ) : (
          <div className="flex size-full items-center justify-center p-2 text-center text-xs break-all text-muted-foreground">
            {file.name}
          </div>
        )}
      </div>
      {file.purpose ? (
        <figcaption>
          <Badge variant="secondary">{t(`work.purposes.${file.purpose}`, { defaultValue: file.purpose })}</Badge>
        </figcaption>
      ) : null}
    </figure>
  )
}

function PhotosTab({ visitId, canEdit }: { visitId: string; canEdit: boolean }) {
  const { t } = useTranslation()
  const files = useVisitFiles(visitId)
  const add = useAddPhotos(visitId)
  const [purpose, setPurpose] = useState<PhotoPurpose>('BEFORE')
  const input = useRef<HTMLInputElement>(null)

  function pick(list: FileList | null) {
    const chosen = Array.from(list ?? [])
    if (input.current) input.current.value = ''
    if (chosen.length === 0) return
    add.mutate(
      { files: chosen, purpose },
      {
        onSuccess: () => toast.success(t('work.photosAdded', { count: chosen.length })),
        onError: (e) => toast.error(errorText(e, t('errors.generic'))),
      },
    )
  }

  const photos = (files.data ?? []).filter((f) => f.kind === 'IMAGE')
  return (
    <div className="flex flex-col gap-4">
      {canEdit ? (
        <div className="flex flex-wrap items-end gap-3">
          <Field className="w-auto min-w-40">
            <FieldLabel htmlFor="p-purpose">{t('work.photoPurpose')}</FieldLabel>
            <Select value={purpose} onValueChange={(v) => setPurpose(v as PhotoPurpose)}>
              <SelectTrigger id="p-purpose">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PHOTO_PURPOSES.map((p) => (
                  <SelectItem key={p} value={p}>
                    {t(`work.purposes.${p}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <input
            ref={input}
            type="file"
            accept="image/*"
            multiple
            className="sr-only"
            aria-label={t('work.addPhotos')}
            onChange={(e) => pick(e.target.files)}
          />
          <Button type="button" disabled={add.isPending} onClick={() => input.current?.click()}>
            {add.isPending ? <Spinner /> : <ImagePlus />}
            {t('work.addPhotos')}
          </Button>
        </div>
      ) : null}
      {files.isPending ? (
        <Skeleton className="h-32 w-full" />
      ) : photos.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyTitle>{t('work.noPhotos')}</EmptyTitle>
            <EmptyDescription>{t('work.noPhotosHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {photos.map((f) => (
            <Thumbnail key={f.id} file={f} />
          ))}
        </div>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ materials

function MaterialsTab({ visit, canEdit }: { visit: Visit; canEdit: boolean }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const showCost = can('finance:read')
  const materials = useMaterials(visit.jobId)
  const add = useAddMaterial(visit.jobId)
  const remove = useDeleteMaterial(visit.jobId)
  const [formError, setFormError] = useState<string | null>(null)
  const required = t('common.required')
  const schema = z.object({
    name: z.string().trim().min(1, required).max(150),
    quantity: z.string().trim().regex(/^\d{1,9}(\.\d{1,3})?$/, t('work.quantityInvalid')).refine((q) => Number(q) > 0, t('work.quantityInvalid')),
    unit: z.string().trim().max(20),
    unitCost: z.string().trim().regex(/^(\d{1,15}(\.\d{1,2})?)?$/, t('work.costInvalid')),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', quantity: '1', unit: '', unitCost: '' } })

  async function onSubmit(v: Values) {
    setFormError(null)
    try {
      await add.mutateAsync({
        visitId: visit.id,
        name: v.name,
        quantity: v.quantity,
        unit: v.unit || undefined,
        unitCost: showCost && v.unitCost ? v.unitCost : undefined,
      })
      form.reset({ name: '', quantity: '1', unit: v.unit, unitCost: '' })
    } catch (e) {
      setFormError(applyServerErrors(e, form.setError, ['name', 'quantity', 'unit', 'unitCost'], t('errors.generic')))
    }
  }

  const lines = (materials.data?.lines ?? []).filter((m) => m.visitId === visit.id)
  const text = (name: keyof Values, label: string, numeric = false) => (
    <Controller
      name={name}
      control={form.control}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={`m-${name}`}>{label}</FieldLabel>
          <Input
            {...field}
            id={`m-${name}`}
            inputMode={numeric ? 'decimal' : undefined}
            dir={numeric ? 'ltr' : undefined}
            aria-invalid={fieldState.invalid}
          />
          <FieldError errors={[fieldState.error]} />
        </Field>
      )}
    />
  )

  return (
    <div className="flex flex-col gap-4">
      {materials.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : lines.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('work.noMaterials')}</p>
      ) : (
        <ul className="divide-y rounded-md border">
          {lines.map((m) => (
            <li key={m.id} className="flex items-center gap-3 px-3 py-2">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{m.name}</p>
                <p className="text-sm text-muted-foreground">
                  <span dir="ltr">{m.quantity}</span> {m.unit ?? ''}
                  {showCost && m.lineCost ? ` — ${formatMoney(m.lineCost)}` : ''}
                </p>
              </div>
              {canEdit ? (
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  aria-label={t('work.removeMaterial', { name: m.name })}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(m.id, { onError: (e) => toast.error(errorText(e, t('errors.generic'))) })}
                >
                  <Trash2 />
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {canEdit ? (
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="rounded-md border p-3">
          <FieldGroup>
            {formError ? (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {text('name', t('work.materialName'))}
            <div className={showCost ? 'grid grid-cols-3 gap-3' : 'grid grid-cols-2 gap-3'}>
              {text('quantity', t('work.quantity'), true)}
              {text('unit', t('work.unit'))}
              {showCost ? text('unitCost', t('work.unitCost'), true) : null}
            </div>
            <Button type="submit" variant="outline" className="self-start" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? <Spinner /> : <Plus />}
              {t('work.addMaterial')}
            </Button>
          </FieldGroup>
        </form>
      ) : null}
    </div>
  )
}

// ------------------------------------------------------------------ sheet body

export function VisitWork({ visit, readOnly }: { visit: Visit; readOnly?: boolean }) {
  const { t } = useTranslation()
  const { can } = useAuth()
  const canEdit = (can('ops:write') || can('ops:assigned')) && !readOnly
  return (
    <Tabs defaultValue="report" className="gap-4">
      <TabsList>
        <TabsTrigger value="report">{t('work.tabs.report')}</TabsTrigger>
        <TabsTrigger value="photos">{t('work.tabs.photos')}</TabsTrigger>
        <TabsTrigger value="materials">{t('work.tabs.materials')}</TabsTrigger>
      </TabsList>
      <TabsContent value="report">
        <ReportTab visitId={visit.id} canEdit={canEdit} />
      </TabsContent>
      <TabsContent value="photos">
        <PhotosTab visitId={visit.id} canEdit={canEdit} />
      </TabsContent>
      <TabsContent value="materials">
        <MaterialsTab visit={visit} canEdit={canEdit} />
      </TabsContent>
    </Tabs>
  )
}
