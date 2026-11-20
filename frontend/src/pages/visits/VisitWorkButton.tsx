import { NotebookPen } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import type { Visit } from '@/pages/jobs/api'

// the forms load only when a visit record is opened (bundle budget)
const VisitWork = lazy(() => import('./VisitWork').then((m) => ({ default: m.VisitWork })))

/** Opens the visit record: technical report, photos and materials used (R4 field evidence). */
export function VisitWorkButton({ visit, readOnly }: { visit: Visit; readOnly?: boolean }) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  if (visit.status === 'CANCELLED' || visit.status === 'RESCHEDULED') return null

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button size="sm" variant="outline">
          <NotebookPen />
          {t('work.open')}
        </Button>
      </SheetTrigger>
      {/* the sheet comes from the inline-end side: left in Arabic, right in English */}
      <SheetContent
        side={i18n.dir() === 'rtl' ? 'left' : 'right'}
        className="w-full overflow-y-auto data-[side=left]:sm:max-w-xl data-[side=right]:sm:max-w-xl"
      >
        <SheetHeader>
          <SheetTitle>{t('work.title')}</SheetTitle>
          <SheetDescription>{formatDateTime(visit.scheduledStart)}</SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-6">
          {open ? (
            <Suspense fallback={<Skeleton className="h-64 w-full" />}>
              <VisitWork visit={visit} readOnly={readOnly} />
            </Suspense>
          ) : null}
        </div>
      </SheetContent>
    </Sheet>
  )
}
