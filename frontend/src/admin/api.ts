import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Page } from '@/lib/api'
import { adminApi } from './session'

// every admin query key starts with 'admin' so signing out drops all of them at once

export interface Limits {
  maxUsers: number | null
  maxActiveProjects: number | null
  maxJobsPerMonth: number | null
  maxStorageMb: number | null
}

export interface PlanView {
  id: string
  code: string
  nameAr: string
  nameEn: string
  descriptionAr: string | null
  descriptionEn: string | null
  priceMonthly: string
  priceYearly: string
  currency: string
  limits: Limits
}

export interface AdminPlan {
  plan: PlanView
  active: boolean
  sortOrder: number
}

export const METHOD_KINDS = ['VODAFONE_CASH', 'INSTAPAY', 'BANK_TRANSFER', 'OTHER'] as const
export type MethodKind = (typeof METHOD_KINDS)[number]

export interface MethodView {
  id: string
  kind: MethodKind
  labelAr: string
  labelEn: string
  account: string
  instructionsAr: string | null
  instructionsEn: string | null
}

export interface AdminMethod {
  method: MethodView
  active: boolean
  sortOrder: number
}

export const REQUEST_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'] as const
export type RequestStatus = (typeof REQUEST_STATUSES)[number]

export interface RequestView {
  id: string
  planId: string
  billingPeriod: 'MONTHLY' | 'YEARLY'
  amount: string
  currency: string
  paymentMethodId: string | null
  payerReference: string | null
  paidOn: string | null
  receiptFileId: string | null
  note: string | null
  status: RequestStatus
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface AdminRequest {
  workspaceId: string
  requestedBy: string
  reviewedBy: string | null
  request: RequestView
}

export const SUBSCRIPTION_STATUSES = ['TRIAL', 'ACTIVE', 'GRACE', 'EXPIRED', 'SUSPENDED'] as const

export function statusVariant(status: string | null) {
  return status === 'EXPIRED' || status === 'SUSPENDED' ? 'destructive' : status === 'GRACE' ? 'outline' : 'secondary'
}

export interface WorkspaceRow {
  id: string
  name: string
  slug: string
  createdAt: string
  deletedAt: string | null
  status: string | null
  currentPeriodEnd: string | null
  planCode: string | null
  members: number
  ownerEmail: string | null
}

export interface SubscriptionView {
  status: string
  readOnly: boolean
  plan: PlanView
  billingPeriod: string | null
  currentPeriodEnd: string | null
  graceEndsAt: string | null
  daysLeft: number
  usage: { users: number; activeProjects: number; jobsThisMonth: number; storageMb: number }
  pendingRequest: RequestView | null
}

export interface EventView {
  type: string
  fromStatus: string | null
  toStatus: string | null
  actorKind: string
  actorUserId: string | null
  detail: Record<string, unknown> | null
  occurredAt: string
}

export interface WorkspaceDetail {
  workspaceId: string
  subscription: SubscriptionView
  history: EventView[]
  requests: RequestView[]
}

export interface AdminUser {
  id: string
  email: string
  firstName: string
  lastName: string
}

// ------------------------------------------------------------------ reads

export function usePlans() {
  return useQuery({ queryKey: ['admin', 'plans'], queryFn: () => adminApi<AdminPlan[]>('/plans') })
}

export function useMethods() {
  return useQuery({ queryKey: ['admin', 'methods'], queryFn: () => adminApi<AdminMethod[]>('/payment-methods') })
}

export function useRequests(status: RequestStatus, page: number) {
  return useQuery({
    queryKey: ['admin', 'requests', { status, page }],
    queryFn: () => adminApi<Page<AdminRequest>>(`/subscription-requests?status=${status}&page=${page}&size=20`),
    placeholderData: keepPreviousData,
  })
}

/** Workspaces newest first; the list is capped at 200 rows by the API. */
export function useWorkspaces(status: string, q: string) {
  const params = new URLSearchParams({ limit: '200' })
  if (status) params.set('status', status)
  if (q) params.set('q', q)
  return useQuery({
    queryKey: ['admin', 'workspaces', { status, q }],
    queryFn: () => adminApi<WorkspaceRow[]>(`/workspaces?${params}`),
    placeholderData: keepPreviousData,
  })
}

export function useWorkspace(id: string) {
  return useQuery({ queryKey: ['admin', 'workspace', id], queryFn: () => adminApi<WorkspaceDetail>(`/workspaces/${id}`) })
}

// ------------------------------------------------------------------ writes

function useAdminMutation<V, R>(fn: (v: V) => Promise<R>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => void client.invalidateQueries({ queryKey: ['admin'] }),
  })
}

export const useReview = (id: string) =>
  useAdminMutation(({ decision, note }: { decision: 'approve' | 'reject'; note?: string }) =>
    adminApi<AdminRequest>(`/subscription-requests/${id}/${decision}`, { method: 'POST', body: { note } }),
  )

export const useWorkspaceAction = (id: string) =>
  useAdminMutation((body: { action: 'EXTEND' | 'SUSPEND' | 'REACTIVATE' | 'CHANGE_PLAN'; days?: number; planId?: string; note?: string }) =>
    adminApi<SubscriptionView>(`/workspaces/${id}/subscription`, { method: 'POST', body }),
  )

export const useSavePlan = (id?: string) =>
  useAdminMutation((body: Record<string, unknown>) =>
    adminApi<AdminPlan>(id ? `/plans/${id}` : '/plans', { method: id ? 'PATCH' : 'POST', body }),
  )

export const useSaveMethod = (id?: string) =>
  useAdminMutation((body: Record<string, unknown>) =>
    adminApi<AdminMethod>(id ? `/payment-methods/${id}` : '/payment-methods', { method: id ? 'PATCH' : 'POST', body }),
  )

export const receiptUrl = (id: string) => adminApi<{ url: string }>(`/subscription-requests/${id}/receipt-url`)

// ------------------------------------------------------------------ console (dashboard, companies, usage, health, audit, users)

/** Counts only; `apiRequests30d` is null when the counter store could not be read. */
export interface CompanyUsage {
  members: number
  activeUsers: number
  customers: number
  projects: number
  workOrders: number
  workOrdersThisMonth: number
  files: number
  storageBytes: number
  apiRequests30d: number | null
}

export interface CompanyRow {
  id: string
  name: string
  slug: string
  createdAt: string
  deletedAt: string | null
  status: string | null
  planCode: string | null
  planNameEn: string | null
  planNameAr: string | null
  billingPeriod: string | null
  currentPeriodEnd: string | null
  ownerEmail: string | null
  usage: CompanyUsage
}

export interface Activity {
  type: string
  fromStatus: string | null
  toStatus: string | null
  actorKind: string
  actorEmail: string | null
  companyId: string | null
  companyName: string | null
  entityType: string
  entityId: string | null
  occurredAt: string
}

export interface Dashboard {
  generatedAt: string
  companies: {
    total: number
    operational: number
    trial: number
    paid: number
    grace: number
    expired: number
    suspended: number
    deleted: number
    newLast30Days: number
    endingWithin7Days: number
  }
  users: { total: number; active: number; inactive: number; platformAdmins: number; newLast30Days: number; signedInLast30Days: number }
  usage: { customers: number; projects: number; workOrders: number; files: number; storageBytes: number; apiRequests30d: number | null }
  payments: { pending: number; approved: number; rejected: number; revenue: { currency: string; total: string; last30Days: string; payments: number }[] }
  recentActivity: Activity[]
}

export interface UsageReport {
  generatedAt: string
  totals: CompanyUsage
  metrics: { key: string; available: boolean; description: string }[]
  companies: Page<CompanyRow>
}

export type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'NOT_CHECKED'

export interface HealthReport {
  checkedAt: string
  overall: HealthStatus
  components: { name: string; status: HealthStatus; latencyMs: number | null; note: string | null }[]
  runtime: { startedAt: string; uptimeSeconds: number; heapUsedBytes: number; heapMaxBytes: number | null; threads: number; processors: number }
  requests: { total: number; clientErrors: number; serverErrors: number }
  database: { schemaVersion: string | null; pendingEvents: number; connections: number } | null
}

export interface AuditRow {
  id: string
  action: string
  entityType: string
  entityId: string | null
  actorUserId: string | null
  actorLabel: string | null
  actorEmail: string | null
  changes: Record<string, unknown> | null
  occurredAt: string
}

export interface PlatformUser {
  id: string
  email: string
  firstName: string
  lastName: string
  active: boolean
  platformAdmin: boolean
  createdAt: string
  lastLoginAt: string | null
  companies: { id: string; name: string; role: string; active: boolean }[]
}

/** Builds a query string, dropping empty values. */
export function query(params: Record<string, string | number | null | undefined>) {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== '' && v != null) p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}

export function useDashboard() {
  return useQuery({ queryKey: ['admin', 'dashboard'], queryFn: () => adminApi<Dashboard>('/dashboard') })
}

export interface CompanyQuery {
  q?: string
  status?: string
  endingWithinDays?: number
  page: number
  size?: number
  sort?: string
}

export function useCompanies(params: CompanyQuery) {
  return useQuery({
    queryKey: ['admin', 'companies', params],
    queryFn: () => adminApi<Page<CompanyRow>>(`/companies${query({ size: 20, ...params })}`),
    placeholderData: keepPreviousData,
  })
}

export function useCompany(id: string) {
  return useQuery({ queryKey: ['admin', 'company', id], queryFn: () => adminApi<CompanyRow>(`/companies/${id}`) })
}

export function useUsage(params: { q?: string; page: number; sort: string }) {
  return useQuery({
    queryKey: ['admin', 'usage', params],
    queryFn: () => adminApi<UsageReport>(`/usage${query({ size: 20, ...params })}`),
    placeholderData: keepPreviousData,
  })
}

export function useHealth() {
  return useQuery({ queryKey: ['admin', 'health'], queryFn: () => adminApi<HealthReport>('/health'), refetchInterval: 30_000 })
}

export function useActivity(limit: number) {
  return useQuery({ queryKey: ['admin', 'activity', limit], queryFn: () => adminApi<Activity[]>(`/activity?limit=${limit}`) })
}

export interface AuditQuery {
  action?: string
  entityType?: string
  entityId?: string
  from?: string
  to?: string
  page: number
}

export function useAuditLog(params: AuditQuery) {
  return useQuery({
    queryKey: ['admin', 'audit', params],
    queryFn: () => adminApi<Page<AuditRow>>(`/audit-log${query({ size: 25, ...params })}`),
    placeholderData: keepPreviousData,
  })
}

export function useAuditActions() {
  return useQuery({ queryKey: ['admin', 'audit-actions'], queryFn: () => adminApi<string[]>('/audit-log/actions') })
}

export interface UserQuery {
  q?: string
  status?: string
  kind?: string
  workspaceId?: string
  page: number
  size?: number
  sort?: string
}

export function useUsers(params: UserQuery) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => adminApi<Page<PlatformUser>>(`/users${query({ size: 20, ...params })}`),
    placeholderData: keepPreviousData,
  })
}

export const useUserAccess = (id: string) =>
  useAdminMutation(({ active, reason }: { active: boolean; reason?: string }) =>
    adminApi<PlatformUser>(`/users/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST', body: { reason } }),
  )

export const useUpdateAccount = () =>
  useAdminMutation((body: { firstName: string; lastName: string }) => adminApi<AdminUser>('/account', { method: 'PATCH', body }))

/** Bytes as a short human size (B, KB, MB, GB). */
export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = bytes / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${units[i]}`
}
