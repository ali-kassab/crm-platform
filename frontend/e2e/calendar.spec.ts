import { expect, test } from './fixtures'

test.use({ timezoneId: 'Africa/Cairo' })

test.beforeEach(async ({ page, api }) => {
  // Tuesday 8 Jan 2030 → the week runs Saturday 5 → Friday 11 Jan
  await page.clock.setFixedTime(new Date('2030-01-08T08:00:00+02:00'))
  api.signedIn = true
  const visit = (id: string, start: string, status: string, notes: string) => ({
    id,
    jobId: `job-${id}`,
    scheduledStart: start,
    scheduledEnd: new Date(new Date(start).getTime() + 2 * 3600_000).toISOString(),
    status,
    outcome: null,
    notes,
    assignees: [{ userId: 'u2', name: 'كريم سالم' }],
  })
  api.visits.push(
    visit('v1', '2030-01-05T07:00:00Z', 'COMPLETED', 'صيانة كاميرات البنك'),
    visit('v2', '2030-01-08T09:00:00Z', 'SCHEDULED', 'تركيب جهاز تسجيل'),
    visit('v3', '2030-01-08T12:00:00Z', 'CANCELLED', 'زيارة ملغاة'),
  )
})

test('week calendar groups visits by day from Saturday and navigates weeks', async ({ page, api }) => {
  await page.goto('/visits')
  await page.getByRole('link', { name: 'تقويم الأسبوع' }).click()
  await expect(page).toHaveURL(/\/visits\/calendar$/)

  const days = page.locator('main ol').first().locator(':scope > li')
  await expect(days).toHaveCount(7)
  await expect(days.first().getByRole('heading')).toContainText('السبت')
  await expect(days.nth(0)).toContainText('صيانة كاميرات البنك')
  await expect(days.nth(3)).toContainText('تركيب جهاز تسجيل') // Tuesday
  await expect(days.nth(3)).toContainText('كريم سالم')
  await expect(page.getByText('زيارة ملغاة')).toHaveCount(0) // cancelled visits are left out
  await expect(page.getByRole('link', { name: /تركيب جهاز تسجيل/ })).toHaveAttribute('href', '/jobs/job-v2')

  const first = api.requests.find((r) => r.path.startsWith('/visits?from='))
  expect(decodeURIComponent(first?.path ?? '')).toContain('from=2030-01-04T22:00:00.000Z') // Sat 00:00 Cairo
  await page.getByRole('button', { name: 'الأسبوع التالي' }).click()
  await expect.poll(() => api.requests.some((r) => decodeURIComponent(r.path).includes('from=2030-01-11T22:00:00.000Z'))).toBe(true)
})
