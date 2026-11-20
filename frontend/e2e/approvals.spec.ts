import { expect, test } from './fixtures'

test.beforeEach(({ api }) => {
  api.signedIn = true
  api.jobs.push({
    id: 'j3',
    projectId: null,
    type: 'INSTALLATION',
    status: 'NEW',
    priority: 'NORMAL',
    title: 'تركيب نظام إنذار',
    reportedIssue: null,
    customer: { id: 'c1', name: 'مستشفى النيل', phone: '+20223456789', mobile: null },
    site: { id: 's1', name: 'الفرع الرئيسي', city: 'القاهرة', addressLine1: null, accessInstructions: null },
    assignees: [],
    budget: '0.00',
    sla: null,
    createdAt: '2030-01-01T08:00:00Z',
    version: 0,
  })
})

test('record a quotation approval request, then the customer approves it', async ({ page, api }) => {
  await page.goto('/jobs/j3')
  await expect(page.getByText('لا توجد موافقات مسجلة')).toBeVisible()
  await page.getByRole('button', { name: 'تسجيل موافقة' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('ملاحظات').fill('عرض سعر رقم 15')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByText('بانتظار القرار')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'POST' && r.path === '/jobs/j3/approvals')?.body).toEqual({
    kind: 'QUOTATION',
    notes: 'عرض سعر رقم 15',
  })

  await page.getByRole('button', { name: 'موافقة', exact: true }).click()
  const decide = page.getByRole('dialog')
  await decide.getByRole('button', { name: 'موافقة' }).click()
  await expect(decide.getByText('مطلوب')).toBeVisible()
  await decide.getByLabel('اسم من اتخذ القرار').fill('د. سامي فؤاد')
  await decide.getByRole('button', { name: 'موافقة' }).click()
  await expect(decide).toBeHidden()
  await expect(page.getByText('موافَق عليها')).toBeVisible()
  await expect(page.getByText(/د\. سامي فؤاد —/)).toBeVisible()
  expect(api.requests.find((r) => r.path === '/jobs/j3/approvals/a1/decision')?.body).toEqual({
    status: 'APPROVED',
    approverName: 'د. سامي فؤاد',
  })
})

test('an on-site acceptance is recorded with its decision in one step', async ({ page, api }) => {
  await page.goto('/jobs/j3')
  await page.getByRole('button', { name: 'تسجيل موافقة' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('نوع الموافقة').click()
  await page.getByRole('option', { name: 'استلام العمل' }).click()
  await dialog.getByLabel('قرار العميل').click()
  await page.getByRole('option', { name: 'موافَق عليها' }).click()
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(dialog.getByText('مطلوب')).toBeVisible() // decider's name is required with a decision
  await dialog.getByLabel('اسم من اتخذ القرار').fill('م. هالة')
  await dialog.getByRole('button', { name: 'حفظ' }).click()
  await expect(page.getByText('تم تسجيل الموافقة')).toBeVisible()
  expect(api.requests.find((r) => r.method === 'POST' && r.path === '/jobs/j3/approvals')?.body).toEqual({
    kind: 'COMPLETION',
    decision: { status: 'APPROVED', approverName: 'م. هالة' },
  })
})
