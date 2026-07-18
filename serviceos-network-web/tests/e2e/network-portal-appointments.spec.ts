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

test.describe('M197 Network Portal 预约协作', () => {
  test('M197-10：伪造上下文失败关闭；有 NETWORK 上下文时展示预约表单', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/tasks')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    const navError = page.getByTestId('network-portal-error')
    const tasksNav = page.getByTestId('nav-network-tasks')
    if (await tasksNav.count()) {
      await tasksNav.click()
      await expect(page.getByTestId('network-portal-tasks')).toBeVisible()
      await expect(page.getByTestId('network-appointment-form')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.APPOINTMENT"]')).toBeVisible()
      await expect(page.getByTestId('appointment-propose-submit')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
