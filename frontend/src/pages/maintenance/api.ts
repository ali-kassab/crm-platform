import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'

export const FREQUENCIES = ['MONTHLY', 'QUARTERLY', 'SEMI_ANNUAL', 'ANNUAL', 'CUSTOM'] as const
export type Frequency = (typeof FREQUENCIES)[number]
export type PlanStatus = 'ACTIVE' | 'PAUSED' | 'ENDED'

export interface Plan {
  id: string
  contactId: string
  locationId: string
  title: string
  frequency: Frequency
  intervalDays: number | null
  startDate: string
  endDate: string | null
  nextDueDate: string | null
  responseHours: number | null
  includedVisits: number | null
  generatedCount: number
  status: PlanStatus
}

export interface Occurrence {
  id: string
  planId: string
  planTitle: string
  dueDate: string
  jobId: string | null
  jobStatus: string | null
  overdue: boolean
}

export interface NewPlan {
  contactId: string
  locationId: string
  title: string
  frequency: Frequency
  intervalDays?: number
  startDate: string
  endDate?: string
  responseHours?: number
  includedVisits?: number
}

// under ['maintenance'] so the realtime MAINTENANCE_PLAN hint can be wired to one prefix later
export function usePlans(status: string, page: number) {
  return useQuery({
    queryKey: ['maintenance', 'plans', { status, page }],
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page), size: '20', sort: 'nextDueDate,asc' })
      if (status) p.set('status', status)
      return api<Page<Plan>>(`/maintenance-plans?${p}`)
    },
    placeholderData: keepPreviousData,
  })
}

export function useOverdue() {
  return useQuery({
    queryKey: ['maintenance', 'overdue'],
    queryFn: () => api<Occurrence[]>('/maintenance-plans/overdue'),
  })
}

export function useCreatePlan() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (p: NewPlan) => api<Plan>('/maintenance-plans', { method: 'POST', body: p }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['maintenance'] }),
  })
}

export function useSetPlanStatus() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'PAUSED' }) =>
      api<Plan>(`/maintenance-plans/${id}`, { method: 'PATCH', body: { status } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['maintenance'] }),
  })
}
