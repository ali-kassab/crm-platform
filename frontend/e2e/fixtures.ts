import { test as base, type Page, type Route } from '@playwright/test'

/** A minimal fake backend: just enough of /api/v1 for the screens under test. */
export interface FakeApi {
  signedIn: boolean
  readOnly: boolean
  customers: { id: string; kind: 'INDIVIDUAL' | 'COMPANY'; fullName: string; phone: string; address: { city: string } }[]
  requests: { method: string; path: string; body: unknown }[]
  jobs: Record<string, unknown>[]
  visits: Record<string, unknown>[]
  paymentRequests: Record<string, unknown>[]
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
    let body: unknown = null
    try {
      body = request.postDataJSON()
    } catch {
      body = '(binary)' // multipart upload
    }
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
    const handled = await r10(route, api, path, request.method(), body)
    if (handled) return
    return json(route, 404, { code: 'NOT_FOUND' })
  })
}

export const test = base.extend<{ api: FakeApi; language: 'ar' | 'en' }>({
  language: ['ar', { option: true }],
  api: async ({ page, language }, provide) => {
    const api: FakeApi = {
      signedIn: false,
      readOnly: false,
      customers: [],
      requests: [],
      jobs: [],
      visits: [],
      paymentRequests: [],
    }
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

// ------------------------------------------------------------------ R10: work orders, visits, finance, billing

const site = { id: 's1', name: 'الفرع الرئيسي', city: 'القاهرة', addressLine1: 'شارع التسعين', accessInstructions: null }

function newJob(id: string, b: Record<string, unknown>) {
  return {
    id,
    projectId: null,
    type: b.type ?? 'CORRECTIVE_REPAIR',
    status: 'NEW',
    priority: b.priority ?? 'NORMAL',
    title: b.title,
    description: null,
    reportedIssue: b.reportedIssue ?? null,
    customer: { id: b.contactId, name: 'مستشفى النيل', phone: '+20223456789', mobile: null },
    site,
    assignees: [{ userId: 'u1', name: 'أحمد علي' }],
    startDate: null,
    budget: b.budget ?? '0.00',
    sla: null,
    createdAt: '2030-01-01T08:00:00Z',
    version: 0,
  }
}

async function r10(route: Route, api: FakeApi, path: string, method: string, body: unknown): Promise<boolean> {
  const b = (body ?? {}) as Record<string, unknown>
  const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
  let m: RegExpMatchArray | null

  if (path.startsWith('/locations')) {
    await json(route, 200, page([{ id: 's1', name: site.name }]))
    return true
  }
  if (path.startsWith('/jobs') && method === 'GET' && !path.includes('/', 1)) {
    await json(route, 200, page(api.jobs))
    return true
  }
  if (path === '/jobs' && method === 'POST') {
    const job = newJob(`j${api.jobs.length + 1}`, b)
    api.jobs.unshift(job)
    await json(route, 201, job)
    return true
  }
  if ((m = path.match(/^\/jobs\/([^/]+)$/))) {
    const job = api.jobs.find((j) => j.id === m![1])
    await json(route, job ? 200 : 404, job ?? { code: 'NOT_FOUND' })
    return true
  }
  if ((m = path.match(/^\/jobs\/([^/]+)\/status$/))) {
    const job = api.jobs.find((j) => j.id === m![1])!
    job.status = b.status
    await json(route, 200, job)
    return true
  }
  if ((m = path.match(/^\/jobs\/([^/]+)\/visits$/))) {
    if (method === 'POST') {
      const visit = {
        id: `v${api.visits.length + 1}`,
        jobId: m[1],
        scheduledStart: b.scheduledStart,
        scheduledEnd: b.scheduledEnd,
        status: 'SCHEDULED',
        outcome: null,
        notes: b.notes ?? null,
        assignees: [{ userId: 'u1', name: 'أحمد علي' }],
      }
      api.visits.push(visit)
      const job = api.jobs.find((j) => j.id === m![1])
      if (job && job.status === 'NEW') job.status = 'SCHEDULED'
      await json(route, 201, visit)
    } else {
      await json(route, 200, api.visits.filter((v) => v.jobId === m![1]))
    }
    return true
  }
  if (path === '/visits') {
    await json(route, 200, page(api.visits))
    return true
  }
  if ((m = path.match(/^\/visits\/([^/]+)\/(start|complete)$/))) {
    const visit = api.visits.find((v) => v.id === m![1])!
    const job = api.jobs.find((j) => j.id === visit.jobId)
    if (m[2] === 'start') {
      visit.status = 'IN_PROGRESS'
      if (job) job.status = 'IN_PROGRESS'
    } else {
      visit.status = 'COMPLETED'
      visit.outcome = b.outcome
      if (job && b.completeJob) job.status = 'COMPLETED'
    }
    await json(route, 200, visit)
    return true
  }
  if (path.startsWith('/accounting/outstanding')) {
    await json(route, 200, {
      content: [
        { contactId: 'c1', customerName: 'مجموعة الفا', phone: '+20100000001', contract: '11500.00', received: '4500.00', remaining: '7000.00', lastPaymentDate: '2030-03-01' },
        { contactId: 'c2', customerName: 'Gamma Store', phone: null, contract: '800.00', received: '0.00', remaining: '800.00', lastPaymentDate: null },
      ],
      totalElements: 2,
      pageRemaining: '7800.00',
    })
    return true
  }
  if (path === '/subscription/plans') {
    await json(route, 200, [
      { id: 'p1', code: 'SOLO', nameAr: 'فني مستقل', nameEn: 'Solo', descriptionAr: null, descriptionEn: null, priceMonthly: '300.00', priceYearly: '3000.00', limits: { maxUsers: 1, maxJobsPerMonth: 100, maxStorageMb: 2048 } },
      { id: 'p2', code: 'TEAM', nameAr: 'فريق', nameEn: 'Team', descriptionAr: null, descriptionEn: null, priceMonthly: '900.00', priceYearly: '9000.00', limits: { maxUsers: 5, maxJobsPerMonth: 500, maxStorageMb: 10240 } },
    ])
    return true
  }
  if (path === '/subscription/payment-methods') {
    await json(route, 200, [
      { id: 'm1', labelAr: 'فودافون كاش', labelEn: 'Vodafone Cash', account: '01000000000', instructionsAr: 'ارفع صورة الإيصال', instructionsEn: 'Upload the receipt' },
    ])
    return true
  }
  if (path === '/subscription/requests') {
    if (method === 'POST') {
      const r = { id: 'r1', ...b, amount: '900.00', status: 'PENDING', reviewNote: null, createdAt: '2030-01-02T10:00:00Z' }
      api.paymentRequests.unshift(r)
      await json(route, 201, r)
    } else {
      await json(route, 200, api.paymentRequests)
    }
    return true
  }
  if (path.startsWith('/files') && method === 'POST') {
    await json(route, 201, { files: [{ id: 'f1' }] })
    return true
  }
  return false
}
