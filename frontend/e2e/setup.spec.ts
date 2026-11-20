import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.workspace.sector = null
})

test('owner sets up a new workspace: sector, settings, then the sector first steps', async ({ page, api }) => {
  api.customers.push({ id: 'c1', kind: 'COMPANY', fullName: 'بنك القاهرة', phone: '+20223456789', address: { city: 'القاهرة' } })
  await page.goto('/')
  await expect(page.getByText('أكمل إعداد مساحة العمل')).toBeVisible()
  await page.getByRole('link', { name: 'ابدأ الإعداد' }).click()
  await expect(page).toHaveURL(/\/setup$/)

  // a sector must be chosen before moving on
  await page.getByRole('button', { name: 'التالي' }).click()
  await expect(page.getByText('اختر مجال العمل', { exact: true })).toBeVisible()
  await page.getByRole('radio', { name: /كاميرات وأنظمة أمن/ }).click()
  await page.getByRole('button', { name: 'التالي' }).click()

  await expect(page.getByLabel('اسم الشركة أو النشاط')).toHaveValue('شركة النور للصيانة')
  await page.getByLabel('العملة').fill('us')
  await page.getByLabel('المنطقة الزمنية').fill('Mars/Base')
  await page.getByRole('button', { name: 'حفظ ومتابعة' }).click()
  await expect(page.getByText('أدخل رمز عملة من ثلاثة أحرف مثل EGP')).toBeVisible()
  await expect(page.getByText('أدخل منطقة زمنية صحيحة مثل Africa/Cairo')).toBeVisible()
  expect(api.requests.some((r) => r.method === 'PATCH')).toBe(false)

  await page.getByLabel('العملة').fill('usd')
  await page.getByLabel('المنطقة الزمنية').fill('Asia/Riyadh')
  await page.getByRole('button', { name: 'حفظ ومتابعة' }).click()
  await expect(page.getByText('أول خطوات في كاميرات وأنظمة أمن')).toBeVisible()
  const patch = api.requests.find((r) => r.method === 'PATCH' && r.path === '/workspace')
  expect(patch?.body).toEqual({
    sector: 'SECURITY_SYSTEMS',
    name: 'شركة النور للصيانة',
    defaultLanguage: 'ar',
    currency: 'USD',
    timeZone: 'Asia/Riyadh',
  })

  // security installers: customer → devices → work order → visit; the customer step is already done
  const steps = page.locator('main ol').last().locator(':scope > li')
  await expect(steps).toHaveCount(4)
  await expect(steps.nth(0)).toContainText('أضف أول عميل')
  await expect(steps.nth(0).getByLabel('تمت')).toBeVisible()
  await expect(steps.nth(1)).toContainText('سجّل أجهزة العميل')
  await expect(steps.nth(1).getByLabel('تمت')).toHaveCount(0)
  await expect(steps.nth(2).getByRole('link', { name: 'أوامر العمل' })).toHaveAttribute('href', '/jobs')

  await page.getByRole('link', { name: 'الذهاب إلى الرئيسية' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('أكمل إعداد مساحة العمل')).toHaveCount(0)

  // the chosen sector suggests the type of a new work order
  await page.goto('/jobs')
  await page.getByRole('button', { name: 'أمر عمل جديد' }).click()
  await expect(page.getByRole('dialog').getByLabel('النوع', { exact: true })).toContainText('تركيب')
})

test('a new owner lands on the setup right after signing up', async ({ page, api }) => {
  api.signedIn = false
  await page.goto('/register')
  await page.getByLabel('الاسم الأول').fill('سارة')
  await page.getByLabel('اسم العائلة').fill('حسن')
  await page.getByLabel('اسم الشركة أو النشاط').fill('ورشة الأمان')
  await page.getByLabel('البريد الإلكتروني').fill('sara@example.com')
  await page.getByLabel('كلمة المرور').fill('Kz7-long-password')
  await page.getByRole('button', { name: 'إنشاء حساب' }).click()
  await expect(page).toHaveURL(/\/setup$/)
  await expect(page.getByRole('heading', { name: 'إعداد مساحة العمل' })).toBeVisible()
  await expect(page.getByRole('radio', { checked: true })).toHaveCount(0)
})

test.describe('in English', () => {
  test.use({ language: 'en' })

  test('settings link to the setup and a maintenance workspace sees its own first steps', async ({ page, api }) => {
    api.workspace.sector = 'MAINTENANCE'
    await page.goto('/settings')
    await expect(page.getByText('Current field: Maintenance & field service')).toBeVisible()
    await page.getByRole('link', { name: 'Change setup' }).click()
    await expect(page.getByRole('radio', { name: /Maintenance/ })).toBeChecked()
    await page.getByRole('button', { name: 'Next' }).click()
    await page.getByRole('button', { name: 'Save and continue' }).click()
    const steps = page.locator('main ol').last().locator(':scope > li')
    await expect(steps).toHaveCount(3)
    await expect(steps.nth(1)).toContainText('Create a preventive maintenance plan')
    await expect(steps.nth(1).getByRole('link', { name: 'Maintenance' })).toHaveAttribute('href', '/maintenance')
    expect(api.requests.find((r) => r.method === 'PATCH')?.body).toMatchObject({ sector: 'MAINTENANCE', currency: 'EGP' })
  })
})
