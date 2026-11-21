import i18n from '@/i18n'

/** RFC 9457 Problem Details as returned by the backend (title/detail already localized). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail?: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(status: number, body: Record<string, unknown> | null) {
    const detail = typeof body?.detail === 'string' ? body.detail : undefined
    const title = typeof body?.title === 'string' ? body.title : undefined
    super(detail ?? title ?? `HTTP ${status}`)
    this.status = status
    this.code = typeof body?.code === 'string' ? body.code : 'HTTP_' + status
    this.detail = detail
    this.fieldErrors = Array.isArray(body?.errors)
      ? (body.errors as { field: string; message: string }[])
      : []
  }
}

let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onSessionLost: () => void = () => {}

export const tokens = {
  set(token: string | null) {
    accessToken = token
  },
  get: () => accessToken,
  onLost(handler: () => void) {
    onSessionLost = handler
  },
}

type Options = Omit<RequestInit, 'body'> & { body?: unknown; retry?: boolean }

const BASE = '/api/v1'

async function send(path: string, { body, retry = true, headers, ...init }: Options = {}): Promise<Response> {
  const h = new Headers(headers)
  if (!h.has('Accept')) h.set('Accept', 'application/json') // file downloads ask for their own type
  h.set('Accept-Language', i18n.language)
  // required by the refresh/logout endpoints (CSRF defence for the HttpOnly cookie)
  h.set('X-Requested-With', 'XMLHttpRequest')
  if (accessToken) h.set('Authorization', `Bearer ${accessToken}`)
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    h.set('Content-Type', 'application/json')
    payload = JSON.stringify(body)
  }
  const response = await fetch(BASE + path, { ...init, headers: h, body: payload, credentials: 'same-origin' })
  if (response.status === 401 && retry && !path.startsWith('/auth/')) {
    if (await refreshSession()) return send(path, { body, retry: false, headers, ...init })
    onSessionLost()
  }
  return response
}

/** One refresh at a time; the refresh token lives only in an HttpOnly cookie. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      // bounded: a hung API (wrong proxy port, stalled backend) must not leave the app on its splash spinner
      const r = await send('/auth/refresh', { method: 'POST', retry: false, signal: AbortSignal.timeout(10_000) })
      if (!r.ok) return false
      const session = (await r.json()) as { accessToken: string }
      accessToken = session.accessToken
      lastSession = session as unknown as AuthSession
      return true
    } catch {
      return false
    } finally {
      queueMicrotask(() => (refreshing = null))
    }
  })()
  return refreshing
}

let lastSession: AuthSession | null = null
export const latestRefreshedSession = () => lastSession

export async function api<T>(path: string, options?: Options): Promise<T> {
  let response: Response
  try {
    response = await send(path, options)
  } catch {
    throw new ApiError(0, { detail: i18n.t('errors.network'), code: 'NETWORK' })
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  const json = text ? (JSON.parse(text) as unknown) : null
  if (!response.ok) throw new ApiError(response.status, json as Record<string, unknown> | null)
  return json as T
}

/**
 * Downloads a file through the API with the current session (the browser can't add the Bearer token
 * to a plain link) and saves it under the server's file name.
 */
export async function download(path: string, accept: string, fallbackName: string): Promise<void> {
  let response: Response
  try {
    response = await send(path, { headers: { Accept: `${accept}, application/problem+json` } })
  } catch {
    throw new ApiError(0, { detail: i18n.t('errors.network'), code: 'NETWORK' })
  }
  if (!response.ok) {
    let problem: Record<string, unknown> | null = null
    try {
      problem = (await response.json()) as Record<string, unknown>
    } catch {
      problem = null
    }
    throw new ApiError(response.status, problem)
  }
  const blob = await response.blob()
  const match = /filename="?([^";]+)"?/i.exec(response.headers.get('Content-Disposition') ?? '')
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = match?.[1] ?? fallbackName
  document.body.append(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

// ------------------------------------------------------------------ shared types

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface AuthSession {
  accessToken: string
  expiresIn: number
  user: { id: string; email: string; firstName: string; lastName: string; preferredLanguage: string | null }
  workspace: { id: string; name: string; defaultLanguage: string; currency: string; sector: string | null }
  role: string
  permissions: string[]
  language: string
}
