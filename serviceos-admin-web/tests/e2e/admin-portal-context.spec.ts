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

test.describe('M188 Portal 上下文与导航', () => {
  test('M188-01/03：/me/navigation 驱动治理入口 pageId', async ({ page }) => {
    const meContexts = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' && response.url().includes('/api/v1/me/contexts'),
    )
    const meNavigation = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' && response.url().includes('/api/v1/me/navigation'),
    )
    await loginWithLocalKeycloak(page)
    expect((await meContexts).status()).toBe(200)
    expect((await meNavigation).status()).toBe(200)

    await expect(page.getByTestId('nav-users')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('[data-page-id="ADMIN.USER.DIRECTORY"]')).toBeVisible()
    await expect(page.getByTestId('nav-grants')).toBeVisible()
  })

  test('M188-02/04：伪造上下文与隐藏 URL 失败关闭', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await page.getByTestId('nav-portal-stubs').click()
    await expect(page.getByTestId('portal-stubs-page')).toBeVisible()
    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText('伪造 NETWORK 上下文被拒绝')

    await page.goto('/users/00000000-0000-4000-8000-000000000099')
    await expect(page.getByTestId('access-denied')).toBeVisible({ timeout: 15_000 })
  })

  test('M188-05：viewer 无治理导航；深链仍失败关闭', async ({ page }) => {
    await loginWithLocalKeycloak(page, 'viewer', 'local-dev-change-me')
    await expect(page.getByTestId('nav-users')).toHaveCount(0)
    await expect(page.getByTestId('nav-grants')).toHaveCount(0)
    await page.goto('/users/06b612f3-a901-4b0e-bd90-86b4259cc087')
    await expect(page.getByTestId('access-denied')).toBeVisible({ timeout: 15_000 })
  })
})
