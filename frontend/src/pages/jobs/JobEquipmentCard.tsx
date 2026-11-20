import { Cctv } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { ApiError } from '@/lib/api'
import { useJobEquipment, useSetJobEquipment, useSiteEquipment, type Equipment } from '@/pages/customers/equipment-api'

/** Devices this work order serves; they make up each device's service history. */
export function JobEquipmentCard({ jobId, siteId, canEdit }: { jobId: string; siteId: string | undefined; canEdit: boolean }) {
  const { t } = useTranslation()
  const linked = useJobEquipment(jobId)

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
        <CardTitle>{t('jobEquipment.title')}</CardTitle>
        {canEdit && linked.data ? <ChooseDevicesDialog jobId={jobId} siteId={siteId} linked={linked.data} /> : null}
      </CardHeader>
      <CardContent>
        {linked.isPending ? (
          <Skeleton className="h-12 w-full" />
        ) : linked.isError ? (
          <Alert variant="destructive">
            <AlertDescription>{t('errors.generic')}</AlertDescription>
          </Alert>
        ) : linked.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('jobEquipment.empty')}</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {linked.data.map((e) => (
              <li key={e.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                <Cctv className="size-4 text-muted-foreground" aria-hidden />
                <span className="font-medium">{e.name}</span>
                <Badge variant="secondary">{t(`equipment.categories.${e.category}`)}</Badge>
                {e.status !== 'ACTIVE' ? <Badge variant="destructive">{t(`equipment.statuses.${e.status}`)}</Badge> : null}
                {e.serialNumber ? (
                  <span className="text-muted-foreground" dir="ltr">
                    {e.serialNumber}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function ChooseDevicesDialog({ jobId, siteId, linked }: { jobId: string; siteId: string | undefined; linked: Equipment[] }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const choices = useSiteEquipment(siteId, open)
  const save = useSetJobEquipment(jobId)
  // removed devices can't be picked again, but stay listed while they are still linked
  const options = (choices.data?.content ?? []).filter((e) => e.status !== 'REMOVED' || picked.has(e.id))

  function onOpenChange(next: boolean) {
    if (next) setPicked(new Set(linked.map((e) => e.id)))
    setOpen(next)
  }

  function toggle(id: string, on: boolean) {
    setPicked((prev) => {
      const next = new Set(prev)
      if (on) next.add(id)
      else next.delete(id)
      return next
    })
  }

  function onSave() {
    save.mutate([...picked], {
      onSuccess: () => {
        toast.success(t('jobEquipment.saved'))
        setOpen(false)
      },
      onError: (e) => toast.error(e instanceof ApiError ? e.message : t('errors.generic')),
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          {t('jobEquipment.choose')}
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('jobEquipment.choose')}</DialogTitle>
          <DialogDescription>{t('jobEquipment.chooseHint')}</DialogDescription>
        </DialogHeader>
        {choices.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : choices.isError ? (
          <Alert variant="destructive">
            <AlertDescription>{t('errors.generic')}</AlertDescription>
          </Alert>
        ) : options.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('jobEquipment.noneAtSite')}</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {options.map((e) => (
              <li key={e.id} className="flex items-center gap-2">
                <Checkbox id={`pick-${e.id}`} checked={picked.has(e.id)} onCheckedChange={(v) => toggle(e.id, v === true)} />
                <Label htmlFor={`pick-${e.id}`} className="flex flex-wrap gap-x-2 font-normal">
                  <span className="font-medium">{e.name}</span>
                  <span className="text-muted-foreground">{t(`equipment.categories.${e.category}`)}</span>
                  {e.serialNumber ? (
                    <span className="text-muted-foreground" dir="ltr">
                      {e.serialNumber}
                    </span>
                  ) : null}
                </Label>
              </li>
            ))}
          </ul>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={onSave} disabled={save.isPending || choices.isPending}>
            {save.isPending ? <Spinner /> : null}
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
