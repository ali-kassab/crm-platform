import { expect, test } from '@playwright/test'

// Needs a platform admin on the stack (PLATFORM_ADMIN_EMAIL / PLATFORM_ADMIN_PASSWORD at its first start):
//   LIVE_ADMIN_EMAIL=... LIVE_ADMIN_PASSWORD=... LIVE_BASE_URL=http://localhost:18088 pnpm test:live
const email = process.env.LIVE_ADMIN_EMAIL
const password = process.env.LIVE_ADMIN_PASSWORD

test('platform admin on the real stack: sign in, open a workspace, extend it, see plans and payment methods', async ({ page }) => {
  test.skip(!email || !password, 'LIVE_ADMIN_EMAIL / LIVE_ADMIN_PASSWORD not set')
  await page.goto('/admin')
  await page.getByLabel('البريد الإلكتروني').fill(email!)
  await page.getByLabel('كلمة المرور').fill(password!)
  await page.getByRole('button', { name: 'تسجيل الدخول' }).click()
  await expect(page).toHaveURL(/\/admin\/requests$/)
  await expect(page.getByRole('heading', { name: 'طلبات الدفع' })).toBeVisible()

  // the newest workspace (registered by the smoke test) is first
  await page.getByRole('link', { name: 'مساحات العمل' }).click()
  const first = page.locator('main ul > li').first()
  await expect(first).toBeVisible()
  const name = (await first.getByRole('link').textContent()) ?? ''
  await first.getByRole('link').click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  await expect(page.getByText('بدء الفترة التجريبية')).toBeVisible()

  await page.getByRole('button', { name: 'تمديد' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('عدد الأيام').fill('7')
  await dialog.getByRole('button', { name: 'تمديد' }).click()
  await expect(page.getByText('تم تمديد الاشتراك')).toBeVisible()
  await expect(page.getByText('تمديد', { exact: true }).last()).toBeVisible() // new history entry

  await page.getByRole('link', { name: 'الخطط' }).click()
  await expect(page.locator('main ul > li')).not.toHaveCount(0)
  await page.getByRole('link', { name: 'وسائل الدفع' }).click()
  await expect(page.getByRole('heading', { name: 'وسائل الدفع' })).toBeVisible()

  await page.getByRole('button', { name: 'تسجيل الخروج' }).click()
  await expect(page).toHaveURL(/\/admin\/login$/)
})
