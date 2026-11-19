import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { formatNumber } from '@/lib/format'
import { useUnreadCount } from './api'

/** Header bell: unread count, opens the notifications screen. */
export function NotificationBell({ enabled }: { enabled: boolean }) {
  const { t } = useTranslation()
  const { data } = useUnreadCount(enabled)
  const unread = data?.unread ?? 0
  const label = unread > 0 ? t('notifications.bellUnread', { count: unread }) : t('notifications.title')
  return (
    <Button asChild variant="ghost" size="icon" className="relative">
      <Link to="/notifications" aria-label={label} title={label}>
        <Bell />
        {unread > 0 ? (
          <span
            aria-hidden
            className="absolute -top-0.5 -end-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-none font-semibold text-white"
          >
            {unread > 99 ? formatNumber(99) + '+' : formatNumber(unread)}
          </span>
        ) : null}
      </Link>
    </Button>
  )
}
