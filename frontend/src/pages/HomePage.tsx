import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useSubscription } from '@/lib/subscription'

export function HomePage() {
  const { t, i18n } = useTranslation()
  const { session, can } = useAuth()
  const { data: sub, isPending } = useSubscription()
  const planName = sub ? (i18n.language === 'ar' ? sub.plan.nameAr : sub.plan.nameEn) : ''

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">{t('home.welcome', { name: session?.user.firstName })}</h1>
        <p className="text-muted-foreground">{t('home.quickStart')}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>{t('home.workspace')}</CardDescription>
            <CardTitle>{session?.workspace.name}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center gap-2">
            {isPending ? (
              <Skeleton className="h-6 w-40" />
            ) : sub ? (
              <>
                <Badge variant={sub.readOnly ? 'destructive' : 'secondary'}>{t(`subscription.status.${sub.status}`)}</Badge>
                <span className="text-sm">{planName}</span>
                <span className="text-sm text-muted-foreground">
                  {sub.status === 'TRIAL'
                    ? t('home.trialEnds', { days: sub.daysLeft })
                    : t('home.periodEnds', { days: sub.daysLeft })}
                </span>
              </>
            ) : null}
          </CardContent>
        </Card>
        {can('crm:read') && (
          <Card>
            <CardHeader>
              <CardDescription>{t('nav.customers')}</CardDescription>
              <CardTitle>{t('customers.emptyHint')}</CardTitle>
            </CardHeader>
            <CardContent>
              <Button asChild variant="outline">
                <Link to="/customers">{t('nav.customers')}</Link>
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
