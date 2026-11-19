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

  // equipment at that site (serial numbers are unique per workspace)
  await page.getByRole('button', { name: 'إضافة جهاز' }).click()
  const eqDialog = page.getByRole('dialog')
  await eqDialog.getByLabel('اسم الجهاز').fill('كاميرا المدخل')
  await eqDialog.getByLabel('الرقم التسلسلي').fill(`SN-${stamp}`)
  await eqDialog.getByLabel('تاريخ التركيب').fill('2026-01-15')
  await eqDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة الجهاز')).toBeVisible()
  await expect(page.getByText(`SN-${stamp}`)).toBeVisible()

  // preventive maintenance plan for the site
  await page.goto('/maintenance')
  await page.getByRole('button', { name: 'خطة صيانة جديدة' }).click()
  const planDialog = page.getByRole('dialog')
  await planDialog.getByLabel('العميل').click()
  await page.getByRole('option', { name: 'مستشفى النيل' }).click()
  await planDialog.getByLabel('الموقع').click()
  await page.getByRole('option', { name: 'المبنى الرئيسي' }).click()
  await planDialog.getByLabel('اسم الخطة').fill('صيانة ربع سنوية')
  await planDialog.getByLabel('زمن الاستجابة للأعطال (ساعات)').fill('24')
  await planDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة خطة الصيانة')).toBeVisible()
  const planRow = page.getByRole('row', { name: /صيانة ربع سنوية/ })
  await expect(planRow.getByText('كل 3 أشهر')).toBeVisible()
  await planRow.getByRole('button', { name: 'إيقاف مؤقت' }).click()
  await expect(page.getByText('تم إيقاف الخطة مؤقتاً')).toBeVisible()

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

  // visit record: report, a real photo (server thumbnail via presigned URL), a material line
  await page.getByRole('button', { name: 'سجل الزيارة' }).click()
  const sheet = page.getByRole('dialog', { name: 'سجل الزيارة' })
  await sheet.getByLabel('ما وجده الفني').fill('ضاغط التكييف لا يعمل')
  await sheet.getByLabel('الأعمال المنفذة').fill('تم تغيير المكثف وشحن الفريون')
  await sheet.getByLabel('اسم العميل الذي أكّد العمل').fill('محمود حسن')
  await sheet.getByRole('button', { name: 'حفظ التقرير' }).click()
  await expect(page.getByText('تم حفظ التقرير')).toBeVisible()
  await sheet.getByRole('tab', { name: 'الصور' }).click()
  // valid 16x16 RGB PNG (the server decodes every image and rejects unreadable ones with 415)
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAABlElEQVR4nBWRURFEMQjEkFAJSKgEJFQCEiohEiqhEpCAhEpAAhLuHt87THYjIgxBhSmYsAQXtoBwhCuEkMITSmhBZDAGOpgDG6yBD/aAwRncQQxy8AY16IGIMhRVpmLKUlzZCspRrhJKKk8ppRWRyZjoZE5ssiY+2RMmZ3InMcnJm9SkJyLGMNSYhhnLcGMbGMe4RhhpPKOMNkQWY6GLubDFWvhiL1icxV3EIhdvUYteiDjDUWc65izHne3gHOc64aTznHLaEdmMjW7mxjZr45u9YXM2dxOb3LxNbXoj8g2IfiWxDwT/wt8duBCQ8KCg/3rkMA56mAc7rIMf9vni53APccjDO9Shz9/DZVz0Mi92WRe/7Ps9P5d7iUte3qUuff8eghFoMAMLVuDBjg/lBDeIIIMXVNDx95CMRJOZWLIST3Z+4Ce5SSSZvKSSzr+Hx3joYz7ssR7+2O+reR73EY98vEc9+v09FKPQYhZWrMKLXd8op7hFFFm8ooquv4dmNNrMxprVeLP7m/A0t4kmm9dU080PiAJwEDqK72wAAAAASUVORK5CYII=',
    'base64',
  )
  await sheet.getByLabel('إضافة صور').setInputFiles({ name: 'after.png', mimeType: 'image/png', buffer: png })
  await expect(page.getByText('تمت إضافة صورة')).toBeVisible()
  const thumb = sheet.getByRole('img', { name: 'after.webp' }) // stored re-encoded as WebP
  await expect(thumb).toBeVisible()
  await expect.poll(() => thumb.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0)
  await sheet.getByRole('tab', { name: 'المواد' }).click()
  await sheet.getByLabel('المادة أو القطعة').fill('مكثف 35 ميكرو')
  await sheet.getByLabel('الوحدة', { exact: true }).fill('قطعة')
  await sheet.getByRole('button', { name: 'إضافة مادة' }).click()
  await expect(sheet.getByText('مكثف 35 ميكرو')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(sheet).toBeHidden()
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
