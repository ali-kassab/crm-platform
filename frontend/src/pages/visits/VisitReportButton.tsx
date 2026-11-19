import { FileDown } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { ApiError, download } from '@/lib/api'
import type { Visit } from '@/pages/jobs/api'

/** Downloads the visit's technical report as a PDF in the current UI language (R12). */
export function VisitReportButton({ visit }: { visit: Visit }) {
  const { t, i18n } = useTranslation()
  const [busy, setBusy] = useState(false)

  if (visit.status === 'CANCELLED' || visit.status === 'RESCHEDULED') return null

  const save = async () => {
    setBusy(true)
    try {
      await download(`/reports/visits/${visit.id}/pdf?lang=${i18n.language}`, 'application/pdf', 'visit-report.pdf')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : t('errors.generic'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button size="sm" variant="outline" disabled={busy} onClick={() => void save()}>
      {busy ? <Spinner /> : <FileDown />}
      {t('visits.downloadReport')}
    </Button>
  )
}
