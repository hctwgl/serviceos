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

test.describe('M204 Network Portal 师傅关系与资质', () => {
  test('M204-10：伪造上下文失败关闭；有 NETWORK 上下文时展示管理表单', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/technicians')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    const navError = page.getByTestId('network-portal-error')
    const techniciansNav = page.getByTestId('nav-network-technicians')
    if (await techniciansNav.count()) {
      await techniciansNav.click()
      await expect(page.getByTestId('network-portal-technicians')).toBeVisible()
      await expect(page.getByTestId('network-manage-technician-forms')).toBeVisible()
      await expect(page.getByTestId('network-create-membership-form')).toBeVisible()
      await expect(page.getByTestId('network-terminate-membership-form')).toBeVisible()
      await expect(page.getByTestId('network-submit-qualification-form')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.TECHNICIAN.LIST"]')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.QUALIFICATION"]')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
