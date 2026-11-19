import { Search, Users } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/auth/auth-context'
import { useSubscription } from '@/lib/subscription'
import { useCustomers } from './api'
import { NewCustomerDialog } from './NewCustomerDialog'

export function CustomersPage() {
  const { t } = useTranslation()
  const { can } = useAuth()
  const { data: subscription } = useSubscription()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const query = useDeferredValue(q)
  const { data, isPending, isError, refetch } = useCustomers(query, page)
  const canWrite = can('crm:write') && !subscription?.readOnly

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{t('customers.title')}</h1>
        <div className="flex-1" />
        {can('crm:write') && <NewCustomerDialog disabled={!canWrite} />}
      </div>
      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute start-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          placeholder={t('customers.searchPlaceholder')}
          aria-label={t('common.search')}
          className="ps-8"
        />
      </div>

      {isError ? (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center gap-3">
            {t('errors.generic')}
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              {t('common.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      ) : isPending ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Users />
            </EmptyMedia>
            <EmptyTitle>{t('customers.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{t('customers.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('customers.name')}</TableHead>
                  <TableHead>{t('customers.kind')}</TableHead>
                  <TableHead>{t('customers.phone')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('customers.city')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="font-medium">{c.fullName}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{t(`customers.${c.kind}`)}</Badge>
                    </TableCell>
                    <TableCell dir="ltr" className="text-start">
                      {c.phone ?? c.mobile}
                    </TableCell>
                    <TableCell className="hidden md:table-cell">{c.address?.city}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-2 text-sm">
              <span className="text-muted-foreground">
                {t('common.page', { page: data.page + 1, pages: data.totalPages })}
              </span>
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                {t('common.previous')}
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                {t('common.next')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
