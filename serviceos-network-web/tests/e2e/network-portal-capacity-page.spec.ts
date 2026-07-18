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

test.describe('M208 Network Portal 产能页', () => {
  test('M208-02/03/04：伪造上下文拒绝；有 NETWORK 上下文时进入产能页', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/capacity')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    const navError = page.getByTestId('network-portal-error')
    const capacityNav = page.getByTestId('nav-network-capacity')
    if (await capacityNav.count()) {
      await capacityNav.click()
      await expect(page.getByTestId('network-portal-capacity')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.CAPACITY"]')).toBeVisible()
      await expect(page.getByTestId('network-capacity-table')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
