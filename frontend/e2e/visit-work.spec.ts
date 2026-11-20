import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.visits.push({
    id: 'v9',
    jobId: 'j9',
    scheduledStart: '2030-01-05T09:00:00Z',
    scheduledEnd: '2030-01-05T11:00:00Z',
    status: 'IN_PROGRESS',
    outcome: null,
    notes: 'كاميرات المدخل',
    assignees: [],
  })
})

test('visit record: write the report, add photos and materials', async ({ page, api }) => {
  await page.goto('/visits')
  await page.getByRole('button', { name: 'سجل الزيارة' }).click()
  const sheet = page.getByRole('dialog', { name: 'سجل الزيارة' })
  await expect(sheet).toBeVisible()

  // report: nothing saved yet (204) → empty form
  await sheet.getByLabel('ما وجده الفني').fill('الكابل مقطوع عند المدخل')
  await sheet.getByLabel('الأعمال المنفذة').fill('تم تغيير الكابل واختبار الكاميرا')
  await sheet.getByLabel('اسم العميل الذي أكّد العمل').fill('محمود حسن')
  await sheet.getByRole('button', { name: 'حفظ التقرير' }).click()
  await expect(page.getByText('تم حفظ التقرير')).toBeVisible()
  const put = api.requests.find((r) => r.method === 'PUT' && r.path === '/visits/v9/report')
  expect(put?.body).toMatchObject({
    findings: 'الكابل مقطوع عند المدخل',
    workPerformed: 'تم تغيير الكابل واختبار الكاميرا',
    customerName: 'محمود حسن',
    diagnosis: null,
  })
  await expect(sheet.getByText(/أكّد العميل في/)).toBeVisible()

  // photos: purpose AFTER, two files
  await sheet.getByRole('tab', { name: 'الصور' }).click()
  await expect(sheet.getByText('لا توجد صور بعد')).toBeVisible()
  await sheet.getByLabel('نوع الصورة').click()
  await page.getByRole('option', { name: 'بعد' }).click()
  const png = Buffer.from('89504e470d0a1a0a', 'hex')
  await sheet.getByLabel('إضافة صور').setInputFiles([
    { name: 'a.png', mimeType: 'image/png', buffer: png },
    { name: 'b.png', mimeType: 'image/png', buffer: png },
  ])
  await expect(page.getByText('تمت إضافة صورتين')).toBeVisible()
  expect(api.requests.some((r) => r.method === 'POST' && r.path === '/files?kind=IMAGE&purpose=AFTER')).toBe(true)
  expect(api.requests.find((r) => r.method === 'POST' && r.path === '/visits/v9/files')?.body).toEqual({ fileIds: ['f1'] })
  await expect(sheet.getByText('بعد', { exact: true }).first()).toBeVisible()

  // materials: validation, add, remove
  await sheet.getByRole('tab', { name: 'المواد' }).click()
  await sheet.getByLabel('الكمية').fill('0')
  await sheet.getByRole('button', { name: 'إضافة مادة' }).click()
  await expect(sheet.getByText('مطلوب')).toBeVisible()
  await expect(sheet.getByText('أدخل كمية أكبر من صفر')).toBeVisible()
  await sheet.getByLabel('المادة أو القطعة').fill('كابل RG59')
  await sheet.getByLabel('الكمية').fill('15')
  await sheet.getByLabel('الوحدة', { exact: true }).fill('متر')
  await sheet.getByLabel('سعر الوحدة').fill('12.5')
  await sheet.getByRole('button', { name: 'إضافة مادة' }).click()
  await expect(sheet.getByText('كابل RG59')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'POST' && r.path === '/jobs/j9/materials')?.body).toMatchObject({
    visitId: 'v9',
    name: 'كابل RG59',
    quantity: '15',
    unit: 'متر',
    unitCost: '12.5',
  })
  await sheet.getByRole('button', { name: 'حذف كابل RG59' }).click()
  await expect(sheet.getByText('لم تُسجّل مواد لهذه الزيارة')).toBeVisible()
})

test('a technician without finance access never sees or sends prices', async ({ page, api }) => {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'test-token',
        expiresIn: 900,
        user: { id: 'u2', email: 'tech@example.com', firstName: 'كريم', lastName: 'سالم', preferredLanguage: null },
        workspace: { id: 'w1', name: 'شركة النور للصيانة', defaultLanguage: 'ar', currency: 'EGP', sector: 'MAINTENANCE' },
        role: 'TECHNICIAN',
        permissions: ['refdata:read', 'ops:assigned', 'files:upload'],
        language: 'ar',
      }),
    }),
  )
  await page.goto('/visits')
  await page.getByRole('button', { name: 'سجل الزيارة' }).click()
  const sheet = page.getByRole('dialog', { name: 'سجل الزيارة' })
  await sheet.getByRole('tab', { name: 'المواد' }).click()
  await expect(sheet.getByLabel('الكمية')).toBeVisible()
  await expect(sheet.getByLabel('سعر الوحدة')).toHaveCount(0)
  await sheet.getByLabel('المادة أو القطعة').fill('بطارية')
  await sheet.getByRole('button', { name: 'إضافة مادة' }).click()
  await expect(sheet.getByText('بطارية')).toBeVisible()
  const post = api.requests.find((r) => r.method === 'POST' && r.path === '/jobs/j9/materials')
  expect(post?.body).not.toHaveProperty('unitCost')
})
