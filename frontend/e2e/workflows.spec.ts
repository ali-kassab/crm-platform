import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.customers.push({ id: 'c1', kind: 'COMPANY', fullName: 'مستشفى النيل', phone: '+20223456789', address: { city: 'القاهرة' } })
})

test('work order end to end: create, schedule a visit, start it and close the job', async ({ page, api }) => {
  await page.goto('/jobs')
  await expect(page.getByText('لا توجد أوامر عمل')).toBeVisible()

  await page.getByRole('button', { name: 'أمر عمل جديد' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('العميل').click()
  await page.getByRole('option', { name: 'مستشفى النيل' }).click()
  await dialog.getByLabel('الموقع').click()
  await page.getByRole('option', { name: 'الفرع الرئيسي' }).click()
  await dialog.getByLabel('عنوان أمر العمل').fill('التكييف لا يبرد')
  await dialog.getByLabel('الأولوية').click()
  await page.getByRole('option', { name: 'عاجلة' }).click()
  await dialog.getByRole('button', { name: 'حفظ' }).click()

  await expect(page).toHaveURL(/\/jobs\/j1$/)
  await expect(page.getByRole('heading', { name: 'التكييف لا يبرد' })).toBeVisible()
  const created = api.requests.find((r) => r.method === 'POST' && r.path === '/jobs')
  expect(created?.body).toMatchObject({ contactId: 'c1', locationId: 's1', priority: 'URGENT', type: 'CORRECTIVE_REPAIR' })

  await page.getByRole('button', { name: 'جدولة زيارة' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت جدولة الزيارة')).toBeVisible()
  await expect(page.getByText('مجدولة', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'بدء الزيارة' }).click()
  await expect(page.getByText('بدأت الزيارة')).toBeVisible()
  await page.getByRole('button', { name: 'إنهاء الزيارة' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تم إنهاء الزيارة')).toBeVisible()
  const done = api.requests.find((r) => r.path === '/visits/v1/complete')
  expect(done?.body).toMatchObject({ outcome: 'RESOLVED', completeJob: true })
  await expect(page.getByText('مكتمل').first()).toBeVisible() // job status refreshed
})

test('technician sees the day as cards and can start a visit', async ({ page, api }) => {
  api.jobs.push({ id: 'j9', status: 'SCHEDULED' })
  api.visits.push({
    id: 'v9',
    jobId: 'j9',
    scheduledStart: '2030-01-05T09:00:00Z',
    scheduledEnd: '2030-01-05T11:00:00Z',
    status: 'SCHEDULED',
    outcome: null,
    notes: 'كاميرات المدخل',
    assignees: [],
  })
  await page.goto('/visits')
  await expect(page.getByText('كاميرات المدخل')).toBeVisible()
  await page.getByRole('button', { name: 'بدء الزيارة' }).click()
  await expect(page.getByText('جارية')).toBeVisible()
})

test('finance lists who still owes money', async ({ page, api }) => {
  void api
  await page.goto('/finance')
  await expect(page.getByRole('cell', { name: /مجموعة الفا/ })).toBeVisible()
  await expect(page.getByText('إجمالي المتبقي في الصفحة').filter({ visible: true })).toBeVisible()
})

test('subscription renewal: plan, payment method, receipt, then pending', async ({ page, api }) => {
  await page.goto('/subscription')
  await expect(page.getByText('الاشتراك الحالي')).toBeVisible()
  await page.getByRole('radio', { name: /فريق/ }).click()
  await page.getByLabel('وسيلة الدفع').click()
  await page.getByRole('option', { name: 'فودافون كاش' }).click()
  await expect(page.getByText('01000000000')).toBeVisible()
  await page.getByLabel('رقم العملية أو رقم المحوِّل').fill('01012345678')
  await page.getByLabel('صورة الإيصال').setInputFiles({ name: 'receipt.png', mimeType: 'image/png', buffer: Buffer.from('png') })
  await page.getByRole('button', { name: 'إرسال طلب التفعيل' }).click()
  await expect(page.getByText('طلبك قيد المراجعة')).toBeVisible()
  expect(api.requests.some((r) => r.method === 'POST' && r.path.startsWith('/files?kind=IMAGE&purpose=RECEIPT'))).toBe(true)
  const sent = api.requests.find((r) => r.method === 'POST' && r.path === '/subscription/requests')
  expect(sent?.body).toMatchObject({ planId: 'p2', billingPeriod: 'MONTHLY', paymentMethodId: 'm1', receiptFileId: 'f1' })
})
