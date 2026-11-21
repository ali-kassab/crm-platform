import { CreditCard, Receipt } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { useCompanies, usePlans, useRequests, useWorkspaces, type RequestStatus } from './api'
import { CompanyStatus, Pager, QueryState } from './parts'

const VIEWS = ['ALL', 'ENDING', 'TRIAL', 'ACTIVE', 'GRACE', 'EXPIRED', 'SUSPENDED'] as const
type View = (typeof VIEWS)[number]

/** Every company's subscription (status, plan, period) and the record of manual payments. */
export function SubscriptionsPage() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const [view, setView] = useState<View>('ENDING')
  const [page, setPage] = useState(0)
  const subscriptions = useCompanies({
    status: view === 'ALL' || view === 'ENDING' ? '' : view,
    endingWithinDays: view === 'ENDING' ? 30 : undefined,
    sort: 'periodEnd,asc',
    page,
  })
  const data = subscriptions.data

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold">{t('admin.subscriptions.title')}</h1>
          <Link to="/admin/requests" className="text-sm text-primary underline-offset-4 hover:underline">
            {t('admin.subscriptions.reviewRequests')}
          </Link>
        </div>
        <Tabs
          value={view}
          onValueChange={(v) => {
            setView(v as View)
            setPage(0)
          }}
        >
          <TabsList className="flex-wrap">
            {VIEWS.map((v) => (
              <TabsTrigger key={v} value={v}>
                {v === 'ALL' ? t('admin.workspaces.allStatuses') : v === 'ENDING' ? t('admin.subscriptions.ending') : t(`subscription.status.${v}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <QueryState
          pending={subscriptions.isPending}
          error={subscriptions.error}
          empty={!!data && data.content.length === 0}
          emptyIcon={CreditCard}
          emptyTitle={t('admin.subscriptions.empty')}
          onRetry={() => void subscriptions.refetch()}
        >
          {data ? (
            <>
              <div className="overflow-x-auto rounded-md border bg-background">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('admin.companies.company')}</TableHead>
                      <TableHead>{t('admin.workspaces.status')}</TableHead>
                      <TableHead>{t('admin.requests.plan')}</TableHead>
                      <TableHead>{t('admin.subscriptions.period')}</TableHead>
                      <TableHead>{t('admin.companies.periodEnd')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.content.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell>
                          <Link to={`/admin/companies/${c.id}`} className="font-medium underline-offset-4 hover:underline">
                            {c.name}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <CompanyStatus status={c.status} deleted={!!c.deletedAt} />
                        </TableCell>
                        <TableCell>{(ar ? c.planNameAr : c.planNameEn) ?? '—'}</TableCell>
                        <TableCell>
                          {c.billingPeriod ? t(`subscriptionPage.${c.billingPeriod === 'YEARLY' ? 'yearly' : 'monthly'}`) : t('admin.subscriptions.trialPeriod')}
                        </TableCell>
                        <TableCell>{c.currentPeriodEnd ? formatDate(c.currentPeriodEnd) : '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <Pager page={page} pages={data.totalPages} total={data.totalElements} onPage={setPage} />
            </>
          ) : null}
        </QueryState>
      </div>

      <PaymentRecords />
    </div>
  )
}

function PaymentRecords() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const [status, setStatus] = useState<RequestStatus>('APPROVED')
  const [page, setPage] = useState(0)
  const records = useRequests(status, page)
  const workspaces = useWorkspaces('', '')
  const plans = usePlans()
  const workspaceName = new Map((workspaces.data ?? []).map((w) => [w.id, w.name]))
  const planName = new Map((plans.data ?? []).map((p) => [p.plan.id, ar ? p.plan.nameAr : p.plan.nameEn]))
  const data = records.data

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('admin.subscriptions.payments')}</CardTitle>
        <CardDescription>{t('admin.subscriptions.paymentsHint')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <Tabs
          value={status}
          onValueChange={(v) => {
            setStatus(v as RequestStatus)
            setPage(0)
          }}
        >
          <TabsList>
            {(['APPROVED', 'REJECTED', 'CANCELLED'] as const).map((s) => (
              <TabsTrigger key={s} value={s}>
                {t(`subscriptionPage.requestStatuses.${s}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <QueryState pending={records.isPending} error={records.error} empty={!!data && data.content.length === 0} emptyIcon={Receipt} emptyTitle={t('admin.requests.empty')}>
          {data ? (
            <>
              <ul className="flex flex-col divide-y text-sm">
                {data.content.map(({ workspaceId, request: r }) => (
                  <li key={r.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2">
                    <Link to={`/admin/companies/${workspaceId}`} className="font-medium underline-offset-4 hover:underline">
                      {workspaceName.get(workspaceId) ?? t('admin.workspaces.workspace')}
                    </Link>
                    <Badge variant="outline">{planName.get(r.planId) ?? '—'}</Badge>
                    <span className="tabular-nums">{formatMoney(r.amount, r.currency)}</span>
                    <span className="text-muted-foreground">{t(`subscriptionPage.${r.billingPeriod === 'YEARLY' ? 'yearly' : 'monthly'}`)}</span>
                    <span className="ms-auto text-muted-foreground">{formatDateTime(r.reviewedAt ?? r.createdAt)}</span>
                    {r.reviewNote ? <span className="w-full text-muted-foreground">{r.reviewNote}</span> : null}
                  </li>
                ))}
              </ul>
              <Pager page={page} pages={data.totalPages} onPage={setPage} />
            </>
          ) : null}
        </QueryState>
      </CardContent>
    </Card>
  )
}
