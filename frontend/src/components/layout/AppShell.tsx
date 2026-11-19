import {
  Building2,
  CalendarClock,
  ClipboardList,
  CreditCard,
  Home,
  LogOut,
  Settings,
  Users,
  Wallet,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/auth/auth-context'
import { useRealtime } from '@/lib/realtime'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Separator } from '@/components/ui/separator'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { LanguageSwitcher } from './LanguageSwitcher'
import { SubscriptionBanner } from './SubscriptionBanner'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  permission?: string
  /** Screens arrive in R10; until then the entry is shown disabled. */
  ready?: boolean
}

export function AppShell() {
  const { t, i18n } = useTranslation()
  const { session, logout, can } = useAuth()
  const location = useLocation()
  const side = i18n.dir() === 'rtl' ? 'right' : 'left'
  useRealtime(session?.user.id ?? null, session?.workspace.id ?? null)

  const operations: NavItem[] = [
    { to: '/', label: t('nav.home'), icon: Home, ready: true },
    { to: '/customers', label: t('nav.customers'), icon: Users, permission: 'crm:read', ready: true },
    { to: '/jobs', label: t('nav.jobs'), icon: ClipboardList, ready: true },
    { to: '/visits', label: t('nav.visits'), icon: CalendarClock, ready: true },
    { to: '/maintenance', label: t('nav.maintenance'), icon: Wrench, permission: 'ops:read' },
    { to: '/finance', label: t('nav.finance'), icon: Wallet, permission: 'finance:read', ready: true },
  ]
  const account: NavItem[] = [
    { to: '/subscription', label: t('nav.subscription'), icon: CreditCard, ready: true },
    { to: '/settings', label: t('nav.settings'), icon: Settings, ready: true },
  ]
  const visible = (items: NavItem[]) => items.filter((i) => !i.permission || can(i.permission))
  const initials = `${session?.user.firstName[0] ?? ''}${session?.user.lastName[0] ?? ''}`

  const menu = (items: NavItem[]) => (
    <SidebarMenu>
      {visible(items).map((item) => (
        <SidebarMenuItem key={item.to}>
          {item.ready ? (
            <SidebarMenuButton
              asChild
              tooltip={item.label}
              isActive={item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)}
            >
              <NavLink to={item.to}>
                <item.icon />
                <span>{item.label}</span>
              </NavLink>
            </SidebarMenuButton>
          ) : (
            <SidebarMenuButton tooltip={item.label} disabled>
              <item.icon />
              <span>{item.label}</span>
            </SidebarMenuButton>
          )}
        </SidebarMenuItem>
      ))}
    </SidebarMenu>
  )

  return (
    <SidebarProvider>
      <Sidebar side={side} collapsible="icon">
        <SidebarHeader>
          <div className="flex items-center gap-2 px-2 py-1.5">
            <Building2 className="size-5 shrink-0 text-primary" />
            <div className="min-w-0 group-data-[collapsible=icon]:hidden">
              <p className="truncate text-sm font-semibold">{session?.workspace.name}</p>
              <p className="truncate text-xs text-muted-foreground">{t('app.name')}</p>
            </div>
          </div>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>{t('nav.main')}</SidebarGroupLabel>
            <SidebarGroupContent>{menu(operations)}</SidebarGroupContent>
          </SidebarGroup>
          <SidebarGroup>
            <SidebarGroupLabel>{t('nav.account')}</SidebarGroupLabel>
            <SidebarGroupContent>{menu(account)}</SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton onClick={() => void logout()} tooltip={t('auth.logout')}>
                <LogOut />
                <span>{t('auth.logout')}</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        <header className="flex h-14 items-center gap-2 border-b px-4">
          <SidebarTrigger />
          <Separator orientation="vertical" className="h-5" />
          <div className="flex-1" />
          <LanguageSwitcher />
          <Avatar className="size-8" title={session?.user.email}>
            <AvatarFallback>{initials}</AvatarFallback>
          </Avatar>
        </header>
        <SubscriptionBanner />
        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}
