import { expect, test } from '@playwright/test'

// Real browser + nginx + Java API + Postgres + Redis + object storage + realtime service.
test('register a workspace, add a customer, open work orders, live socket connects', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  const sockets: string[] = []
  page.on('websocket', (ws) => sockets.push(ws.url()))

  const stamp = Date.now()
  await page.goto('/register')
  await page.getByLabel('الاسم الأول').fill('سارة')
  await page.getByLabel('اسم العائلة').fill('اختبار')
  await page.getByLabel('اسم الشركة أو النشاط').fill(`ورشة ${stamp}`)
  await page.getByLabel('البريد الإلكتروني').fill(`live-${stamp}@smoke.test`)
  await page.getByLabel('كلمة المرور').fill(`Kz7-${Math.random().toString(36).slice(2, 10)}-Wq`)
  await page.getByRole('button', { name: 'إنشاء حساب' }).click()
  await expect(page.getByText('أهلاً سارة')).toBeVisible()

  await page.goto('/customers')
  await page.getByRole('button', { name: 'إضافة عميل' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('اسم الشركة').fill('مستشفى النيل')
  await dialog.getByLabel('الهاتف').fill('+20223456789')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة العميل')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'مستشفى النيل' })).toBeVisible()

  // search goes through app_search_norm on the real database (أ/ا normalisation)
  await page.getByLabel('بحث').fill('مستشفي')
  await expect(page.getByRole('cell', { name: 'مستشفى النيل' })).toBeVisible()

  await page.goto('/jobs')
  await expect(page.getByText('لا توجد أوامر عمل')).toBeVisible()

  await expect.poll(() => sockets.some((u) => u.includes('/realtime/socket.io/'))).toBe(true)
  expect(sockets.every((u) => !u.includes('ticket'))).toBe(true)
  expect(errors.filter((e) => /Content Security Policy/i.test(e))).toEqual([])
})
