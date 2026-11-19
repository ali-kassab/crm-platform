import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

export const PERIODS = ['WEEK', 'MONTH', 'QUARTER', 'YEAR', 'ALL'] as const
export type Period = (typeof PERIODS)[number]

/** GET /reports/dashboard (R12). Counts only; `finance` is absent without finance:read. */
export interface Dashboard {
  period: Period
  from?: string
  to: string
  timeZone: string
  jobs: { open: number; openByStatus: Record<string, number>; slaBreached: number; created: number; completed: number }
  visits: { today: number; overdue: number; completed: number; missed: number }
  maintenance: { overdue: number; warrantiesExpiringSoon: number }
  finance?: { received: string; paid: string; outstanding: string }
}

export function useDashboard(period: Period) {
  return useQuery({
    queryKey: ['dashboard', period],
    queryFn: () => api<Dashboard>(`/reports/dashboard?period=${period}`),
    placeholderData: keepPreviousData, // switching periods keeps the numbers on screen
  })
}
