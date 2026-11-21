import { Building2 } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDate, formatNumber } from '@/lib/format'
import { formatBytes, SUBSCRIPTION_STATUSES, useCompanies } from './api'
import { CompanyStatus, Pager, QueryState } from './parts'

const ALL = 'ALL'
const COMPANY_FILTERS = ['OPERATIONAL', ...SUBSCRIPTION_STATUSES, 'DELETED'] as const
const SORTS = ['createdAt,desc', 'createdAt,asc', 'name,asc', 'periodEnd,asc', 'users,desc', 'storage,desc'] as const

/** Every registered company (workspace) with its subscription and aggregate usage. */
export function CompaniesPage() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const [q, setQ] = useState('')
  const [status, setStatus] = useState<string>(ALL)
  const [sort, setSort] = useState<string>(SORTS[0])
  const [page, setPage] = useState(0)
  const query = useDeferredValue(q.trim())
  const companies = useCompanies({ q: query, status: status === ALL ? '' : status, sort, page })
  const data = companies.data

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('admin.companies.title')}</h1>
      <div className="flex flex-wrap gap-2">
        <Input
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          placeholder={t('admin.workspaces.search')}
          aria-label={t('common.search')}
          className="max-w-xs bg-background"
        />
        <Select
          value={status}
          onValueChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-48 bg-background" aria-label={t('admin.workspaces.status')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t('admin.workspaces.allStatuses')}</SelectItem>
            {COMPANY_FILTERS.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`admin.companies.filters.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={sort}
          onValueChange={(v) => {
            setSort(v)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-48 bg-background" aria-label={t('admin.sortBy')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {SORTS.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`admin.companies.sorts.${s.replace(',', '_')}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <QueryState
        pending={companies.isPending}
        error={companies.error}
        empty={!!data && data.content.length === 0}
        emptyIcon={Building2}
        emptyTitle={t('admin.workspaces.empty')}
        onRetry={() => void companies.refetch()}
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
                    <TableHead>{t('admin.companies.periodEnd')}</TableHead>
                    <TableHead className="text-end">{t('admin.usage.users')}</TableHead>
                    <TableHead className="text-end">{t('admin.usage.customers')}</TableHead>
                    <TableHead className="text-end">{t('admin.usage.workOrders')}</TableHead>
                    <TableHead className="text-end">{t('admin.usage.storage')}</TableHead>
                    <TableHead>{t('admin.companies.registered')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>
                        <Link to={`/admin/companies/${c.id}`} className="font-medium underline-offset-4 hover:underline">
                          {c.name}
                        </Link>
                        {c.ownerEmail ? (
                          <div className="text-xs text-muted-foreground" dir="ltr">
                            {c.ownerEmail}
                          </div>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <CompanyStatus status={c.status} deleted={!!c.deletedAt} />
                      </TableCell>
                      <TableCell>{(ar ? c.planNameAr : c.planNameEn) ?? c.planCode ?? '—'}</TableCell>
                      <TableCell>{c.currentPeriodEnd ? formatDate(c.currentPeriodEnd) : '—'}</TableCell>
                      <TableCell className="text-end tabular-nums">{formatNumber(c.usage.activeUsers)}</TableCell>
                      <TableCell className="text-end tabular-nums">{formatNumber(c.usage.customers)}</TableCell>
                      <TableCell className="text-end tabular-nums">{formatNumber(c.usage.workOrders)}</TableCell>
                      <TableCell className="text-end tabular-nums" dir="ltr">
                        {formatBytes(c.usage.storageBytes)}
                      </TableCell>
                      <TableCell>{formatDate(c.createdAt)}</TableCell>
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
  )
}
