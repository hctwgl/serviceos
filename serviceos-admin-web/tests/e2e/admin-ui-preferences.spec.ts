import { expect, test, type Page } from '@playwright/test'

async function loginWithLocalKeycloak(
  page: Page,
  username = 'developer',
  password = 'local-dev-change-me',
) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('input[type="submit"], button[type="submit"]').click()
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill(`${username}@serviceos.local`)
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }
  await expect(page).toHaveURL(/\/work-orders$/)
}

test.describe('M190 Admin UI Preferences', () => {
  test('登录 → 设置 theme → 重载仍持久', async ({ page }) => {
    await loginWithLocalKeycloak(page)

    const getPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/me/ui-preferences'),
    )
    await page.goto('/settings/preferences')
    await expect(page.getByTestId('ui-preferences-page')).toBeVisible({ timeout: 15_000 })
    expect((await getPromise).status()).toBe(200)

    await page.getByTestId('pref-theme').selectOption('DARK')
    await page.getByTestId('pref-density').selectOption('COMPACT')
    await page.getByTestId('pref-reduce-motion').check()

    const putPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().includes('/api/v1/me/ui-preferences'),
    )
    await page.getByTestId('pref-save').click()
    expect((await putPromise).status()).toBe(200)
    await expect(page.getByTestId('pref-message')).toContainText('已保存')
    await expect(page.locator('html')).toHaveClass(/theme-dark/)

    await page.reload()
    await expect(page.getByTestId('pref-theme')).toHaveValue('DARK', { timeout: 15_000 })
    await expect(page.getByTestId('pref-density')).toHaveValue('COMPACT')
    await expect(page.getByTestId('pref-reduce-motion')).toBeChecked()
    await expect(page.locator('html')).toHaveClass(/theme-dark/)
    await expect(page.locator('html')).toHaveClass(/density-compact/)
    await expect(page.locator('html')).toHaveClass(/reduce-motion/)
  })
})
