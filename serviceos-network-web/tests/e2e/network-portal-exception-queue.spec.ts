import { expect, test, type Page } from './support/fixture'
import { navigateNetwork } from './support/fixture'

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

test.describe('M203 Network Portal 运营异常队列', () => {
  test('M203-08：伪造上下文拒绝；有 NETWORK 上下文时进入异常页', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/exceptions')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    const navError = page.getByTestId('network-portal-error')
    const exceptionsNav = page.getByTestId('nav-network-exceptions')
    if (await exceptionsNav.count()) {
      await exceptionsNav.click()
      await expect(page.getByTestId('network-portal-exceptions')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.EXCEPTION.QUEUE"]')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
