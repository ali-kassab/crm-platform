import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Wallet } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/auth-context'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDate, formatMoney } from '@/lib/format'

interface Outstanding {
  content: {
    contactId: string
    customerName: string
    phone: string | null
    contract: string
    received: string
    remaining: string
    lastPaymentDate: string | null
  }[]
  totalElements: number
  pageRemaining: string
}

const SIZE = 20

export function FinancePage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const currency = session?.workspace.currency ?? 'EGP'
  const [page, setPage] = useState(0)
  const { data, isPending } = useQuery({
    queryKey: ['outstanding', page],
    queryFn: () => api<Outstanding>(`/accounting/outstanding?page=${page}&size=${SIZE}`),
    placeholderData: keepPreviousData,
  })
  const pages = data ? Math.max(1, Math.ceil(data.totalElements / SIZE)) : 1

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('finance.outstanding')}</h1>
      {isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : data.content.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Wallet />
            </EmptyMedia>
            <EmptyTitle>{t('finance.emptyTitle')}</EmptyTitle>
            <EmptyDescription>{t('finance.emptyHint')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('jobs.customer')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('finance.contract')}</TableHead>
                  <TableHead className="hidden md:table-cell">{t('finance.received')}</TableHead>
                  <TableHead>{t('finance.remaining')}</TableHead>
                  <TableHead className="hidden lg:table-cell">{t('finance.lastPayment')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((r) => (
                  <TableRow key={r.contactId}>
                    <TableCell className="font-medium">
                      {r.customerName}
                      {r.phone && (
                        <a href={`tel:${r.phone}`} dir="ltr" className="ms-2 text-xs text-primary">
                          {r.phone}
                        </a>
                      )}
                    </TableCell>
                    <TableCell className="hidden md:table-cell">{formatMoney(r.contract, currency)}</TableCell>
                    <TableCell className="hidden md:table-cell">{formatMoney(r.received, currency)}</TableCell>
                    <TableCell className="font-semibold">{formatMoney(r.remaining, currency)}</TableCell>
                    <TableCell className="hidden lg:table-cell">{formatDate(r.lastPaymentDate)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell colSpan={3} className="hidden md:table-cell">
                    {t('finance.pageTotal')}
                  </TableCell>
                  <TableCell className="md:hidden">{t('finance.pageTotal')}</TableCell>
                  <TableCell className="font-semibold">{formatMoney(data.pageRemaining, currency)}</TableCell>
                  <TableCell className="hidden lg:table-cell" />
                </TableRow>
              </TableFooter>
            </Table>
          </div>
          {pages > 1 && (
            <div className="flex items-center justify-end gap-2 text-sm">
              <span className="text-muted-foreground">{t('common.page', { page: page + 1, pages })}</span>
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                {t('common.previous')}
              </Button>
              <Button size="sm" variant="outline" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>
                {t('common.next')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
