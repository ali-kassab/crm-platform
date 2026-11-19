import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface VisitReport {
  reportedProblem: string | null
  findings: string | null
  diagnosis: string | null
  workPerformed: string | null
  recommendations: string | null
  notes: string | null
  customerName: string | null
  customerConfirmedAt: string | null
  customerSignatureFileId: string | null
  version: number
}

export type ReportInput = Omit<VisitReport, 'customerConfirmedAt' | 'version'>

export const PHOTO_PURPOSES = ['BEFORE', 'DURING', 'AFTER', 'INSPECTION'] as const
export type PhotoPurpose = (typeof PHOTO_PURPOSES)[number]

export interface VisitFile {
  id: string
  kind: string
  name: string
  purpose: string | null
  hasThumbnail: boolean
}

export interface Material {
  id: string
  visitId: string | null
  name: string
  quantity: string
  unit: string | null
  unitCost: string | null
  lineCost: string | null
}

export function useVisitReport(visitId: string) {
  return useQuery({
    queryKey: ['visit-report', visitId],
    // 204 (no report yet) → null
    queryFn: async () => (await api<VisitReport | undefined>(`/visits/${visitId}/report`)) ?? null,
  })
}

export function useSaveReport(visitId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (r: ReportInput) => api<VisitReport>(`/visits/${visitId}/report`, { method: 'PUT', body: r }),
    onSuccess: (saved) => client.setQueryData(['visit-report', visitId], saved),
  })
}

export function useVisitFiles(visitId: string) {
  return useQuery({
    queryKey: ['visit-files', visitId],
    queryFn: () => api<VisitFile[]>(`/visits/${visitId}/files`),
  })
}

/** Upload to file storage, then attach to the visit (two calls; the API keeps them separate). */
export function useAddPhotos(visitId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async ({ files, purpose }: { files: File[]; purpose: PhotoPurpose }) => {
      const form = new FormData()
      for (const f of files) form.append('files', f)
      const uploaded = await api<{ files: { id: string }[] }>(`/files?kind=IMAGE&purpose=${purpose}`, {
        method: 'POST',
        body: form,
      })
      return api<VisitFile[]>(`/visits/${visitId}/files`, {
        method: 'POST',
        body: { fileIds: uploaded.files.map((f) => f.id) },
      })
    },
    onSuccess: () => void client.invalidateQueries({ queryKey: ['visit-files', visitId] }),
  })
}

/** Short-lived presigned URL of the 320 px preview (refetched before it expires). */
export function useThumbnail(fileId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['thumbnail', fileId],
    queryFn: () => api<{ url: string }>(`/files/${fileId}/thumbnail-url`),
    enabled,
    staleTime: 4 * 60_000,
  })
}

export function useMaterials(jobId: string) {
  return useQuery({
    queryKey: ['job', jobId, 'materials'],
    queryFn: () => api<{ lines: Material[]; totalCost: string | null }>(`/jobs/${jobId}/materials`),
  })
}

export function useAddMaterial(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (m: { visitId: string; name: string; quantity: string; unit?: string; unitCost?: string }) =>
      api<Material>(`/jobs/${jobId}/materials`, { method: 'POST', body: m }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['job', jobId, 'materials'] }),
  })
}

export function useDeleteMaterial(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/jobs/${jobId}/materials/${id}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['job', jobId, 'materials'] }),
  })
}
