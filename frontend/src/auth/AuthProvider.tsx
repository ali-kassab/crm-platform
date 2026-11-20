import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import i18n from '@/i18n'
import { api, latestRefreshedSession, refreshSession, tokens, type AuthSession } from '@/lib/api'
import { AuthContext, type AuthState, type RegisterInput } from './auth-context'

/**
 * The access token stays in memory only; the refresh token is an HttpOnly cookie, so a reload
 * restores the session through /auth/refresh without exposing any token to scripts.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [ready, setReady] = useState(false)
  const queryClient = useQueryClient()

  const adopt = useCallback((s: AuthSession | null) => {
    tokens.set(s?.accessToken ?? null)
    setSession(s)
  }, [])

  useEffect(() => {
    tokens.onLost(() => {
      adopt(null)
      queryClient.clear()
    })
    let cancelled = false
    void refreshSession().then((ok) => {
      if (cancelled) return
      if (ok) adopt(latestRefreshedSession())
      setReady(true)
    })
    return () => {
      cancelled = true
    }
  }, [adopt, queryClient])

  const login = useCallback(
    async (email: string, password: string) => {
      adopt(await api<AuthSession>('/auth/login', { method: 'POST', body: { email, password } }))
    },
    [adopt],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      const language = i18n.language === 'en' ? 'en' : 'ar'
      adopt(await api<AuthSession>('/auth/register', { method: 'POST', body: { ...input, language } }))
    },
    [adopt],
  )

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } finally {
      adopt(null)
      queryClient.clear()
    }
  }, [adopt, queryClient])

  const updateWorkspace = useCallback((workspace: Partial<AuthSession['workspace']>) => {
    setSession((s) => (s ? { ...s, workspace: { ...s.workspace, ...workspace } } : s))
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      session,
      ready,
      login,
      register,
      logout,
      updateWorkspace,
      can: (permission) => session?.permissions.includes(permission) ?? false,
    }),
    [session, ready, login, register, logout, updateWorkspace],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
