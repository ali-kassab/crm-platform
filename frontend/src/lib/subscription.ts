import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface SubscriptionOverview {
  status: 'TRIAL' | 'ACTIVE' | 'GRACE' | 'EXPIRED' | 'SUSPENDED'
  readOnly: boolean
  plan: { code: string; nameAr: string; nameEn: string }
  billingPeriod: string | null
  currentPeriodEnd: string
  graceEndsAt: string | null
  daysLeft: number
  usage: { users: number; activeProjects: number; jobsThisMonth: number; storageMb: number }
}

export function useSubscription(enabled = true) {
  return useQuery({
    queryKey: ['subscription'],
    queryFn: () => api<SubscriptionOverview>('/subscription'),
    enabled,
    staleTime: 60_000,
  })
}
