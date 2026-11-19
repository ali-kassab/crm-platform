import { BellOff, CheckCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate, formatDateTime } from '@/lib/format'
import { targetOf, useMarkAllRead, useMarkRead, useNotifications, type AppNotification } from './api'

const DATE_KEYS = ['endDate', 'dueDate', 'endsAt', 'graceEndsAt', 'periodEnd'] as const

/** Text is built here from the type and its id-free payload (ar/en), never sent ready-made by the server. */
function useMessage() {
  const { t } = useTranslation()
  return (n: AppNotification) => {
    const p = n.payload
    const date = DATE_KEYS.map((k) => p[k]).find((v): v is string => typeof v === 'string')
    return t(`notifications.types.${n.type}`, {
      ...p,
      when: typeof p.scheduledStart === 'string' ? formatDateTime(p.scheduledStart) : '',
      date: date ? formatDate(date) : '',
      defaultValue: t('notifications.unknown'),
    })
  }
}

function NotificationItem({ n }: { n: AppNotification }) {
  const message = useMessage()
  const navigate = useNavigate()
  const markRead = useMarkRead()
  const target = targetOf(n)

  function open() {
    if (!n.read) markRead.mutate(n.id)
    if (target) void navigate(target)
  }

  return (
    <li>
      <button
        type="button"
        onClick={open}
        className="flex w-full items-start gap-3 px-4 py-3 text-start hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none"
      >
        <span
          aria-hidden
          className={n.read ? 'mt-2 size-2 shrink-0 rounded-full' : 'mt-2 size-2 shrink-0 rounded-full bg-primary'}
        />
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className={n.read ? 'text-muted-foreground' : 'font-medium'}>{message(n)}</span>
          <span className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</span>
        </span>
      </button>
    </li>
  )
}

export function NotificationsPage() {
  const { t } = useTranslation()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const list = useNotifications(unreadOnly)
  const markAll = useMarkAllRead()
  const items = list.data?.pages.flatMap((p) => p.content) ?? []
  const hasUnread = items.some((n) => !n.read)

  return (
    <div className="flex max-w-3xl flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('notifications.title')}</h1>
        <div className="flex-1" />
        <Button variant="outline" size="sm" disabled={!hasUnread || markAll.isPending} onClick={() => markAll.mutate()}>
          {markAll.isPending ? <Spinner /> : <CheckCheck />}
          {t('notifications.markAllRead')}
        </Button>
      </div>
      <Tabs value={unreadOnly ? 'unread' : 'all'} onValueChange={(v) => setUnreadOnly(v === 'unread')}>
        <TabsList>
          <TabsTrigger value="all">{t('notifications.all')}</TabsTrigger>
          <TabsTrigger value="unread">{t('notifications.unreadOnly')}</TabsTrigger>
        </TabsList>
      </Tabs>

      {list.isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center gap-3">
            {t('errors.generic')}
            <Button size="sm" variant="outline" onClick={() => void list.refetch()}>
              {t('common.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      ) : list.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : items.length === 0 ? (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <BellOff />
            </EmptyMedia>
            <EmptyTitle>{unreadOnly ? t('notifications.noneUnread') : t('notifications.none')}</EmptyTitle>
            <EmptyDescription>{t('notifications.noneHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <ul className="divide-y rounded-md border">
            {items.map((n) => (
              <NotificationItem key={n.id} n={n} />
            ))}
          </ul>
          {list.hasNextPage ? (
            <Button
              variant="outline"
              className="self-center"
              disabled={list.isFetchingNextPage}
              onClick={() => void list.fetchNextPage()}
            >
              {list.isFetchingNextPage ? <Spinner /> : null}
              {t('notifications.more')}
            </Button>
          ) : null}
        </>
      )}
    </div>
  )
}
