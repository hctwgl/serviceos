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

test.describe('M194 Network Portal 只读', () => {
  test('M194-08：伪造 X-Network-Context 失败关闭；有 NETWORK 上下文时进入只读壳', async ({
    page,
  }) => {
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/workbench')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    // 本地 developer 可能无 NETWORK 人格；有上下文时验证导航 pageId，否则验证失败关闭文案。
    const navError = page.getByTestId('network-portal-error')
    const workOrdersNav = page.getByTestId('nav-network-work-orders')
    if (await workOrdersNav.count()) {
      await workOrdersNav.click()
      await expect(page.getByTestId('network-portal-work-orders')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.WORKORDER.LIST"]')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
