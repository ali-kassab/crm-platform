import { createContext, useContext } from 'react'
import type { AuthSession } from '@/lib/api'

export interface RegisterInput {
  email: string
  password: string
  firstName: string
  lastName: string
  workspaceName: string
  sector?: string
}

export interface AuthState {
  session: AuthSession | null
  ready: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => Promise<void>
  can: (permission: string) => boolean
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
