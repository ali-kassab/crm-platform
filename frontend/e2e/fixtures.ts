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
  notifications: { id: string; type: string; entityType: string | null; entityId: string | null; payload: Record<string, unknown>; read: boolean; createdAt: string }[]
  visitReports: Record<string, Record<string, unknown>>
  visitFiles: Record<string, Record<string, unknown>[]>
  materials: Record<string, Record<string, unknown>[]>
  plans: Record<string, unknown>[]
  overdue: Record<string, unknown>[]
  equipment: Record<string, unknown>[]
  approvals: Record<string, Record<string, unknown>[]>
  /** device ids linked to each work order (PUT /jobs/{id}/equipment) */
  jobEquipment: Record<string, string[]>
  /** workspace settings behind GET/PATCH /workspace; the session's workspace mirrors them */
  workspace: { name: string; defaultLanguage: string; locale: string; currency: string; timeZone: string; sector: string | null }
  /** sites per customer id; a customer missing here has the default site s1 */
  sites: Record<string, { id: string; contactId: string; type: string; name: string; address: { city?: string | null; line1?: string | null } | null; accessInstructions: string | null }[]>
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

function sessionOf(api: FakeApi) {
  const w = api.workspace
  return { ...session, workspace: { ...session.workspace, name: w.name, defaultLanguage: w.defaultLanguage, currency: w.currency, sector: w.sector } }
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
      return api.signedIn ? json(route, 200, sessionOf(api)) : json(route, 401, { code: 'UNAUTHENTICATED' })
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
      return json(route, 200, sessionOf(api))
    }
    if (path === '/auth/register') {
      const b = body as { workspaceName: string; sector?: string }
      Object.assign(api.workspace, { name: b.workspaceName, sector: b.sector ?? null })
      api.signedIn = true
      return json(route, 201, sessionOf(api))
    }
    if (path === '/auth/logout') {
      api.signedIn = false
      return route.fulfill({ status: 204 })
    }
    if (path === '/workspace') {
      if (request.method() === 'PATCH') Object.assign(api.workspace, body)
      return json(route, 200, { id: 'w1', slug: 'w1', ...api.workspace })
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
    const one = path.match(/^\/contacts\/([^/]+)$/)
    if (one && request.method() === 'GET') {
      const c = api.customers.find((x) => x.id === one[1])
      return c ? json(route, 200, { ...c, email: null, mobile: null }) : json(route, 404, { code: 'NOT_FOUND' })
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
    const handled = await r10(route, api, path, request.method(), body, url.searchParams)
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
      sites: {},
      notifications: [],
      visitReports: {},
      visitFiles: {},
      materials: {},
      plans: [],
      overdue: [],
      equipment: [],
      approvals: {},
      jobEquipment: {},
      workspace: { name: session.workspace.name, defaultLanguage: 'ar', locale: 'ar-EG', currency: 'EGP', timeZone: 'Africa/Cairo', sector: session.workspace.sector },
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

async function r10(route: Route, api: FakeApi, path: string, method: string, body: unknown, search: URLSearchParams): Promise<boolean> {
  const b = (body ?? {}) as Record<string, unknown>
  const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
  let m: RegExpMatchArray | null

  if (path === '/notifications/unread-count') {
    await json(route, 200, { unread: api.notifications.filter((n) => !n.read).length })
    return true
  }
  if (path === '/notifications' && method === 'GET') {
    const unread = search.get('unread') === 'true'
    await json(route, 200, page(api.notifications.filter((n) => !unread || !n.read)))
    return true
  }
  if (path === '/notifications/read-all' && method === 'POST') {
    const updated = api.notifications.filter((n) => !n.read).length
    api.notifications.forEach((n) => (n.read = true))
    await json(route, 200, { updated })
    return true
  }
  if ((m = path.match(/^\/notifications\/([^/]+)\/read$/))) {
    const n = api.notifications.find((x) => x.id === m![1])
    if (n) n.read = true
    await json(route, n ? 200 : 404, n ?? { code: 'NOT_FOUND' })
    return true
  }
  if (path === '/locations' && method === 'POST') {
    const contactId = String(b.contactId)
    const address = b.address as { city?: string; line1?: string } | undefined
    const created = { id: `s${Date.now()}`, contactId, type: String(b.type), name: String(b.name), address: address ?? null, accessInstructions: (b.accessInstructions as string) ?? null }
    api.sites[contactId] = [...(api.sites[contactId] ?? []), created]
    await json(route, 201, created)
    return true
  }
  if (path.startsWith('/locations')) {
    const contactId = search.get('contactId') ?? ''
    const fallback = [{ id: 's1', contactId, type: 'COMMERCIAL', name: site.name, address: { city: site.city, line1: site.addressLine1 }, accessInstructions: null }]
    await json(route, 200, page(api.sites[contactId] ?? fallback))
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
  // customer approvals on a work order (R4 API)
  if ((m = path.match(/^\/jobs\/([^/]+)\/approvals(?:\/([^/]+)\/decision)?$/))) {
    const list = (api.approvals[m[1]] ??= [])
    const decided = (d: Record<string, unknown>) => ({ status: d.status, approverName: d.approverName, decidedAt: '2030-01-05T10:00:00Z' })
    if (method === 'POST' && m[2]) {
      const a = list.find((x) => x.id === m![2])
      Object.assign(a ?? {}, decided(b))
      await json(route, 200, a)
    } else if (method === 'POST') {
      const d = b.decision as Record<string, unknown> | undefined
      const a = { id: `a${list.length + 1}`, kind: b.kind, notes: b.notes ?? null, createdAt: '2030-01-05T09:00:00Z', status: 'PENDING', approverName: null, decidedAt: null, ...(d ? decided(d) : {}) }
      list.push(a)
      await json(route, 201, a)
    } else {
      await json(route, 200, list)
    }
    return true
  }
  // maintenance plans and equipment (R6 API)
  if (path === '/maintenance-plans/overdue') {
    await json(route, 200, api.overdue)
    return true
  }
  if (path === '/maintenance-plans' && method === 'POST') {
    const plan = { id: `p${api.plans.length + 1}`, status: 'ACTIVE', nextDueDate: b.startDate, generatedCount: 0, intervalDays: null, ...b }
    api.plans.push(plan)
    await json(route, 201, plan)
    return true
  }
  if (path === '/maintenance-plans') {
    const status = search.get('status')
    await json(route, 200, page(api.plans.filter((p) => !status || p.status === status)))
    return true
  }
  if ((m = path.match(/^\/maintenance-plans\/([^/]+)$/)) && method === 'PATCH') {
    const plan = api.plans.find((p) => p.id === m![1])
    Object.assign(plan ?? {}, b)
    await json(route, plan ? 200 : 404, plan ?? { code: 'NOT_FOUND' })
    return true
  }
  if (path === '/equipment' && method === 'POST') {
    const item = { id: `e${api.equipment.length + 1}`, status: 'ACTIVE', manufacturer: null, model: null, serialNumber: null, installedOn: null, ...b }
    api.equipment.push(item)
    await json(route, 201, item)
    return true
  }
  if (path === '/equipment') {
    const contactId = search.get('contactId')
    const locationId = search.get('locationId')
    await json(route, 200, page(api.equipment.filter((e) => (!contactId || e.contactId === contactId) && (!locationId || e.locationId === locationId))))
    return true
  }
  if ((m = path.match(/^\/equipment\/([^/]+)$/))) {
    const device = api.equipment.find((e) => e.id === m![1])
    if (!device) {
      await json(route, 404, { code: 'NOT_FOUND' })
      return true
    }
    if (method === 'PATCH') Object.assign(device, b)
    const serviceHistory = Object.entries(api.jobEquipment)
      .filter(([, ids]) => ids.includes(device.id as string))
      .map(([jobId]) => api.jobs.find((j) => j.id === jobId))
      .filter(Boolean)
      .map((j) => ({ jobId: j!.id, title: j!.title, type: j!.type, status: j!.status, startDate: null, completedAt: null }))
    await json(route, 200, { notes: null, warranties: [], ...device, serviceHistory })
    return true
  }
  if ((m = path.match(/^\/jobs\/([^/]+)\/equipment$/))) {
    if (method === 'PUT') api.jobEquipment[m[1]] = (b.equipmentIds as string[]) ?? []
    const ids = api.jobEquipment[m[1]] ?? []
    await json(route, 200, api.equipment.filter((e) => ids.includes(e.id as string)))
    return true
  }
  // visit record (R4 API): report, photos, materials
  if ((m = path.match(/^\/visits\/([^/]+)\/report$/))) {
    if (method === 'PUT') {
      api.visitReports[m[1]] = { ...b, customerConfirmedAt: b.customerName ? '2030-01-05T10:00:00Z' : null, version: 1 }
      await json(route, 200, api.visitReports[m[1]])
    } else if (api.visitReports[m[1]]) {
      await json(route, 200, api.visitReports[m[1]])
    } else {
      await route.fulfill({ status: 204 })
    }
    return true
  }
  if ((m = path.match(/^\/visits\/([^/]+)\/files$/))) {
    const list = (api.visitFiles[m[1]] ??= [])
    if (method === 'POST') {
      const ids = (b.fileIds as string[]) ?? []
      const purpose = api.requests.findLast((r) => r.path.startsWith('/files?'))?.path.match(/purpose=(\w+)/)?.[1] ?? null
      ids.forEach((id) => list.push({ id: `${id}-${list.length}`, kind: 'IMAGE', name: 'photo.jpg', purpose, hasThumbnail: false }))
    }
    await json(route, 200, list)
    return true
  }
  if ((m = path.match(/^\/jobs\/([^/]+)\/materials(?:\/([^/]+))?$/))) {
    const list = (api.materials[m[1]] ??= [])
    if (method === 'POST') {
      const qty = String(b.quantity)
      const cost = b.unitCost ? Number(b.unitCost).toFixed(2) : null
      list.push({ id: `m${list.length + 1}`, visitId: b.visitId, name: b.name, quantity: qty, unit: b.unit ?? null, unitCost: cost, lineCost: cost ? (Number(cost) * Number(qty)).toFixed(2) : null })
      await json(route, 201, list[list.length - 1])
    } else if (method === 'DELETE') {
      api.materials[m[1]] = list.filter((x) => x.id !== m![2])
      await route.fulfill({ status: 204 })
    } else {
      await json(route, 200, { lines: list, totalCost: null })
    }
    return true
  }
  return false
}
