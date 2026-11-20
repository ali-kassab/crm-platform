import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Field, FieldLabel } from '@/components/ui/field'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { formatDate, formatDateTime } from '@/lib/format'
import {
  EQUIPMENT_STATUSES,
  useEquipmentDetail,
  useUpdateEquipmentStatus,
  type Equipment,
  type EquipmentDetail,
} from './equipment-api'

/** A device's card: details, status, the work orders that served it and its warranties. */
export function EquipmentDetailsSheet({ equipment, siteName, canWrite }: { equipment: Equipment; siteName?: string; canWrite: boolean }) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const detail = useEquipmentDetail(equipment.id, open)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <button type="button" className="text-start font-medium underline-offset-4 hover:underline focus-visible:underline focus-visible:outline-none">
          {equipment.name}
        </button>
      </SheetTrigger>
      {/* inline-end side: left in Arabic, right in English (as the visit record) */}
      <SheetContent
        side={i18n.dir() === 'rtl' ? 'left' : 'right'}
        className="w-full overflow-y-auto data-[side=left]:sm:max-w-lg data-[side=right]:sm:max-w-lg"
      >
        <SheetHeader>
          <SheetTitle>{equipment.name}</SheetTitle>
          <SheetDescription>{[t(`equipment.categories.${equipment.category}`), siteName].filter(Boolean).join('، ')}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-5 px-4 pb-6">
          {detail.isPending ? (
            <Skeleton className="h-48 w-full" />
          ) : detail.isError ? (
            <Alert variant="destructive">
              <AlertDescription>{t('errors.generic')}</AlertDescription>
            </Alert>
          ) : (
            <DeviceBody device={detail.data} canWrite={canWrite} />
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

function DeviceBody({ device, canWrite }: { device: EquipmentDetail; canWrite: boolean }) {
  const { t } = useTranslation()
  const update = useUpdateEquipmentStatus(device.id)
  const facts: [string, React.ReactNode][] = [
    [t('equipment.manufacturer'), device.manufacturer],
    [t('equipment.model'), device.model],
    [t('equipment.serial'), device.serialNumber ? <span dir="ltr">{device.serialNumber}</span> : null],
    [t('equipment.installedOn'), device.installedOn ? formatDate(device.installedOn) : null],
    [t('equipment.notes'), device.notes],
  ]

  function changeStatus(status: Equipment['status']) {
    update.mutate(status, {
      onSuccess: () => toast.success(t('equipment.statusSaved')),
      onError: (e) => toast.error(e instanceof ApiError ? e.message : t('errors.generic')),
    })
  }

  return (
    <>
      <dl className="grid grid-cols-[8rem_1fr] gap-x-2 gap-y-2 text-sm">
        {facts
          .filter(([, value]) => value)
          .map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="break-words">{value}</dd>
            </div>
          ))}
      </dl>

      <Field>
        <FieldLabel htmlFor={`status-${device.id}`}>{t('equipment.status')}</FieldLabel>
        <Select value={device.status} onValueChange={(v) => changeStatus(v as Equipment['status'])} disabled={!canWrite || update.isPending}>
          <SelectTrigger id={`status-${device.id}`} className="w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {EQUIPMENT_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`equipment.statuses.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <Separator />
      <section aria-labelledby={`history-${device.id}`} className="flex flex-col gap-2">
        <h3 id={`history-${device.id}`} className="font-semibold">
          {t('equipment.history')}
        </h3>
        {device.serviceHistory.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('equipment.historyEmpty')}</p>
        ) : (
          <ol className="flex flex-col divide-y rounded-md border">
            {device.serviceHistory.map((h) => (
              <li key={h.jobId} className="flex flex-col gap-1 px-3 py-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`/jobs/${h.jobId}`} className="font-medium underline-offset-4 hover:underline">
                    {h.title}
                  </Link>
                  <Badge variant="secondary">{t(`jobs.statuses.${h.status}`)}</Badge>
                </div>
                <p className="text-sm text-muted-foreground">
                  {[t(`jobs.types.${h.type}`), h.completedAt ? t('equipment.completedOn', { date: formatDateTime(h.completedAt) }) : h.startDate ? formatDate(h.startDate) : null]
                    .filter(Boolean)
                    .join('، ')}
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section aria-labelledby={`warranties-${device.id}`} className="flex flex-col gap-2">
        <h3 id={`warranties-${device.id}`} className="font-semibold">
          {t('equipment.warranties')}
        </h3>
        {device.warranties.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('equipment.warrantiesEmpty')}</p>
        ) : (
          <ul className="flex flex-col divide-y rounded-md border">
            {device.warranties.map((w) => (
              <li key={w.id} className="flex flex-wrap items-center gap-2 px-3 py-2 text-sm">
                <span className="font-medium">{w.title}</span>
                <Badge variant={w.status === 'ACTIVE' ? 'secondary' : 'outline'}>{t(`equipment.warrantyStatuses.${w.status}`)}</Badge>
                <span className="text-muted-foreground">
                  {[w.provider, w.endDate ? t('equipment.warrantyEnds', { date: formatDate(w.endDate) }) : null].filter(Boolean).join('، ')}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
