import { expect, session, test } from './fixtures'

/** R12: dashboard on the home page (reports:read) and the visit report PDF download. */

const dashboard = (period: string, finance: boolean) => ({
  period,
  from: '2026-11-01',
  to: '2026-11-20',
  timeZone: 'Africa/Cairo',
  jobs: { open: 7, openByStatus: { NEW: 3, SCHEDULED: 2, IN_PROGRESS: 2, ON_HOLD: 0 }, slaBreached: 2, created: period === 'WEEK' ? 6 : 11, completed: 5 },
  visits: { today: 3, overdue: 0, completed: 9, missed: 1 },
  maintenance: { overdue: 1, warrantiesExpiringSoon: 4 },
  ...(finance ? { finance: { received: '12500.00', paid: '3000.00', outstanding: '8000.00' } } : {}),
})

test.beforeEach(({ api }) => {
  api.signedIn = true
})

test('owners see the overview, switch periods, and alert counts stand out', async ({ page }) => {
  const periods: string[] = []
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({ json: { ...session, permissions: [...session.permissions, 'reports:read'] } }),
  )
  await page.route('**/api/v1/reports/dashboard**', (route) => {
    const period = new URL(route.request().url()).searchParams.get('period') ?? 'MONTH'
    periods.push(period)
    return route.fulfill({ json: dashboard(period, true) })
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'نظرة عامة' })).toBeVisible()
  await expect(page.getByText('أوامر عمل مفتوحة')).toBeVisible()
  await expect(page.getByText('١١')).toBeVisible() // created this month, Arabic-Indic digits like the rest of the UI
  await expect(page.getByText('مستحق على العملاء')).toBeVisible()
  // counts that need attention are highlighted only when above zero
  await expect(page.getByText('تجاوزت وقت الاستجابة')).toHaveClass(/text-destructive/)
  await expect(page.getByText('زيارات متأخرة')).not.toHaveClass(/text-destructive/)
  expect(periods).toEqual(['MONTH'])

  await page.getByRole('tab', { name: 'آخر 7 أيام' }).click()
  await expect(page.getByText('٦', { exact: true })).toBeVisible()
  expect(periods).toEqual(['MONTH', 'WEEK'])

  // the open-orders tile leads to the list
  await page.getByRole('link', { name: /أوامر عمل مفتوحة/ }).click()
  await expect(page).toHaveURL(/\/jobs$/)
})

test('without reports:read there is no overview and no dashboard request', async ({ page, api }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /أهلاً/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'نظرة عامة' })).toHaveCount(0)
  expect(api.requests.some((r) => r.path.startsWith('/reports/'))).toBe(false)
})

test('a visit report downloads as a PDF in the interface language', async ({ page, api }) => {
  api.visits.push({
    id: 'v7',
    jobId: 'j7',
    scheduledStart: '2030-01-05T09:00:00Z',
    scheduledEnd: '2030-01-05T11:00:00Z',
    status: 'IN_PROGRESS',
    outcome: null,
    notes: 'كاميرات البوابة',
    assignees: [],
  })
  let accept = ''
  await page.route('**/api/v1/reports/visits/*/pdf**', (route) => {
    accept = route.request().headers()['accept'] ?? ''
    return route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      headers: { 'Content-Disposition': 'attachment; filename="visit-report-2030-01-05.pdf"' },
      body: '%PDF-1.7\n%fake\n',
    })
  })

  await page.goto('/visits')
  await expect(page.getByText('كاميرات البوابة')).toBeVisible()
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: 'تحميل التقرير PDF' }).click()
  const file = await downloading
  expect(file.suggestedFilename()).toBe('visit-report-2030-01-05.pdf')
  expect(accept).toContain('application/pdf')
  const req = api.requests.find((r) => r.path.startsWith('/reports/visits/v7/pdf'))
  expect(req).toBeUndefined() // handled by the dedicated route above, not the generic fake API
})

test('a failed report download shows the server message', async ({ page, api }) => {
  api.visits.push({
    id: 'v8',
    jobId: 'j8',
    scheduledStart: '2030-01-05T09:00:00Z',
    scheduledEnd: '2030-01-05T11:00:00Z',
    status: 'IN_PROGRESS',
    outcome: null,
    notes: 'زيارة',
    assignees: [],
  })
  await page.route('**/api/v1/reports/visits/*/pdf**', (route) =>
    route.fulfill({
      status: 429,
      contentType: 'application/problem+json',
      json: { code: 'RATE_LIMITED', detail: 'طلبات كثيرة جدًا. يُرجى المحاولة لاحقًا.' },
    }),
  )
  await page.goto('/visits')
  await page.getByRole('button', { name: 'تحميل التقرير PDF' }).click()
  await expect(page.getByText('طلبات كثيرة جدًا. يُرجى المحاولة لاحقًا.')).toBeVisible()
})
