import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'

export const JOB_TYPES = ['INSTALLATION', 'PREVENTIVE_MAINTENANCE', 'CORRECTIVE_REPAIR', 'INSPECTION'] as const
export const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const
export const JOB_STATUSES = ['NEW', 'SCHEDULED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'] as const
export type JobStatus = (typeof JOB_STATUSES)[number]

export interface Job {
  id: string
  projectId: string | null
  type: (typeof JOB_TYPES)[number]
  status: JobStatus
  priority: (typeof PRIORITIES)[number]
  title: string
  description: string | null
  reportedIssue: string | null
  customer: { id: string; name: string; phone: string | null; mobile: string | null } | null
  site: { id: string; name: string; city: string | null; addressLine1: string | null; accessInstructions: string | null } | null
  assignees: { userId: string; name: string | null }[]
  startDate: string | null
  budget: string
  sla: { responseDueAt: string; respondedAt: string | null; breached: boolean } | null
  createdAt: string
  version: number
}

export interface Visit {
  id: string
  jobId: string
  scheduledStart: string
  scheduledEnd: string
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'MISSED' | 'RESCHEDULED'
  outcome: string | null
  notes: string | null
  assignees: { userId: string; name: string | null }[]
}

export interface JobFilter {
  q: string
  status: string
  page: number
  slaBreached?: boolean
}

export function useJobs(f: JobFilter) {
  return useQuery({
    queryKey: ['jobs', f],
    queryFn: () => {
      const p = new URLSearchParams({ page: String(f.page), size: '20', sort: 'createdAt,desc' })
      if (f.q.trim()) p.set('q', f.q.trim())
      if (f.status) p.set('status', f.status)
      if (f.slaBreached) p.set('slaBreached', 'true')
      return api<Page<Job>>(`/jobs?${p}`)
    },
    placeholderData: keepPreviousData,
  })
}

export function useJob(id: string) {
  return useQuery({ queryKey: ['job', id], queryFn: () => api<Job>(`/jobs/${id}`) })
}

export function useJobVisits(id: string) {
  return useQuery({ queryKey: ['job', id, 'visits'], queryFn: () => api<Visit[]>(`/jobs/${id}/visits`) })
}

export interface NewJob {
  contactId: string
  locationId: string
  title: string
  type: string
  priority: string
  reportedIssue?: string
  budget?: string
}

export function useCreateJob() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (j: NewJob) => api<Job>('/jobs', { method: 'POST', body: { ...j, budget: j.budget || undefined } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['jobs'] }),
  })
}

export function useChangeJobStatus(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (status: JobStatus) => api<Job>(`/jobs/${id}/status`, { method: 'POST', body: { status } }),
    onSuccess: (job) => {
      client.setQueryData(['job', id], job)
      void client.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export function useScheduleVisit(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (v: { scheduledStart: string; scheduledEnd: string; notes?: string }) =>
      api<Visit>(`/jobs/${jobId}/visits`, { method: 'POST', body: v }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['job', jobId] })
      void client.invalidateQueries({ queryKey: ['visits'] })
    },
  })
}

/** Customer and site pickers for new work orders. */
export function useCustomerOptions() {
  return useQuery({
    queryKey: ['customers', 'options'],
    queryFn: () => api<Page<{ id: string; fullName: string }>>('/contacts?size=100&sort=createdAt,desc'),
  })
}

export function useSiteOptions(contactId: string | undefined) {
  return useQuery({
    queryKey: ['sites', contactId],
    queryFn: () => api<Page<{ id: string; name: string }>>(`/locations?contactId=${contactId}&size=100`),
    enabled: !!contactId,
  })
}
