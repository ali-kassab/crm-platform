import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
})

test('empty state, add a company customer, then search', async ({ page, api }) => {
  await page.goto('/customers')
  await expect(page.getByText('لا يوجد عملاء بعد')).toBeVisible()

  await page.getByRole('button', { name: 'إضافة عميل' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog.getByText('مطلوب').first()).toBeVisible() // client-side validation, nothing sent
  expect(api.requests.some((r) => r.method === 'POST' && r.path === '/contacts')).toBe(false)

  await dialog.getByLabel('اسم الشركة').fill('مستشفى النيل')
  await dialog.getByLabel('الهاتف').fill('+20223456789')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تمت إضافة العميل')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'مستشفى النيل' })).toBeVisible()
  const post = api.requests.find((r) => r.method === 'POST' && r.path === '/contacts')
  expect(post?.body).toMatchObject({ type: 'CLIENT', kind: 'COMPANY', company: 'مستشفى النيل', phone: '+20223456789' })

  await page.getByLabel('بحث').fill('النيل')
  await expect.poll(() => api.requests.some((r) => r.path.includes('q=%D8%A7%D9%84%D9%86%D9%8A%D9%84'))).toBe(true)
})

test('an expired subscription shows the read-only banner and disables adding', async ({ page, api }) => {
  api.readOnly = true
  api.customers.push({ id: 'c1', kind: 'COMPANY', fullName: 'شركة الأمل', phone: '+20100000000', address: { city: 'الجيزة' } })
  await page.goto('/customers')
  await expect(page.getByText('الحساب للقراءة فقط')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'شركة الأمل' })).toBeVisible() // data stays readable
  await expect(page.getByRole('button', { name: 'إضافة عميل' })).toBeDisabled()
})
