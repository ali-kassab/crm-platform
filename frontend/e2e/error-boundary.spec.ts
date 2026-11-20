import { expect, test } from './fixtures'

// a payload the screen cannot render (no assignees list) must stay inside the work-order screen
const brokenJob = {
  id: 'j9',
  projectId: null,
  type: 'CORRECTIVE_REPAIR',
  status: 'NEW',
  priority: 'NORMAL',
  title: 'إنذار الحريق لا يعمل',
  reportedIssue: null,
  customer: { id: 'c1', name: 'شركة النيل', phone: '+20223456789', mobile: null },
  site: { id: 's1', name: 'المخزن', city: 'الجيزة', addressLine1: null, accessInstructions: null },
  budget: '0.00',
  sla: null,
  createdAt: '2030-01-01T08:00:00Z',
  version: 0,
}

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.jobs.push({ ...brokenJob })
})

test('a work order that fails to render shows a contained error, then recovers on retry', async ({ page, api }) => {
  await page.goto('/jobs/j9')
  const alert = page.getByRole('alert').filter({ hasText: 'تعذّر عرض هذه الصفحة' })
  await expect(alert).toBeVisible()
  // the rest of the app keeps working: the shell is still there and the way back is offered
  await expect(page.getByRole('link', { name: 'أوامر العمل' }).first()).toBeVisible()
  await expect(alert.getByRole('link', { name: 'أوامر العمل' })).toHaveAttribute('href', '/jobs')

  // the server now sends a complete work order: retry refetches instead of re-rendering the cached one
  api.jobs.find((j) => j.id === 'j9')!.assignees = []
  const refetch = page.waitForRequest((r) => r.url().endsWith('/api/v1/jobs/j9'))
  await alert.getByRole('button', { name: 'إعادة المحاولة' }).click()
  await refetch
  await expect(page.getByRole('heading', { name: 'إنذار الحريق لا يعمل' })).toBeVisible()
  await expect(page.getByText('تعذّر عرض هذه الصفحة')).toHaveCount(0)
})

test('leaving the failed screen clears the error', async ({ page }) => {
  await page.goto('/jobs/j9')
  const alert = page.getByRole('alert').filter({ hasText: 'تعذّر عرض هذه الصفحة' })
  await expect(alert).toBeVisible()
  await alert.getByRole('link', { name: 'أوامر العمل' }).click()
  await expect(page).toHaveURL(/\/jobs$/)
  await expect(page.getByText('تعذّر عرض هذه الصفحة')).toHaveCount(0)
})
