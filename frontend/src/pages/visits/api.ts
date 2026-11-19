import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'
import type { Visit } from '@/pages/jobs/api'

export const OUTCOMES = ['RESOLVED', 'FOLLOW_UP_REQUIRED', 'NOT_RESOLVED', 'CUSTOMER_UNAVAILABLE'] as const

/** Technicians only ever get their own visits back (the server enforces it). */
export function useMyVisits() {
  return useQuery({
    queryKey: ['visits', 'mine'],
    queryFn: () => api<Page<Visit>>('/visits?assignee=me&size=50&sort=scheduledStart,asc'),
  })
}

function useVisitAction<T>(path: (id: string) => string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body?: T }) => api<Visit>(path(id), { method: 'POST', body }),
    onSuccess: (visit) => {
      void client.invalidateQueries({ queryKey: ['visits'] })
      void client.invalidateQueries({ queryKey: ['job', visit.jobId] })
      void client.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export const useStartVisit = () => useVisitAction<never>((id) => `/visits/${id}/start`)
export const useCompleteVisit = () =>
  useVisitAction<{ outcome: string; notes?: string; completeJob: boolean }>((id) => `/visits/${id}/complete`)
