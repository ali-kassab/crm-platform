import { ArrowRight, MapPin } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/auth/auth-context'
import { useSubscription } from '@/lib/subscription'
import { useCustomer, useSites, type Site } from './api'
import { EquipmentSection } from './EquipmentSection'
import { NewSiteDialog } from './NewSiteDialog'

function SiteRow({ site }: { site: Site }) {
  const { t } = useTranslation()
  const place = [site.address?.line1, site.address?.city].filter(Boolean).join('، ')
  return (
    <li className="flex flex-col gap-1 px-4 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{site.name}</span>
        <Badge variant="secondary">{t(`sites.types.${site.type}`)}</Badge>
      </div>
      {place ? <p className="text-sm text-muted-foreground">{place}</p> : null}
      {site.accessInstructions ? (
        <p className="text-sm text-muted-foreground">
          {t('sites.access')}: {site.accessInstructions}
        </p>
      ) : null}
    </li>
  )
}

export function CustomerDetailPage() {
  const { id } = useParams()
  const { t } = useTranslation()
  const { can } = useAuth()
  const { data: subscription } = useSubscription()
  const customer = useCustomer(id)
  const sites = useSites(id)
  const canWrite = can('crm:write') && !subscription?.readOnly

  if (customer.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{t('errors.notFound')}</AlertDescription>
      </Alert>
    )
  }
  if (customer.isPending || !id) return <Skeleton className="h-40 w-full" />
  const c = customer.data

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="self-start">
        <Link to="/customers">
          <ArrowRight className="ltr:rotate-180" />
          {t('customers.title')}
        </Link>
      </Button>

      <header className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold">{c.fullName}</h1>
          <Badge variant="secondary">{t(`customers.${c.kind}`)}</Badge>
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
          {c.phone || c.mobile ? (
            <span dir="ltr" className="text-start">
              {c.phone ?? c.mobile}
            </span>
          ) : null}
          {c.email ? <span dir="ltr">{c.email}</span> : null}
        </div>
      </header>

      <section aria-labelledby="sites-heading" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <h2 id="sites-heading" className="text-lg font-semibold">
            {t('sites.title')}
          </h2>
          <div className="flex-1" />
          {can('crm:write') ? <NewSiteDialog contactId={id} disabled={!canWrite} size="sm" /> : null}
        </div>
        {sites.isPending ? (
          <Skeleton className="h-20 w-full" />
        ) : sites.isError ? (
          <Alert variant="destructive">
            <AlertDescription>{t('errors.generic')}</AlertDescription>
          </Alert>
        ) : sites.data.content.length === 0 ? (
          <Empty className="border">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <MapPin />
              </EmptyMedia>
              <EmptyTitle>{t('sites.emptyTitle')}</EmptyTitle>
              <EmptyDescription>{t('sites.emptyHint')}</EmptyDescription>
            </EmptyHeader>
            {can('crm:write') ? (
              <EmptyContent>
                <NewSiteDialog contactId={id} disabled={!canWrite} />
              </EmptyContent>
            ) : null}
          </Empty>
        ) : (
          <ul className="divide-y rounded-md border">
            {sites.data.content.map((s) => (
              <SiteRow key={s.id} site={s} />
            ))}
          </ul>
        )}
      </section>

      {can('ops:read') ? (
        <EquipmentSection
          contactId={id}
          sites={sites.data?.content ?? []}
          canWrite={can('ops:write') && !subscription?.readOnly}
          showAdd={can('ops:write')}
        />
      ) : null}
    </div>
  )
}
