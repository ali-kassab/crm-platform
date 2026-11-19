import { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { AppShell } from '@/components/layout/AppShell'
import { Spinner } from '@/components/ui/spinner'
import { LoginPage } from '@/pages/auth/LoginPage'
import { HomePage } from '@/pages/HomePage'

// screens beyond the first paint load on demand (bundle budget)
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage').then((m) => ({ default: m.RegisterPage })))
const CustomersPage = lazy(() => import('@/pages/customers/CustomersPage').then((m) => ({ default: m.CustomersPage })))
const CustomerDetailPage = lazy(() => import('@/pages/customers/CustomerDetailPage').then((m) => ({ default: m.CustomerDetailPage })))
const SettingsPage = lazy(() => import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })))
const JobsPage = lazy(() => import('@/pages/jobs/JobsPage').then((m) => ({ default: m.JobsPage })))
const JobDetailPage = lazy(() => import('@/pages/jobs/JobDetailPage').then((m) => ({ default: m.JobDetailPage })))
const VisitsPage = lazy(() => import('@/pages/visits/VisitsPage').then((m) => ({ default: m.VisitsPage })))
const FinancePage = lazy(() => import('@/pages/finance/FinancePage').then((m) => ({ default: m.FinancePage })))
const SubscriptionPage = lazy(() => import('@/pages/subscription/SubscriptionPage').then((m) => ({ default: m.SubscriptionPage })))

function FullPageSpinner() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Spinner className="size-6" />
    </div>
  )
}

/** Signed-in area; remembers where the visitor wanted to go. */
function RequireSession() {
  const { session, ready } = useAuth()
  const location = useLocation()
  if (!ready) return <FullPageSpinner />
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

function GuestOnly() {
  const { session, ready } = useAuth()
  if (!ready) return <FullPageSpinner />
  return session ? <Navigate to="/" replace /> : <Outlet />
}

function NotFound() {
  const { t } = useTranslation()
  return <p className="p-6 text-muted-foreground">{t('errors.notFound')}</p>
}

export default function App() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>
      <Route element={<RequireSession />}>
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="customers/:id" element={<CustomerDetailPage />} />
          <Route path="jobs" element={<JobsPage />} />
          <Route path="jobs/:id" element={<JobDetailPage />} />
          <Route path="visits" element={<VisitsPage />} />
          <Route path="finance" element={<FinancePage />} />
          <Route path="subscription" element={<SubscriptionPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
    </Suspense>
  )
}
