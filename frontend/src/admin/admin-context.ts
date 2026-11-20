import { createContext, useContext } from 'react'
import type { AdminUser } from './api'

export interface AdminState {
  admin: AdminUser | null
  signIn: (email: string, password: string) => Promise<void>
  signOut: (reason?: 'expired') => void
  updateAdmin: (user: AdminUser) => void
}

export const AdminContext = createContext<AdminState | null>(null)

export function useAdmin(): AdminState {
  const ctx = useContext(AdminContext)
  if (!ctx) throw new Error('useAdmin must be used inside the admin area')
  return ctx
}
