import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useAuth } from '@/auth/auth-context'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { api, ApiError } from '@/lib/api'
import { formatDate, formatDateTime } from '@/lib/format'
import { useSubscription } from '@/lib/subscription'

interface Plan {
  id: string
  code: string
  nameAr: string
  nameEn: string
  descriptionAr: string | null
  descriptionEn: string | null
  priceMonthly: string
  priceYearly: string
  limits: { maxUsers: number | null; maxJobsPerMonth: number | null; maxStorageMb: number | null }
}
interface Method {
  id: string
  labelAr: string
  labelEn: string
  account: string
  instructionsAr: string | null
  instructionsEn: string | null
}
interface PaymentRequest {
  id: string
  planId: string
  billingPeriod: string
  amount: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  reviewNote: string | null
  createdAt: string
}

export function SubscriptionPage() {
  const { t, i18n } = useTranslation()
  const { can } = useAuth()
  const ar = i18n.language === 'ar'
  const { data: sub, isPending } = useSubscription()
  const plans = useQuery({ queryKey: ['plans'], queryFn: () => api<Plan[]>('/subscription/plans') })
  const methods = useQuery({ queryKey: ['payment-methods'], queryFn: () => api<Method[]>('/subscription/payment-methods') })
  const manage = can('workspace:manage')
  const requests = useQuery({
    queryKey: ['subscription-requests'],
    queryFn: () => api<PaymentRequest[]>('/subscription/requests'),
    enabled: manage,
  })

  if (isPending || !sub) return <Skeleton className="h-48 w-full" />
  const planName = (p: { nameAr: string; nameEn: string }) => (ar ? p.nameAr : p.nameEn)
  const limit = (n: number | null) => (n == null ? t('subscriptionPage.unlimited') : String(n))
  const current = plans.data?.find((p) => p.code === sub.plan.code)
  const pending = requests.data?.find((r) => r.status === 'PENDING')

  return (
    <div className="flex max-w-4xl flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t('subscriptionPage.title')}</h1>
      <Card>
        <CardHeader>
          <CardDescription>{t('subscriptionPage.current')}</CardDescription>
          <CardTitle className="flex flex-wrap items-center gap-2">
            {planName(sub.plan)}
            <Badge variant={sub.readOnly ? 'destructive' : 'secondary'}>{t(`subscription.status.${sub.status}`)}</Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="grid gap-2 text-sm sm:grid-cols-3">
          <Usage label={t('subscriptionPage.users')} value={`${sub.usage.users} / ${limit(current?.limits.maxUsers ?? null)}`} />
          <Usage
            label={t('subscriptionPage.jobsThisMonth')}
            value={`${sub.usage.jobsThisMonth} / ${limit(current?.limits.maxJobsPerMonth ?? null)}`}
          />
          <Usage
            label={t('subscriptionPage.storage')}
            value={`${sub.usage.storageMb} MB / ${current?.limits.maxStorageMb == null ? t('subscriptionPage.unlimited') : `${current.limits.maxStorageMb} MB`}`}
          />
          <p className="text-muted-foreground sm:col-span-3">
            {sub.status === 'TRIAL' ? t('home.trialEnds', { days: sub.daysLeft }) : t('home.periodEnds', { days: sub.daysLeft })}
            {' · '}
            {formatDate(sub.currentPeriodEnd)}
          </p>
        </CardContent>
      </Card>

      {manage &&
        (pending ? (
          <Alert>
            <AlertDescription>{t('subscriptionPage.pending')}</AlertDescription>
          </Alert>
        ) : (
          <RenewForm plans={plans.data ?? []} methods={methods.data ?? []} />
        ))}

      {manage && (requests.data?.length ?? 0) > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>{t('subscriptionPage.history')}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            {requests.data!.map((r) => (
              <div key={r.id} className="flex flex-wrap items-center gap-2 border-b pb-2 last:border-0">
                <span>{formatDateTime(r.createdAt)}</span>
                <span className="text-muted-foreground">{r.amount} EGP</span>
                <Badge variant={r.status === 'REJECTED' ? 'destructive' : 'secondary'}>
                  {t(`subscriptionPage.requestStatuses.${r.status}`)}
                </Badge>
                {r.reviewNote && <span className="text-muted-foreground">{r.reviewNote}</span>}
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function Usage({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border p-3">
      <p className="text-muted-foreground">{label}</p>
      <p className="font-semibold" dir="ltr">
        {value}
      </p>
    </div>
  )
}

function RenewForm({ plans, methods }: { plans: Plan[]; methods: Method[] }) {
  const { t, i18n } = useTranslation()
  const ar = i18n.language === 'ar'
  const client = useQueryClient()
  const [planId, setPlanId] = useState('')
  const [period, setPeriod] = useState<'MONTHLY' | 'YEARLY'>('MONTHLY')
  const [methodId, setMethodId] = useState('')
  const [reference, setReference] = useState('')
  const [paidOn, setPaidOn] = useState(new Date().toISOString().slice(0, 10))
  const [receipt, setReceipt] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const method = methods.find((m) => m.id === methodId)

  const submit = useMutation({
    mutationFn: async () => {
      const form = new FormData()
      form.append('files', receipt!)
      const uploaded = await api<{ files: { id: string }[] }>('/files?kind=IMAGE&purpose=RECEIPT', {
        method: 'POST',
        body: form,
      })
      return api('/subscription/requests', {
        method: 'POST',
        body: {
          planId,
          billingPeriod: period,
          paymentMethodId: methodId,
          payerReference: reference,
          paidOn,
          receiptFileId: uploaded.files[0].id,
        },
      })
    },
    onSuccess: () => {
      toast.success(t('subscriptionPage.submitted'))
      void client.invalidateQueries({ queryKey: ['subscription-requests'] })
      void client.invalidateQueries({ queryKey: ['subscription'] })
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : t('errors.generic')),
  })

  const ready = planId && methodId && reference.trim() && paidOn && receipt

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('subscriptionPage.renew')}</CardTitle>
      </CardHeader>
      <CardContent>
        <FieldGroup>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <Tabs value={period} onValueChange={(v) => setPeriod(v as 'MONTHLY' | 'YEARLY')}>
            <TabsList>
              <TabsTrigger value="MONTHLY">{t('subscriptionPage.monthly')}</TabsTrigger>
              <TabsTrigger value="YEARLY">{t('subscriptionPage.yearly')}</TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label={t('subscriptionPage.plan')}>
            {plans.map((p) => (
              <Button
                key={p.id}
                type="button"
                role="radio"
                aria-checked={planId === p.id}
                variant={planId === p.id ? 'default' : 'outline'}
                className="h-auto flex-col items-start gap-1 p-4 text-start whitespace-normal"
                onClick={() => setPlanId(p.id)}
              >
                <span className="font-semibold">{ar ? p.nameAr : p.nameEn}</span>
                <span className="text-xs opacity-80">
                  {period === 'MONTHLY'
                    ? t('subscriptionPage.perMonth', { price: p.priceMonthly })
                    : t('subscriptionPage.perYear', { price: p.priceYearly })}
                </span>
                <span className="text-xs opacity-80">{ar ? p.descriptionAr : p.descriptionEn}</span>
              </Button>
            ))}
          </div>
          {methods.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('subscriptionPage.noMethods')}</p>
          ) : (
            <Field>
              <FieldLabel htmlFor="s-method">{t('subscriptionPage.method')}</FieldLabel>
              <Select value={methodId} onValueChange={setMethodId}>
                <SelectTrigger id="s-method">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {methods.map((m) => (
                    <SelectItem key={m.id} value={m.id}>
                      {ar ? m.labelAr : m.labelEn}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {method && (
                <FieldDescription>
                  {t('subscriptionPage.payTo')}: <span dir="ltr" className="font-semibold">{method.account}</span>
                  {(ar ? method.instructionsAr : method.instructionsEn) && ` — ${ar ? method.instructionsAr : method.instructionsEn}`}
                </FieldDescription>
              )}
            </Field>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor="s-ref">{t('subscriptionPage.reference')}</FieldLabel>
              <Input id="s-ref" dir="ltr" value={reference} onChange={(e) => setReference(e.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="s-paid">{t('subscriptionPage.paidOn')}</FieldLabel>
              <Input id="s-paid" type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="s-receipt">{t('subscriptionPage.receipt')}</FieldLabel>
            <Input id="s-receipt" type="file" accept="image/*" onChange={(e) => setReceipt(e.target.files?.[0] ?? null)} />
          </Field>
          <Button disabled={!ready || submit.isPending} onClick={() => submit.mutate()}>
            {submit.isPending && <Spinner />}
            {t('subscriptionPage.submit')}
          </Button>
        </FieldGroup>
      </CardContent>
    </Card>
  )
}
