import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.jobs.push({
    id: 'j5',
    projectId: null,
    type: 'INSTALLATION',
    status: 'NEW',
    priority: 'NORMAL',
    title: 'تركيب كاميرات المخزن',
    reportedIssue: null,
    customer: { id: 'c1', name: 'مستشفى النيل', phone: '+20223456789', mobile: null },
    site: { id: 's1', name: 'الفرع الرئيسي', city: 'القاهرة', addressLine1: null, accessInstructions: null },
    assignees: [],
    budget: '0.00',
    sla: null,
    createdAt: '2030-01-01T08:00:00Z',
    version: 0,
  })
  api.notifications.push(
    {
      id: 'n1',
      type: 'JOB_ASSIGNED',
      entityType: 'JOB',
      entityId: 'j5',
      payload: { jobTitle: 'تركيب كاميرات المخزن' },
      read: false,
      createdAt: '2030-01-05T08:00:00Z',
    },
    {
      id: 'n2',
      type: 'SUBSCRIPTION_REQUEST_REJECTED',
      entityType: 'SUBSCRIPTION',
      entityId: 'sub1',
      payload: { reason: 'الإيصال غير واضح' },
      read: false,
      createdAt: '2030-01-04T08:00:00Z',
    },
    {
      id: 'n3',
      type: 'SUBSCRIPTION_ACTIVATED',
      entityType: 'SUBSCRIPTION',
      entityId: 'sub1',
      payload: {},
      read: true,
      createdAt: '2030-01-03T08:00:00Z',
    },
  )
})

test('bell shows the unread count; opening a notification marks it read and goes to the work order', async ({ page, api }) => {
  await page.goto('/')
  const bell = page.getByRole('link', { name: 'الإشعارات: إشعاران غير مقروءين' })
  await expect(bell).toBeVisible()
  await bell.click()
  await expect(page).toHaveURL(/\/notifications$/)
  await expect(page.getByText('رُفض طلب التجديد: الإيصال غير واضح')).toBeVisible()
  await expect(page.getByText('تم تفعيل اشتراكك')).toBeVisible()

  await page.getByRole('tab', { name: 'غير المقروءة' }).click()
  await expect(page.getByText('تم تفعيل اشتراكك')).toBeHidden()

  await page.getByRole('button', { name: /أُسند إليك أمر العمل: تركيب كاميرات المخزن/ }).click()
  await expect(page).toHaveURL(/\/jobs\/j5$/)
  expect(api.requests.some((r) => r.method === 'POST' && r.path === '/notifications/n1/read')).toBe(true)
  await expect(page.getByRole('link', { name: 'الإشعارات: إشعار واحد غير مقروء' })).toBeVisible()
})

test('mark all as read clears the badge', async ({ page, api }) => {
  await page.goto('/notifications')
  await page.getByRole('button', { name: 'تعليم الكل كمقروء' }).click()
  await expect(page.getByRole('link', { name: 'الإشعارات', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'تعليم الكل كمقروء' })).toBeDisabled()
  expect(api.notifications.every((n) => n.read)).toBe(true)
})

test.describe('english', () => {
  test.use({ language: 'en' })
  test('texts are built in English from the same payload', async ({ page }) => {
    await page.goto('/notifications')
    await expect(page.getByText('Renewal rejected: الإيصال غير واضح')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Notifications: 2 unread' })).toBeVisible()
  })
})
