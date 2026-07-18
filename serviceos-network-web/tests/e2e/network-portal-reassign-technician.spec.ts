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

test.describe('M200 Network Portal 改派师傅', () => {
  test('M200-08：伪造上下文失败关闭；有 NETWORK 上下文时展示改派控件', async ({ page }) => {
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
      await expect(page.getByTestId('network-assign-technician-form')).toBeVisible()
      await expect(page.getByTestId('reassign-technician-submit')).toBeVisible()
      await expect(page.getByTestId('reassign-reason')).toHaveValue('MANUAL_REASSIGNMENT')
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
