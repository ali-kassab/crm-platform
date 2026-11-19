import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import type { Job } from './api'

export function StatusBadge({ status }: { status: Job['status'] }) {
  const { t } = useTranslation()
  const variant = status === 'COMPLETED' ? 'default' : status === 'CANCELLED' ? 'outline' : 'secondary'
  return <Badge variant={variant}>{t(`jobs.statuses.${status}`)}</Badge>
}

export function PriorityBadge({ priority }: { priority: Job['priority'] }) {
  const { t } = useTranslation()
  return (
    <Badge variant={priority === 'URGENT' || priority === 'HIGH' ? 'destructive' : 'outline'}>
      {t(`jobs.priorities.${priority}`)}
    </Badge>
  )
}
