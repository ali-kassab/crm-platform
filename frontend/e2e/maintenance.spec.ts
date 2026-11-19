import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.customers.push({ id: 'c1', kind: 'COMPANY', fullName: 'مستشفى النيل', phone: '+20223456789', address: { city: 'القاهرة' } })
})

test('maintenance: overdue visits, create a quarterly plan, pause it', async ({ page, api, isMobile }) => {
  api.overdue.push({ id: 'o1', planId: 'p0', planTitle: 'صيانة التكييف المركزي', dueDate: '2030-01-01', jobId: 'j1', jobStatus: 'NEW', overdue: true })
  await page.goto('/')
  if (isMobile) await page.getByRole('button', { name: 'Toggle Sidebar' }).click() // the menu is a sheet on phones
  await page.getByRole('link', { name: 'الصيانة الدورية' }).click()
  await expect(page).toHaveURL(/\/maintenance$/)
  await expect(page.getByText('زيارة صيانة متأخرة')).toBeVisible()
  await expect(page.getByRole('link', { name: 'صيانة التكييف المركزي' })).toHaveAttribute('href', '/jobs/j1')
  await expect(page.getByText('لا توجد خطط صيانة')).toBeVisible()

  await page.getByRole('button', { name: 'خطة صيانة جديدة' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('العميل').click()
  await page.getByRole('option', { name: 'مستشفى النيل' }).click()
  await dialog.getByLabel('الموقع').click()
  await page.getByRole('option', { name: 'الفرع الرئيسي' }).click()
  await dialog.getByLabel('اسم الخطة').fill('صيانة كاميرات المستشفى')
  await dialog.getByLabel('تاريخ البداية').fill('2030-02-01')
  await dialog.getByLabel('تاريخ النهاية').fill('2030-01-01')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog.getByText('تاريخ النهاية قبل البداية')).toBeVisible()
  await dialog.getByLabel('تاريخ النهاية').fill('')
  await dialog.getByLabel('زمن الاستجابة للأعطال (ساعات)').fill('24')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة خطة الصيانة')).toBeVisible()
  const post = api.requests.find((r) => r.method === 'POST' && r.path === '/maintenance-plans')
  expect(post?.body).toEqual({
    contactId: 'c1',
    locationId: 's1',
    title: 'صيانة كاميرات المستشفى',
    frequency: 'QUARTERLY',
    startDate: '2030-02-01',
    responseHours: 24,
  })

  const row = page.getByRole('row', { name: /صيانة كاميرات المستشفى/ })
  await expect(row.getByText('كل 3 أشهر')).toBeVisible()
  await row.getByRole('button', { name: 'إيقاف مؤقت' }).click()
  await expect(page.getByText('تم إيقاف الخطة مؤقتاً')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'PATCH')?.body).toEqual({ status: 'PAUSED' })
  await expect(page.getByText('لا توجد خطط صيانة')).toBeVisible() // left the "active" filter
  await page.getByRole('tab', { name: 'موقوفة مؤقتاً' }).click()
  await expect(page.getByRole('row', { name: /صيانة كاميرات المستشفى/ })).toBeVisible()
})

test('customer page: record a camera with its serial number', async ({ page, api }) => {
  await page.goto('/customers/c1')
  await expect(page.getByText('لا توجد أجهزة مسجلة')).toBeVisible()
  await page.getByRole('button', { name: 'إضافة جهاز' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('الموقع')).toContainText('الفرع الرئيسي') // the only site is preselected
  await dialog.getByLabel('اسم الجهاز').fill('كاميرا المدخل الرئيسي')
  await dialog.getByLabel('الشركة المصنعة').fill('Hikvision')
  await dialog.getByLabel('الرقم التسلسلي').fill('DS-2CD-0042')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة الجهاز')).toBeVisible()
  await expect(page.getByText('كاميرا المدخل الرئيسي')).toBeVisible()
  await expect(page.getByText('DS-2CD-0042')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'POST' && r.path === '/equipment')?.body).toEqual({
    contactId: 'c1',
    locationId: 's1',
    category: 'CCTV_CAMERA',
    name: 'كاميرا المدخل الرئيسي',
    manufacturer: 'Hikvision',
    serialNumber: 'DS-2CD-0042',
  })
})
