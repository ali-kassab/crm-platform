import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '@/lib/api'

export interface AppNotification {
  id: string
  type: string
  entityType: string | null
  entityId: string | null
  payload: Record<string, unknown>
  read: boolean
  createdAt: string
}

// every key starts with 'notifications', so the realtime hint (invalidate ['notifications']) refreshes both
export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api<{ unread: number }>('/notifications/unread-count'),
    enabled,
    staleTime: 30_000,
    refetchInterval: 120_000, // fallback when the realtime service is unavailable
  })
}

export function useNotifications(unreadOnly: boolean) {
  return useInfiniteQuery({
    queryKey: ['notifications', 'list', { unreadOnly }],
    queryFn: ({ pageParam }) =>
      api<Page<AppNotification>>(`/notifications?unread=${unreadOnly}&page=${pageParam}&size=20`),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useMarkRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<AppNotification>(`/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useMarkAllRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<{ updated: number }>('/notifications/read-all', { method: 'POST' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

/** Where a notification leads; null when the app has no screen for it yet. */
export function targetOf(n: AppNotification): string | null {
  if (n.entityType === 'JOB' && n.entityId) return `/jobs/${n.entityId}`
  if (n.entityType === 'VISIT' && typeof n.payload.jobId === 'string') return `/jobs/${n.payload.jobId}`
  if (n.entityType === 'SUBSCRIPTION') return '/subscription'
  return null
}
