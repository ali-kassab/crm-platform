import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'

export type CustomerKind = 'INDIVIDUAL' | 'COMPANY'

export interface Customer {
  id: string
  kind: CustomerKind
  fullName: string
  company: string | null
  email: string | null
  phone: string | null
  mobile: string | null
  address: { city?: string | null } | null
  createdAt: string
}

export interface NewCustomer {
  kind: CustomerKind
  firstName?: string
  lastName?: string
  company?: string
  phone: string
  email?: string
}

export function useCustomers(q: string, page: number) {
  return useQuery({
    queryKey: ['customers', { q, page }],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: '20', sort: 'createdAt,desc' })
      if (q.trim()) params.set('q', q.trim())
      return api<Page<Customer>>(`/contacts?${params}`)
    },
    placeholderData: keepPreviousData,
  })
}

export function useCreateCustomer() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (c: NewCustomer) =>
      api<Customer>('/contacts', {
        method: 'POST',
        body: {
          type: 'CLIENT',
          kind: c.kind,
          name: c.kind === 'INDIVIDUAL' ? { first: c.firstName, last: c.lastName || null } : undefined,
          company: c.kind === 'COMPANY' ? c.company : undefined,
          phone: c.phone,
          email: c.email || undefined,
        },
      }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['customers'] }),
  })
}
