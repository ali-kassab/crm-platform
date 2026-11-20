import { useQueryClient, type QueryKey } from '@tanstack/react-query'
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

type BoundaryProps = {
  children: ReactNode
  fallback: (retry: () => void) => ReactNode
  onRetry: () => void
}

/**
 * React only offers error boundaries as class components; this is the minimal piece
 * (no extra dependency). It keeps a rendering crash inside one screen instead of a blank app.
 */
class Boundary extends Component<BoundaryProps, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // no business data in the message; the stack helps when someone reports it
    console.error('Screen failed to render', error, info.componentStack)
  }

  private retry = () => {
    this.props.onRetry()
    this.setState({ failed: false })
  }

  render() {
    return this.state.failed ? this.props.fallback(this.retry) : this.props.children
  }
}

type Props = {
  children: ReactNode
  /** where "back" leads, e.g. the list the screen was opened from */
  backTo: string
  backLabel: string
  /** queries refetched on retry, so a bad cached payload is not rendered again */
  retryQueryKey?: QueryKey
}

/** Error boundary for one routed screen; it resets when the address changes. */
export function PageErrorBoundary({ children, backTo, backLabel, retryQueryKey }: Props) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const queryClient = useQueryClient()

  return (
    <Boundary
      key={pathname}
      onRetry={() => {
        if (retryQueryKey) void queryClient.resetQueries({ queryKey: retryQueryKey })
      }}
      fallback={(retry) => (
        <Alert variant="destructive" className="max-w-xl">
          <AlertTitle>{t('errors.screenFailed')}</AlertTitle>
          <AlertDescription>
            <p>{t('errors.screenFailedHint')}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" onClick={retry}>
                {t('common.retry')}
              </Button>
              <Button size="sm" variant="outline" asChild>
                <Link to={backTo}>{backLabel}</Link>
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      )}
    >
      {children}
    </Boundary>
  )
}
