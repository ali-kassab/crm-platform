import { test as base, type Page, type Route } from '@playwright/test'

/** A minimal fake backend: just enough of /api/v1 for the screens under test. */
export interface FakeApi {
  signedIn: boolean
  readOnly: boolean
  customers: { id: string; kind: 'INDIVIDUAL' | 'COMPANY'; fullName: string; phone: string; address: { city: string } }[]
  requests: { method: string; path: string; body: unknown }[]
}

export const session = {
  accessToken: 'test-token',
  expiresIn: 900,
  user: { id: 'u1', email: 'ahmed@example.com', firstName: 'أحمد', lastName: 'علي', preferredLanguage: null },
  workspace: { id: 'w1', name: 'شركة النور للصيانة', defaultLanguage: 'ar', currency: 'EGP', sector: 'MAINTENANCE' },
  role: 'OWNER',
  permissions: ['crm:read', 'crm:write', 'ops:read', 'ops:write', 'finance:read', 'workspace:manage'],
  language: 'ar',
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function install(page: Page, api: FakeApi) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const body = request.postDataJSON?.() ?? null
    api.requests.push({ method: request.method(), path: path + url.search, body })
    const arabic = (request.headers()['accept-language'] ?? 'ar').startsWith('ar')

    if (path === '/auth/refresh') {
      return api.signedIn ? json(route, 200, session) : json(route, 401, { code: 'UNAUTHENTICATED' })
    }
    if (path === '/auth/login') {
      const b = body as { email: string; password: string }
      if (b.password !== 'correct-horse') {
        return json(route, 401, {
          code: 'INVALID_CREDENTIALS',
          detail: arabic ? 'البريد الإلكتروني أو كلمة المرور غير صحيحة' : 'Invalid email or password',
        })
      }
      api.signedIn = true
      return json(route, 200, session)
    }
    if (path === '/auth/logout') {
      api.signedIn = false
      return route.fulfill({ status: 204 })
    }
    if (path === '/subscription') {
      return json(route, 200, {
        status: api.readOnly ? 'EXPIRED' : 'TRIAL',
        readOnly: api.readOnly,
        plan: { code: 'TEAM', nameAr: 'فريق', nameEn: 'Team' },
        billingPeriod: null,
        currentPeriodEnd: '2030-01-15T00:00:00Z',
        graceEndsAt: null,
        daysLeft: api.readOnly ? 0 : 12,
        usage: { users: 1, activeProjects: 0, jobsThisMonth: 0, storageMb: 0 },
      })
    }
    if (path === '/contacts' && request.method() === 'GET') {
      const q = url.searchParams.get('q') ?? ''
      const content = api.customers.filter((c) => c.fullName.includes(q) || c.phone.includes(q))
      return json(route, 200, { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
    }
    if (path === '/contacts' && request.method() === 'POST') {
      const b = body as { kind: 'COMPANY' | 'INDIVIDUAL'; company?: string; name?: { first: string }; phone: string }
      const created = {
        id: `c${api.customers.length + 1}`,
        kind: b.kind,
        fullName: b.company ?? b.name?.first ?? '',
        phone: b.phone,
        address: { city: 'القاهرة' },
      }
      api.customers.unshift(created)
      return json(route, 201, created)
    }
    return json(route, 404, { code: 'NOT_FOUND' })
  })
}

export const test = base.extend<{ api: FakeApi; language: 'ar' | 'en' }>({
  language: ['ar', { option: true }],
  api: async ({ page, language }, provide) => {
    const api: FakeApi = { signedIn: false, readOnly: false, customers: [], requests: [] }
    // seed the language once per test; later reloads keep whatever the user picked
    await page.addInitScript((lang) => {
      if (!sessionStorage.getItem('seeded')) {
        localStorage.setItem('ui-language', lang)
        sessionStorage.setItem('seeded', '1')
      }
    }, language)
    await install(page, api)
    await provide(api)
  },
})

export { expect } from '@playwright/test'
