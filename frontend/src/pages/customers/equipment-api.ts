import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'

export const EQUIPMENT_CATEGORIES = [
  'CCTV_CAMERA',
  'RECORDER',
  'ACCESS_CONTROL',
  'ALARM',
  'FIRE_SYSTEM',
  'NETWORK',
  'HVAC_UNIT',
  'ELECTRICAL',
  'PLUMBING',
  'OTHER',
] as const
export type EquipmentCategory = (typeof EQUIPMENT_CATEGORIES)[number]

export interface Equipment {
  id: string
  contactId: string
  locationId: string
  category: EquipmentCategory
  name: string
  manufacturer: string | null
  model: string | null
  serialNumber: string | null
  installedOn: string | null
  status: 'ACTIVE' | 'FAULTY' | 'REPLACED' | 'REMOVED'
}

export interface NewEquipment {
  locationId: string
  category: EquipmentCategory
  name: string
  manufacturer?: string
  model?: string
  serialNumber?: string
  installedOn?: string
}

export function useEquipment(contactId: string) {
  return useQuery({
    queryKey: ['equipment', { contactId }],
    queryFn: () => api<Page<Equipment>>(`/equipment?contactId=${contactId}&size=100`),
  })
}

export function useCreateEquipment(contactId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (e: NewEquipment) => api<Equipment>('/equipment', { method: 'POST', body: { ...e, contactId } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['equipment'] }),
  })
}

// ------------------------------------------------------------------ device detail and work-order links

export interface ServiceEntry {
  jobId: string
  title: string
  type: string
  status: string
  startDate: string | null
  completedAt: string | null
}

export interface WarrantyRef {
  id: string
  title: string
  provider: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED'
  endDate: string | null
}

export interface EquipmentDetail extends Equipment {
  notes: string | null
  serviceHistory: ServiceEntry[]
  warranties: WarrantyRef[]
}

export const EQUIPMENT_STATUSES = ['ACTIVE', 'FAULTY', 'REPLACED', 'REMOVED'] as const

// under ['equipment'] so the realtime EQUIPMENT hint refreshes it too
export function useEquipmentDetail(id: string, enabled: boolean) {
  return useQuery({
    queryKey: ['equipment', id],
    queryFn: () => api<EquipmentDetail>(`/equipment/${id}`),
    enabled,
  })
}

export function useUpdateEquipmentStatus(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (status: Equipment['status']) => api<EquipmentDetail>(`/equipment/${id}`, { method: 'PATCH', body: { status } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['equipment'] }),
  })
}

/** Devices at one site: the choices when linking devices to a work order there. */
export function useSiteEquipment(locationId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['equipment', { locationId }],
    queryFn: () => api<Page<Equipment>>(`/equipment?locationId=${locationId}&size=100`),
    enabled: enabled && !!locationId,
  })
}

// under ['job', id] so the realtime JOB hint refreshes it with the rest of the work order
export function useJobEquipment(jobId: string) {
  return useQuery({ queryKey: ['job', jobId, 'equipment'], queryFn: () => api<Equipment[]>(`/jobs/${jobId}/equipment`) })
}

export function useSetJobEquipment(jobId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (equipmentIds: string[]) => api<Equipment[]>(`/jobs/${jobId}/equipment`, { method: 'PUT', body: { equipmentIds } }),
    onSuccess: (list) => {
      client.setQueryData(['job', jobId, 'equipment'], list)
      void client.invalidateQueries({ queryKey: ['equipment'] }) // service histories changed
    },
  })
}
