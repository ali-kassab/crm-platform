import i18n from '@/i18n'
import { ApiError } from '@/lib/api'

/**
 * The platform-admin session is kept apart from the workspace session: its own short-lived token in
 * memory only (no refresh token exists for admins), never the tenant token, and no cookies are sent,
 * so a workspace sign-in in the same browser can't leak into admin calls or the other way round.
 */
let token: string | null = null
let onExpired: () => void = () => {}

export const adminToken = {
  set(value: string | null) {
    token = value
  },
  has: () => token !== null,
  onExpired(handler: () => void) {
    onExpired = handler
  },
}

type Options = { method?: string; body?: unknown }

export async function adminApi<T>(path: string, { method = 'GET', body }: Options = {}): Promise<T> {
  const headers = new Headers({ Accept: 'application/json', 'Accept-Language': i18n.language })
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  let response: Response
  try {
    response = await fetch(`/api/v1/admin${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',
    })
  } catch {
    throw new ApiError(0, { detail: i18n.t('errors.network'), code: 'NETWORK' })
  }
  if (response.status === 401 && path !== '/auth/login') {
    token = null
    onExpired()
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const json = text ? (JSON.parse(text) as unknown) : null
  if (!response.ok) throw new ApiError(response.status, json as Record<string, unknown> | null)
  return json as T
}
