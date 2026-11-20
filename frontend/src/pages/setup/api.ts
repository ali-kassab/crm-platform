import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'
import type { FirstStep } from './sectors'

export interface WorkspaceSettings {
  id: string
  name: string
  defaultLanguage: string
  locale: string
  currency: string
  timeZone: string
  sector: string | null
}

export type WorkspacePatch = Partial<Pick<WorkspaceSettings, 'name' | 'defaultLanguage' | 'currency' | 'timeZone' | 'sector'>>

export function useWorkspaceSettings() {
  return useQuery({ queryKey: ['workspace'], queryFn: () => api<WorkspaceSettings>('/workspace') })
}

export function useSaveWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (patch: WorkspacePatch) => api<WorkspaceSettings>('/workspace', { method: 'PATCH', body: patch }),
    onSuccess: (saved) => queryClient.setQueryData(['workspace'], saved),
  })
}

// one cheap "is there at least one?" request per step (page size 1, only totalElements is read)
const PROBES: Record<FirstStep, string> = {
  customer: '/contacts?size=1',
  equipment: '/equipment?size=1',
  plan: '/maintenance-plans?size=1',
  job: '/jobs?size=1',
  visit: '/visits?size=1',
}

/** Which first steps already have data; requests run in parallel. */
export function useFirstStepsDone(steps: readonly FirstStep[]) {
  return useQueries({
    queries: steps.map((step) => ({
      queryKey: ['setup', step],
      queryFn: () => api<Page<unknown>>(PROBES[step]).then((p) => p.totalElements > 0),
    })),
  })
}
