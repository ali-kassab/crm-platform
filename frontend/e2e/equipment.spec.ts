import { expect, test } from './fixtures'

const device = (id: string, name: string, extra: Record<string, unknown> = {}) => ({
  id,
  contactId: 'c1',
  locationId: 's1',
  category: 'CCTV_CAMERA',
  name,
  manufacturer: 'Hikvision',
  model: 'DS-2CD',
  serialNumber: `SN-${id}`,
  installedOn: '2029-12-01',
  status: 'ACTIVE',
  ...extra,
})

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.customers.push({ id: 'c1', kind: 'COMPANY', fullName: 'بنك القاهرة', phone: '+20223456789', address: { city: 'القاهرة' } })
  api.equipment.push(
    device('e1', 'كاميرا المدخل'),
    device('e2', 'كاميرا الخزينة'),
    device('e3', 'كاميرا قديمة', { status: 'REMOVED' }),
    device('e4', 'كاميرا فرع آخر', { locationId: 's9' }),
  )
  api.jobs.push({
    id: 'j7',
    projectId: null,
    type: 'CORRECTIVE_REPAIR',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    title: 'الكاميرا لا تسجل',
    reportedIssue: null,
    customer: { id: 'c1', name: 'بنك القاهرة', phone: '+20223456789', mobile: null },
    site: { id: 's1', name: 'الفرع الرئيسي', city: 'القاهرة', addressLine1: null, accessInstructions: null },
    assignees: [],
    budget: '0.00',
    sla: null,
    createdAt: '2030-01-01T08:00:00Z',
    version: 0,
  })
})

test('link devices to a work order, then see it in the device service history and mark the device faulty', async ({ page, api }) => {
  await page.goto('/jobs/j7')
  await expect(page.getByText('لم تُحدد أجهزة لهذا الأمر')).toBeVisible()
  await page.getByRole('button', { name: 'تحديد الأجهزة' }).click()
  const dialog = page.getByRole('dialog')
  // only devices at the work order's site, and not removed ones
  await expect(dialog.getByRole('checkbox')).toHaveCount(2)
  await expect(dialog.getByText('كاميرا فرع آخر')).toHaveCount(0)
  await expect(dialog.getByText('كاميرا قديمة')).toHaveCount(0)
  await dialog.getByLabel(/كاميرا المدخل/).check()
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog).toBeHidden()
  expect(api.requests.find((r) => r.method === 'PUT')?.body).toEqual({ equipmentIds: ['e1'] })
  await expect(page.getByText('كاميرا المدخل')).toBeVisible()
  await expect(page.getByText('SN-e1')).toBeVisible()

  await page.goto('/customers/c1')
  await page.getByRole('button', { name: 'كاميرا المدخل' }).click()
  const sheet = page.getByRole('dialog')
  await expect(sheet.getByRole('heading', { name: 'كاميرا المدخل' })).toBeVisible()
  await expect(sheet.getByText('Hikvision')).toBeVisible()
  await expect(sheet.getByRole('link', { name: 'الكاميرا لا تسجل' })).toHaveAttribute('href', '/jobs/j7')
  await expect(sheet.getByText('لا توجد ضمانات مسجلة لهذا الجهاز')).toBeVisible()

  await sheet.getByLabel('الحالة').click()
  await page.getByRole('option', { name: 'معطل' }).click()
  await expect(page.getByText('تم تحديث حالة الجهاز')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'PATCH' && r.path === '/equipment/e1')?.body).toEqual({ status: 'FAULTY' })
})

test('a device with no work orders explains how to build its history', async ({ page }) => {
  await page.goto('/customers/c1')
  await page.getByRole('button', { name: 'كاميرا الخزينة' }).click()
  await expect(page.getByText('لم يُربط هذا الجهاز بأي أمر عمل بعد', { exact: false })).toBeVisible()
})
