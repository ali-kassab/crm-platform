import { CalendarClock, MapPin } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import { useSubscription } from '@/lib/subscription'
import { useMyVisits } from './api'
import { VisitActions } from './VisitActions'

/** The technician's day: upcoming and running visits, one card each (phone-first). */
export function VisitsPage() {
  const { t } = useTranslation()
  const { data, isPending } = useMyVisits()
  const { data: subscription } = useSubscription()
  const open = data?.content.filter((v) => v.status !== 'COMPLETED' && v.status !== 'CANCELLED' && v.status !== 'RESCHEDULED')

  return (
    <div className="flex max-w-3xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('visits.mine')}</h1>
      {isPending ? (
        <Skeleton className="h-28 w-full" />
      ) : !open || open.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <CalendarClock />
            </EmptyMedia>
            <EmptyTitle>{t('visits.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{t('visits.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        open.map((v) => (
          <Card key={v.id}>
            <CardHeader>
              <CardTitle className="text-base">{formatDateTime(v.scheduledStart)}</CardTitle>
              <CardDescription className="flex items-center gap-1">
                <MapPin className="size-3.5" />
                <Link to={`/jobs/${v.jobId}`} className="hover:underline">
                  {t('jobs.details')}
                </Link>
              </CardDescription>
              <CardAction>
                <Badge variant={v.status === 'MISSED' ? 'destructive' : 'secondary'}>{t(`visits.statuses.${v.status}`)}</Badge>
              </CardAction>
            </CardHeader>
            <CardContent className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-muted-foreground">{v.notes}</p>
              <VisitActions visit={v} disabled={subscription?.readOnly} />
            </CardContent>
          </Card>
        ))
      )}
    </div>
  )
}
