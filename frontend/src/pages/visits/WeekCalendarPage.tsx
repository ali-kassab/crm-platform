import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { api, type Page } from '@/lib/api'
import type { Visit } from '@/pages/jobs/api'

const DAY_MS = 86_400_000

/** Saturday 00:00 local time of the week containing `date` (the working week in Egypt starts on Saturday). */
function weekStart(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const sinceSaturday = (d.getDay() + 1) % 7
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - sinceSaturday)
}

function addDays(d: Date, n: number) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n)
}

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function useWeekVisits(start: Date) {
  const from = start.toISOString()
  const to = new Date(start.getTime() + 7 * DAY_MS).toISOString()
  return useQuery({
    queryKey: ['visits', 'week', from],
    queryFn: () => api<Page<Visit>>(`/visits?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&size=100&sort=scheduledStart,asc`),
  })
}

const STATUS_BADGE: Record<Visit['status'], 'default' | 'secondary' | 'destructive' | 'outline'> = {
  SCHEDULED: 'secondary',
  IN_PROGRESS: 'default',
  COMPLETED: 'outline',
  MISSED: 'destructive',
  CANCELLED: 'outline',
  RESCHEDULED: 'outline',
}

/** All visits of one week, day by day: who goes where and when (office view). */
export function WeekCalendarPage() {
  const { t, i18n } = useTranslation()
  const [start, setStart] = useState(() => weekStart(new Date()))
  const week = useWeekVisits(start)
  const locale = i18n.language === 'ar' ? 'ar-EG' : 'en-EG'
  const dayName = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'short' })
  const time = new Intl.DateTimeFormat(locale, { timeStyle: 'short' })
  const range = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' })
  const today = new Date()
  const days = Array.from({ length: 7 }, (_, i) => addDays(start, i))
  const visits = (week.data?.content ?? []).filter((v) => v.status !== 'CANCELLED' && v.status !== 'RESCHEDULED')
  const rtl = i18n.dir() === 'rtl'

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('calendar.title')}</h1>
        <div className="flex-1" />
        <div className="flex items-center gap-2">
          <Button size="icon" variant="outline" aria-label={t('calendar.previous')} onClick={() => setStart((s) => addDays(s, -7))}>
            {rtl ? <ChevronRight /> : <ChevronLeft />}
          </Button>
          <Button variant="outline" onClick={() => setStart(weekStart(new Date()))}>
            {t('calendar.thisWeek')}
          </Button>
          <Button size="icon" variant="outline" aria-label={t('calendar.next')} onClick={() => setStart((s) => addDays(s, 7))}>
            {rtl ? <ChevronLeft /> : <ChevronRight />}
          </Button>
        </div>
      </div>
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {range.format(start)} – {range.format(addDays(start, 6))}
      </p>

      {week.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : week.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        // one column per day on wide screens, a list of days on phones
        <ol className="grid gap-3 lg:grid-cols-7">
          {days.map((day) => {
            const items = visits.filter((v) => sameDay(new Date(v.scheduledStart), day))
            const isToday = sameDay(day, today)
            return (
              <li key={day.toISOString()} className={isToday ? 'rounded-md border border-primary p-2' : 'rounded-md border p-2'}>
                <h2 className="mb-2 text-sm font-semibold">
                  {dayName.format(day)}
                  {isToday ? <span className="sr-only"> ({t('calendar.today')})</span> : null}
                </h2>
                {items.length === 0 ? (
                  <p className="text-xs text-muted-foreground">{t('calendar.free')}</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {items.map((v) => (
                      <li key={v.id}>
                        <Link
                          to={`/jobs/${v.jobId}`}
                          className="flex flex-col gap-1 rounded-sm bg-muted/50 p-2 text-xs hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                        >
                          <span className="font-medium">
                            {time.format(new Date(v.scheduledStart))} – {time.format(new Date(v.scheduledEnd))}
                          </span>
                          <Badge variant={STATUS_BADGE[v.status]} className="self-start">
                            {t(`visits.statuses.${v.status}`)}
                          </Badge>
                          {v.assignees.length > 0 ? (
                            <span className="text-muted-foreground">{v.assignees.map((a) => a.name ?? '—').join('، ')}</span>
                          ) : null}
                          {v.notes ? <span className="line-clamp-2">{v.notes}</span> : null}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}
