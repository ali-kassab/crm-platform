import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.customers.push({ id: 'c7', kind: 'COMPANY', fullName: 'فندق الصفوة', phone: '+20225550000', address: { city: 'الجيزة' } })
  api.sites.c7 = []
})

test('customer page: add the first site', async ({ page, api }) => {
  await page.goto('/customers')
  await page.getByRole('link', { name: 'فندق الصفوة' }).click()
  await expect(page).toHaveURL(/\/customers\/c7$/)
  await expect(page.getByRole('heading', { name: 'فندق الصفوة' })).toBeVisible()
  await expect(page.getByText('لا توجد مواقع لهذا العميل')).toBeVisible()

  await page.getByRole('button', { name: 'إضافة موقع' }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog.getByText('مطلوب')).toBeVisible() // name required, nothing sent
  expect(api.requests.some((r) => r.method === 'POST' && r.path === '/locations')).toBe(false)

  await dialog.getByLabel('اسم الموقع').fill('المبنى الرئيسي')
  await dialog.getByLabel('المدينة').fill('الجيزة')
  await dialog.getByLabel('تعليمات الوصول').fill('البوابة الخلفية')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة الموقع')).toBeVisible()
  await expect(page.getByText('المبنى الرئيسي')).toBeVisible()
  await expect(page.getByText('تعليمات الوصول: البوابة الخلفية')).toBeVisible()
  const post = api.requests.find((r) => r.method === 'POST' && r.path === '/locations')
  expect(post?.body).toMatchObject({ contactId: 'c7', type: 'COMMERCIAL', name: 'المبنى الرئيسي', address: { city: 'الجيزة' } })
})

test('new work order for a customer without sites: add one inline and it is selected', async ({ page, api }) => {
  await page.goto('/jobs')
  await page.getByRole('button', { name: 'أمر عمل جديد' }).click()
  const jobDialog = page.getByRole('dialog', { name: 'أمر عمل جديد' })
  await jobDialog.getByLabel('العميل').click()
  await page.getByRole('option', { name: 'فندق الصفوة' }).click()
  await expect(jobDialog.getByText('لا توجد مواقع لهذا العميل بعد.')).toBeVisible()

  await jobDialog.getByRole('button', { name: 'إضافة موقع' }).click()
  const siteDialog = page.getByRole('dialog', { name: 'إضافة موقع' })
  await siteDialog.getByLabel('اسم الموقع').fill('الجناح الشرقي')
  await siteDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(siteDialog).toBeHidden()
  // the work-order form was not submitted by the inner form
  expect(api.requests.some((r) => r.method === 'POST' && r.path === '/jobs')).toBe(false)
  await expect(jobDialog.getByLabel('الموقع')).toContainText('الجناح الشرقي')

  await jobDialog.getByLabel('عنوان أمر العمل').fill('كاميرا الاستقبال لا تعمل')
  await jobDialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page).toHaveURL(/\/jobs\/j1$/)
  const created = api.requests.find((r) => r.method === 'POST' && r.path === '/jobs')
  const siteId = (api.sites.c7 ?? [])[0]?.id
  expect(created?.body).toMatchObject({ contactId: 'c7', locationId: siteId })
})
