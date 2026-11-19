import { AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useSubscription } from '@/lib/subscription'

/** Grace and read-only warnings (D5); nothing is shown while the subscription is fine. */
export function SubscriptionBanner() {
  const { t, i18n } = useTranslation()
  const { data } = useSubscription()
  if (!data || (data.status !== 'GRACE' && !data.readOnly)) return null
  const date = data.graceEndsAt
    ? new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-EG' : 'en-EG', { dateStyle: 'medium' }).format(
        new Date(data.graceEndsAt),
      )
    : ''
  return (
    <Alert variant={data.readOnly ? 'destructive' : 'default'} className="mx-4 mt-4 w-auto">
      <AlertTriangle />
      <AlertDescription>
        {data.readOnly ? t('subscription.readOnlyBanner') : t('subscription.graceBanner', { date })}
      </AlertDescription>
    </Alert>
  )
}
