import { zodResolver } from '@hookform/resolvers/zod'
import { Check, FileImage, Inbox, X } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import {
  receiptUrl,
  REQUEST_STATUSES,
  useMethods,
  usePlans,
  useRequests,
  useReview,
  useWorkspaces,
  type AdminRequest,
  type RequestStatus,
} from './api'

/** Manual payments waiting for review (Vodafone Cash / InstaPay receipts), oldest first. */
export function RequestsPage() {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const [status, setStatus] = useState<RequestStatus>('PENDING')
  const [page, setPage] = useState(0)
  const requests = useRequests(status, page)
  // names for the ids in each request (all three lists are small)
  const workspaces = useWorkspaces('', '')
  const plans = usePlans()
  const methods = useMethods()
  const workspaceName = new Map((workspaces.data ?? []).map((w) => [w.id, w.name]))
  const planName = new Map((plans.data ?? []).map((p) => [p.plan.id, ar ? p.plan.nameAr : p.plan.nameEn]))
  const methodName = new Map((methods.data ?? []).map((m) => [m.method.id, ar ? m.method.labelAr : m.method.labelEn]))

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t('admin.requests.title')}</h1>
        <Tabs
          value={status}
          onValueChange={(v) => {
            setStatus(v as RequestStatus)
            setPage(0)
          }}
        >
          <TabsList>
            {REQUEST_STATUSES.map((s) => (
              <TabsTrigger key={s} value={s}>
                {t(`subscriptionPage.requestStatuses.${s}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {requests.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : requests.isError ? (
        <Alert variant="destructive">
          <AlertDescription>{t('errors.generic')}</AlertDescription>
        </Alert>
      ) : requests.data.content.length === 0 ? (
        <Empty className="border bg-background">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <Inbox />
            </EmptyMedia>
            <EmptyTitle>{t('admin.requests.empty')}</EmptyTitle>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <ul className="flex flex-col divide-y rounded-md border bg-background">
            {requests.data.content.map((r) => (
              <RequestRow
                key={r.request.id}
                item={r}
                workspace={workspaceName.get(r.workspaceId)}
                plan={planName.get(r.request.planId)}
                method={r.request.paymentMethodId ? methodName.get(r.request.paymentMethodId) : undefined}
              />
            ))}
          </ul>
          {requests.data.totalPages > 1 ? (
            <div className="flex items-center justify-end gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                {t('common.previous')}
              </Button>
              <span className="text-sm text-muted-foreground">{t('common.page', { page: page + 1, pages: requests.data.totalPages })}</span>
              <Button variant="outline" size="sm" disabled={page + 1 >= requests.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                {t('common.next')}
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}

function RequestRow({ item, workspace, plan, method }: { item: AdminRequest; workspace?: string; plan?: string; method?: string }) {
  const { t } = useTranslation()
  const r = item.request
  const [opening, setOpening] = useState(false)

  async function openReceipt() {
    setOpening(true)
    try {
      const { url } = await receiptUrl(r.id)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : t('errors.generic'))
    } finally {
      setOpening(false)
    }
  }

  const details = [
    [t('admin.requests.plan'), [plan, t(`subscriptionPage.${r.billingPeriod === 'YEARLY' ? 'yearly' : 'monthly'}`)].filter(Boolean).join('، ')],
    [t('admin.requests.amount'), formatMoney(r.amount, r.currency)],
    [t('subscriptionPage.method'), method],
    [t('subscriptionPage.reference'), r.payerReference],
    [t('subscriptionPage.paidOn'), r.paidOn ? formatDate(r.paidOn) : null],
    [t('admin.requests.note'), r.note],
    [t('admin.requests.reviewNote'), r.reviewNote],
  ].filter(([, v]) => v) as [string, string][]

  return (
    <li className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Link to={`/admin/workspaces/${item.workspaceId}`} className="font-medium underline-offset-4 hover:underline">
          {workspace ?? item.workspaceId}
        </Link>
        <Badge variant={r.status === 'PENDING' ? 'default' : r.status === 'APPROVED' ? 'secondary' : 'outline'}>
          {t(`subscriptionPage.requestStatuses.${r.status}`)}
        </Badge>
        <span className="text-sm text-muted-foreground">{formatDateTime(r.createdAt)}</span>
      </div>
      <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
        {details.map(([label, value]) => (
          <div key={label} className="flex gap-2">
            <dt className="text-muted-foreground">{label}:</dt>
            <dd className="break-words">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex flex-wrap gap-2">
        {r.receiptFileId ? (
          <Button size="sm" variant="outline" onClick={() => void openReceipt()} disabled={opening}>
            {opening ? <Spinner /> : <FileImage />}
            {t('admin.requests.receipt')}
          </Button>
        ) : (
          <span className="text-sm text-muted-foreground">{t('admin.requests.noReceipt')}</span>
        )}
        {r.status === 'PENDING' ? (
          <>
            <ReviewDialog id={r.id} decision="approve" />
            <ReviewDialog id={r.id} decision="reject" />
          </>
        ) : null}
      </div>
    </li>
  )
}

function ReviewDialog({ id, decision }: { id: string; decision: 'approve' | 'reject' }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const review = useReview(id)
  const schema = z.object({
    note: decision === 'reject' ? z.string().trim().min(1, t('admin.requests.reasonRequired')).max(1000) : z.string().trim().max(1000),
  })
  type Values = z.infer<typeof schema>
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { note: '' } })

  async function onSubmit(v: Values) {
    setError(null)
    try {
      await review.mutateAsync({ decision, note: v.note || undefined })
      toast.success(t(`admin.requests.${decision}d`))
      setOpen(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('errors.generic'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant={decision === 'approve' ? 'default' : 'outline'}>
          {decision === 'approve' ? <Check /> : <X />}
          {t(`admin.requests.${decision}`)}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`admin.requests.${decision}Title`)}</DialogTitle>
          <DialogDescription>{t(`admin.requests.${decision}Hint`)}</DialogDescription>
        </DialogHeader>
        <form id={`review-${decision}-${id}`} onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <FieldGroup>
            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <Controller
              name="note"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor={`note-${decision}-${id}`}>
                    {t(decision === 'reject' ? 'admin.requests.reason' : 'admin.requests.reviewNote')}
                  </FieldLabel>
                  <Textarea {...field} id={`note-${decision}-${id}`} rows={3} aria-invalid={fieldState.invalid} />
                  <FieldError errors={[fieldState.error]} />
                </Field>
              )}
            />
          </FieldGroup>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={`review-${decision}-${id}`} variant={decision === 'reject' ? 'destructive' : 'default'} disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? <Spinner /> : null}
            {t(`admin.requests.${decision}`)}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
