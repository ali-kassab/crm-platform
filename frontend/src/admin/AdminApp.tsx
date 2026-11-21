import { useQueryClient } from '@tanstack/react-query'
import { LogOut, ShieldCheck } from 'lucide-react'
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, NavLink, Outlet, Route, Routes, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { AdminUser } from './api'
import { AdminContext, useAdmin } from './admin-context'
import { AdminLoginPage } from './AdminLoginPage'
import { adminApi, adminToken } from './session'

const DashboardPage = lazy(() => import('./DashboardPage').then((m) => ({ default: m.DashboardPage })))
const RequestsPage = lazy(() => import('./RequestsPage').then((m) => ({ default: m.RequestsPage })))
const CompaniesPage = lazy(() => import('./CompaniesPage').then((m) => ({ default: m.CompaniesPage })))
const CompanyDetailPage = lazy(() => import('./CompanyDetailPage').then((m) => ({ default: m.CompanyDetailPage })))
const UsersPage = lazy(() => import('./UsersPage').then((m) => ({ default: m.UsersPage })))
const SubscriptionsPage = lazy(() => import('./SubscriptionsPage').then((m) => ({ default: m.SubscriptionsPage })))
const PlansPage = lazy(() => import('./PlansPage').then((m) => ({ default: m.PlansPage })))
const MethodsPage = lazy(() => import('./MethodsPage').then((m) => ({ default: m.MethodsPage })))
const UsagePage = lazy(() => import('./UsagePage').then((m) => ({ default: m.UsagePage })))
const HealthPage = lazy(() => import('./HealthPage').then((m) => ({ default: m.HealthPage })))
const AuditPage = lazy(() => import('./AuditPage').then((m) => ({ default: m.AuditPage })))
const AccountPage = lazy(() => import('./AccountPage').then((m) => ({ default: m.AccountPage })))

/** Old links (/admin/workspaces/…) keep working. */
function LegacyWorkspace() {
  const { id } = useParams()
  return <Navigate to={id ? `/admin/companies/${id}` : '/admin/companies'} replace />
}

/** Platform-admin area (/admin/*): its own sign-in and session, never the workspace session. */
export default function AdminApp() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [admin, setAdmin] = useState<AdminUser | null>(null)
  const expiry = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const signOut = useCallback(
    (reason?: 'expired') => {
      clearTimeout(expiry.current)
      adminToken.set(null)
      setAdmin(null)
      queryClient.removeQueries({ queryKey: ['admin'] })
      if (reason === 'expired') toast.info(t('admin.sessionEnded'))
      void navigate('/admin/login', { replace: true })
    },
    [navigate, queryClient, t],
  )

  useEffect(() => {
    adminToken.onExpired(() => signOut('expired'))
  }, [signOut])

  useEffect(() => {
    const timer = expiry
    return () => {
      clearTimeout(timer.current)
      adminToken.set(null) // leaving the admin area ends the admin session
    }
  }, [])

  const signIn = useCallback(
    async (email: string, password: string) => {
      const s = await adminApi<{ accessToken: string; expiresIn: number; user: AdminUser }>('/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      adminToken.set(s.accessToken)
      setAdmin(s.user)
      clearTimeout(expiry.current)
      expiry.current = setTimeout(() => signOut('expired'), s.expiresIn * 1000)
    },
    [signOut],
  )

  const value = useMemo(() => ({ admin, signIn, signOut, updateAdmin: setAdmin }), [admin, signIn, signOut])

  return (
    <AdminContext.Provider value={value}>
      <Routes>
        <Route path="login" element={admin ? <Navigate to="/admin/dashboard" replace /> : <AdminLoginPage />} />
        <Route element={admin ? <AdminShell /> : <Navigate to="/admin/login" replace />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="companies" element={<CompaniesPage />} />
          <Route path="companies/:id" element={<CompanyDetailPage />} />
          <Route path="workspaces" element={<LegacyWorkspace />} />
          <Route path="workspaces/:id" element={<LegacyWorkspace />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="subscriptions" element={<SubscriptionsPage />} />
          <Route path="requests" element={<RequestsPage />} />
          <Route path="plans" element={<PlansPage />} />
          <Route path="payment-methods" element={<MethodsPage />} />
          <Route path="usage" element={<UsagePage />} />
          <Route path="health" element={<HealthPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="*" element={<Navigate to="dashboard" replace />} />
        </Route>
      </Routes>
    </AdminContext.Provider>
  )
}

const NAV = [
  ['dashboard', 'admin.nav.dashboard'],
  ['companies', 'admin.nav.companies'],
  ['users', 'admin.nav.users'],
  ['subscriptions', 'admin.nav.subscriptions'],
  ['requests', 'admin.nav.requests'],
  ['plans', 'admin.nav.plans'],
  ['payment-methods', 'admin.nav.methods'],
  ['usage', 'admin.nav.usage'],
  ['health', 'admin.nav.health'],
  ['audit', 'admin.nav.audit'],
  ['account', 'admin.nav.account'],
] as const

function AdminShell() {
  const { t } = useTranslation()
  const { admin, signOut } = useAdmin()
  return (
    <div className="flex min-h-svh flex-col bg-muted/30">
      <header className="border-b bg-background">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-5 text-primary" aria-hidden />
            <span className="font-semibold">{t('admin.title')}</span>
          </div>
          <span className="hidden text-sm text-muted-foreground sm:inline" dir="ltr">
            {admin?.email}
          </span>
          <div className="flex-1" />
          <LanguageSwitcher />
          <Button variant="ghost" size="sm" onClick={() => signOut()}>
            <LogOut />
            {t('auth.logout')}
          </Button>
        </div>
        <nav aria-label={t('admin.title')} className="mx-auto max-w-6xl overflow-x-auto px-4">
          <ul className="flex gap-1">
            {NAV.map(([to, label]) => (
              <li key={to}>
                <NavLink
                  to={`/admin/${to}`}
                  className={({ isActive }) =>
                    cn(
                      'block border-b-2 px-3 py-2 text-sm whitespace-nowrap',
                      isActive ? 'border-primary font-medium' : 'border-transparent text-muted-foreground hover:text-foreground',
                    )
                  }
                >
                  {t(label)}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 p-4">
        <Suspense fallback={<Skeleton className="h-64 w-full" />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
