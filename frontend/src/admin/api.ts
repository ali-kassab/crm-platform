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
