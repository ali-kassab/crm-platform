import { expect, test } from '@playwright/test'

// Real browser + nginx + Java API + Postgres + Redis + object storage + realtime service.
test('full field flow on the real stack: workspace, customer, site, work order, visit, Arabic PDF, live socket', async ({ page }) => {
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

  // customer page → first site
  await page.getByRole('link', { name: 'مستشفى النيل' }).click()
  await page.getByRole('button', { name: 'إضافة موقع' }).first().click()
  const siteDialog = page.getByRole('dialog')
  await siteDialog.getByLabel('اسم الموقع').fill('المبنى الرئيسي')
  await siteDialog.getByLabel('المدينة').fill('القاهرة')
  await siteDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة الموقع')).toBeVisible()

  // work order → visit → start → complete
  await page.goto('/jobs')
  await expect(page.getByText('لا توجد أوامر عمل')).toBeVisible()
  await page.getByRole('button', { name: 'أمر عمل جديد' }).click()
  const jobDialog = page.getByRole('dialog')
  await jobDialog.getByLabel('العميل').click()
  await page.getByRole('option', { name: 'مستشفى النيل' }).click()
  await jobDialog.getByLabel('الموقع').click()
  await page.getByRole('option', { name: 'المبنى الرئيسي' }).click()
  await jobDialog.getByLabel('عنوان أمر العمل').fill('التكييف لا يبرد')
  await jobDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByRole('heading', { name: 'التكييف لا يبرد' })).toBeVisible()

  await page.getByRole('button', { name: 'جدولة زيارة' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت جدولة الزيارة')).toBeVisible()
  await page.getByRole('button', { name: 'بدء الزيارة' }).click()
  await expect(page.getByText('بدأت الزيارة')).toBeVisible()
  await page.getByRole('button', { name: 'إنهاء الزيارة' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تم إنهاء الزيارة')).toBeVisible()

  // real Arabic PDF from the Java renderer
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'تحميل التقرير PDF' }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/^visit-report-.*\.pdf$/)
  const bytes = await (await file.createReadStream()).toArray()
  expect(Buffer.concat(bytes).subarray(0, 5).toString()).toBe('%PDF-')

  await expect.poll(() => sockets.some((u) => u.includes('/realtime/socket.io/'))).toBe(true)
  expect(sockets.every((u) => !u.includes('ticket'))).toBe(true)
  expect(errors.filter((e) => /Content Security Policy/i.test(e))).toEqual([])
})
