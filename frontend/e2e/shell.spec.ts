import { expect, test } from './fixtures'

test.describe('Arabic first (RTL)', () => {
  test('sign-in page is Arabic and right-to-left by default', async ({ page, api }) => {
    void api
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl')
    await expect(page.locator('html')).toHaveAttribute('lang', 'ar')
    await expect(page.getByText('مرحباً بعودتك')).toBeVisible()
    await expect(page).toHaveScreenshot('login-ar.png')
  })

  test('wrong password shows the server message in Arabic; then the user signs in', async ({ page, api }) => {
    await page.goto('/login')
    await page.getByLabel('البريد الإلكتروني').fill('ahmed@example.com')
    await page.getByLabel('كلمة المرور').fill('nope')
    await page.getByRole('button', { name: 'تسجيل الدخول' }).click()
    await expect(page.getByText('البريد الإلكتروني أو كلمة المرور غير صحيحة')).toBeVisible()

    await page.getByLabel('كلمة المرور').fill('correct-horse')
    await page.getByRole('button', { name: 'تسجيل الدخول' }).click()
    await expect(page.getByRole('heading', { name: 'أهلاً أحمد' })).toBeVisible()
    await expect(page.getByText('تنتهي الفترة التجريبية خلال 12 يوم')).toBeVisible()
    const login = api.requests.findLast((r) => r.path === '/auth/login')
    expect(login?.body).toEqual({ email: 'ahmed@example.com', password: 'correct-horse' })
  })

  test('the sidebar sits on the right in RTL (desktop)', async ({ page, api, isMobile }) => {
    test.skip(isMobile, 'the sidebar is a sheet on phones')
    api.signedIn = true
    await page.goto('/')
    const sidebar = page.locator('[data-slot="sidebar-container"]').first()
    const box = await sidebar.boundingBox()
    expect(box!.x).toBeGreaterThan(640)
    await expect(page).toHaveScreenshot('home-ar.png')
  })
})

test.describe('English (LTR)', () => {
  test.use({ language: 'en' })

  test('sign-in page is English and left-to-right', async ({ page, api }) => {
    void api
    await page.goto('/login')
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr')
    await expect(page.getByText('Welcome back')).toBeVisible()
    await expect(page).toHaveScreenshot('login-en.png')
  })

  test('the sidebar sits on the left in LTR (desktop)', async ({ page, api, isMobile }) => {
    test.skip(isMobile, 'the sidebar is a sheet on phones')
    api.signedIn = true
    await page.goto('/')
    const box = await page.locator('[data-slot="sidebar-container"]').first().boundingBox()
    expect(box!.x).toBeLessThan(10)
    await expect(page).toHaveScreenshot('home-en.png')
  })
})

test('switching the language flips the direction without a reload', async ({ page, api }) => {
  void api
  await page.goto('/login')
  await page.getByRole('button', { name: 'اللغة' }).click()
  await page.getByRole('menuitemradio', { name: 'English' }).click()
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr')
  await expect(page.getByText('Welcome back')).toBeVisible()
  // remembered for the next visit
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr')
})
