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
