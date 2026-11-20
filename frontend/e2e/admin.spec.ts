import type { Page, Route } from '@playwright/test'
import { expect, test } from './fixtures'

/** A small fake of /api/v1/admin/**; registered after the fixture, so it wins for admin paths. */
function fakeAdmin(page: Page) {
  const state = {
    calls: [] as { method: string; path: string; body: unknown; auth: string | undefined; cookie: string | undefined }[],
    expireNext: false,
    plans: [
      { plan: { id: 'p1', code: 'TEAM', nameAr: 'فريق', nameEn: 'Team', descriptionAr: null, descriptionEn: null, priceMonthly: '900.00', priceYearly: '9000.00', currency: 'EGP', limits: { maxUsers: 5, maxActiveProjects: null, maxJobsPerMonth: 200, maxStorageMb: 5120 } }, active: true, sortOrder: 1 },
      { plan: { id: 'p2', code: 'BUSINESS', nameAr: 'أعمال', nameEn: 'Business', descriptionAr: null, descriptionEn: null, priceMonthly: '2500.00', priceYearly: '25000.00', currency: 'EGP', limits: { maxUsers: 25, maxActiveProjects: null, maxJobsPerMonth: null, maxStorageMb: 51200 } }, active: true, sortOrder: 2 },
    ] as Record<string, unknown>[],
    methods: [
      { method: { id: 'm1', kind: 'VODAFONE_CASH', labelAr: 'فودافون كاش', labelEn: 'Vodafone Cash', account: '01000000000', instructionsAr: 'حوّل المبلغ ثم ارفع الإيصال', instructionsEn: null }, active: true, sortOrder: 1 },
    ] as Record<string, unknown>[],
    requests: [
      {
        workspaceId: 'w7',
        requestedBy: 'u7',
        reviewedBy: null,
        request: { id: 'r1', planId: 'p1', billingPeriod: 'MONTHLY', amount: '900.00', currency: 'EGP', paymentMethodId: 'm1', payerReference: 'TX-5521', paidOn: '2030-01-03', receiptFileId: 'f1', note: null, status: 'PENDING', reviewNote: null, reviewedAt: null, createdAt: '2030-01-03T09:00:00Z' },
      },
    ],
    workspaces: [
      { id: 'w7', name: 'ورشة الأمان', slug: 'w7', createdAt: '2029-12-20T08:00:00Z', deletedAt: null, status: 'GRACE', currentPeriodEnd: '2030-01-02T00:00:00Z', planCode: 'TEAM', members: 3, ownerEmail: 'owner@amaan.test' },
    ],
  }
  const subscription = {
    status: 'GRACE', readOnly: false, plan: state.plans[0].plan, billingPeriod: 'MONTHLY', currentPeriodEnd: '2030-01-02T00:00:00Z',
    graceEndsAt: '2030-01-09T00:00:00Z', daysLeft: 0, usage: { users: 3, activeProjects: 0, jobsThisMonth: 12, storageMb: 40 }, pendingRequest: null,
  }
  const json = (route: Route, status: number, body: unknown) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

  return page.route('**/api/v1/admin/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1/admin', '')
    let body: unknown = null
    try {
      body = req.postDataJSON()
    } catch {
      body = null
    }
    const headers = req.headers()
    state.calls.push({ method: req.method(), path: path + url.search, body, auth: headers.authorization, cookie: headers.cookie })
    const b = (body ?? {}) as Record<string, unknown>

    if (path === '/auth/login') {
      return b.password === 'Admin-pass-2030x'
        ? json(route, 200, { accessToken: 'admin-token', expiresIn: 600, user: { id: 'a1', email: 'admin@platform.test', firstName: 'Owner', lastName: 'Admin' } })
        : json(route, 401, { code: 'INVALID_CREDENTIALS', detail: 'البريد الإلكتروني أو كلمة المرور غير صحيحة' })
    }
    if (headers.authorization !== 'Bearer admin-token' || state.expireNext) return json(route, 401, { code: 'UNAUTHENTICATED' })
    if (path === '/plans' && req.method() === 'GET') return json(route, 200, state.plans)
    if (path === '/plans' && req.method() === 'POST') {
      const created = { plan: { id: 'p3', currency: 'EGP', descriptionAr: null, descriptionEn: null, ...b, limits: { maxUsers: b.maxUsers, maxActiveProjects: b.maxActiveProjects, maxJobsPerMonth: b.maxJobsPerMonth, maxStorageMb: b.maxStorageMb } }, active: b.active, sortOrder: b.sortOrder }
      state.plans.push(created)
      return json(route, 201, created)
    }
    if (path === '/payment-methods' && req.method() === 'GET') return json(route, 200, state.methods)
    if (path === '/subscription-requests') return json(route, 200, { content: state.requests.filter((r) => r.request.status === url.searchParams.get('status')), page: 0, size: 20, totalElements: 1, totalPages: 1 })
    if (path === '/subscription-requests/r1/receipt-url') return json(route, 200, { url: 'http://localhost:4173/receipt-r1.jpg' })
    const review = path.match(/^\/subscription-requests\/r1\/(approve|reject)$/)
    if (review) {
      state.requests[0].request.status = review[1] === 'approve' ? 'APPROVED' : 'REJECTED'
      return json(route, 200, state.requests[0])
    }
    if (path === '/workspaces') return json(route, 200, state.workspaces.filter((w) => !url.searchParams.get('status') || w.status === url.searchParams.get('status')))
    if (path === '/workspaces/w7') {
      return json(route, 200, {
        workspaceId: 'w7',
        subscription,
        history: [{ type: 'TRIAL_STARTED', fromStatus: null, toStatus: 'TRIAL', actorKind: 'SYSTEM', actorUserId: null, detail: null, occurredAt: '2029-12-20T08:00:00Z' }],
        requests: [state.requests[0].request],
      })
    }
    if (path === '/workspaces/w7/subscription') return json(route, 200, subscription)
    return json(route, 404, { code: 'NOT_FOUND' })
  }).then(() => state)
}

test('admin signs in on a separate session and reviews a manual payment', async ({ page, api }) => {
  api.signedIn = true // a workspace session in the same browser must not leak into admin calls
  const admin = await fakeAdmin(page)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/admin\/login$/)

  await page.getByLabel('البريد الإلكتروني').fill('admin@platform.test')
  await page.getByLabel('كلمة المرور').fill('wrong-password')
  await page.getByRole('button', { name: 'تسجيل الدخول' }).click()
  await expect(page.getByText('البريد الإلكتروني أو كلمة المرور غير صحيحة')).toBeVisible()
  await page.getByLabel('كلمة المرور').fill('Admin-pass-2030x')
  await page.getByRole('button', { name: 'تسجيل الدخول' }).click()

  await expect(page).toHaveURL(/\/admin\/requests$/)
  await expect(page.getByRole('link', { name: 'ورشة الأمان' })).toBeVisible()
  await expect(page.getByText('TX-5521')).toBeVisible()
  await expect(page.getByText('فودافون كاش')).toBeVisible()
  const authed = admin.calls.filter((c) => c.path !== '/auth/login')
  expect(authed.length).toBeGreaterThan(0)
  expect(authed.every((c) => c.auth === 'Bearer admin-token' && c.cookie === undefined)).toBe(true)

  const popup = page.waitForEvent('popup')
  await page.getByRole('button', { name: 'عرض الإيصال' }).click()
  expect((await popup).url()).toContain('/receipt-r1.jpg')

  // rejecting needs a reason
  await page.getByRole('button', { name: 'رفض' }).click()
  const rejectDialog = page.getByRole('dialog')
  await rejectDialog.getByRole('button', { name: 'رفض' }).click()
  await expect(rejectDialog.getByText('اكتب سبب الرفض')).toBeVisible()
  await rejectDialog.getByRole('button', { name: 'إلغاء' }).click()

  await page.getByRole('button', { name: 'قبول' }).click()
  const approveDialog = page.getByRole('dialog')
  await approveDialog.getByLabel('ملاحظة المراجعة').fill('تم التحقق من التحويل')
  await approveDialog.getByRole('button', { name: 'قبول' }).click()
  await expect(page.getByText('تم قبول الطلب وتفعيل الاشتراك')).toBeVisible()
  expect(admin.calls.find((c) => c.path === '/subscription-requests/r1/approve')?.body).toEqual({ note: 'تم التحقق من التحويل' })
  await expect(page.getByText('لا توجد طلبات')).toBeVisible() // the pending tab is empty now
})

test('admin extends and suspends a workspace, adds a plan, and is sent back to sign-in when the session ends', async ({ page }) => {
  const admin = await fakeAdmin(page)
  await page.goto('/admin/login')
  await page.getByLabel('البريد الإلكتروني').fill('admin@platform.test')
  await page.getByLabel('كلمة المرور').fill('Admin-pass-2030x')
  await page.getByRole('button', { name: 'تسجيل الدخول' }).click()

  await page.getByRole('link', { name: 'مساحات العمل' }).click()
  await expect(page.getByText('owner@amaan.test', { exact: false })).toBeVisible()
  await page.getByRole('link', { name: 'ورشة الأمان' }).click()
  await expect(page.getByRole('heading', { name: 'ورشة الأمان' })).toBeVisible()
  await expect(page.getByText('بدء الفترة التجريبية')).toBeVisible()

  await page.getByRole('button', { name: 'تمديد' }).click()
  const extend = page.getByRole('dialog')
  await extend.getByLabel('عدد الأيام').fill('400')
  await expect(extend.getByText('من 1 إلى 366 يوماً')).toBeVisible()
  await extend.getByLabel('عدد الأيام').fill('45')
  await extend.getByRole('button', { name: 'تمديد' }).click()
  await expect(page.getByText('تم تمديد الاشتراك')).toBeVisible()
  expect(admin.calls.find((c) => c.path === '/workspaces/w7/subscription')?.body).toEqual({ action: 'EXTEND', days: 45 })

  await page.getByRole('button', { name: 'إيقاف' }).click()
  const suspend = page.getByRole('dialog')
  await expect(suspend.getByRole('button', { name: 'إيقاف' })).toBeDisabled() // a reason is required
  await suspend.getByLabel('السبب (مطلوب)').fill('إيصال مزور')
  await suspend.getByRole('button', { name: 'إيقاف' }).click()
  await expect(page.getByText('تم إيقاف الاشتراك')).toBeVisible()
  expect(admin.calls.findLast((c) => c.path === '/workspaces/w7/subscription')?.body).toEqual({ action: 'SUSPEND', note: 'إيصال مزور' })

  await page.getByRole('link', { name: 'الخطط' }).click()
  await page.getByRole('button', { name: 'خطة جديدة' }).click()
  const planDialog = page.getByRole('dialog')
  await planDialog.getByLabel('الرمز').fill('solo_plus')
  await planDialog.getByLabel('الاسم بالعربية').fill('فردي بلس')
  await planDialog.getByLabel('الاسم بالإنجليزية').fill('Solo Plus')
  await planDialog.getByLabel('السعر الشهري').fill('450')
  await planDialog.getByLabel('السعر السنوي').fill('4500')
  await planDialog.getByLabel('المستخدمون').fill('2')
  await planDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تم حفظ الخطة')).toBeVisible()
  expect(admin.calls.find((c) => c.method === 'POST' && c.path === '/plans')?.body).toEqual({
    code: 'SOLO_PLUS', nameAr: 'فردي بلس', nameEn: 'Solo Plus', descriptionAr: null, descriptionEn: null,
    priceMonthly: '450', priceYearly: '4500', maxUsers: 2, maxActiveProjects: null, maxJobsPerMonth: null, maxStorageMb: null, sortOrder: 0, active: true,
  })

  admin.expireNext = true
  await page.getByRole('link', { name: 'وسائل الدفع' }).click()
  await expect(page).toHaveURL(/\/admin\/login$/)
  await expect(page.getByText('انتهت جلسة الإدارة. سجّل الدخول مرة أخرى.')).toBeVisible()
})
