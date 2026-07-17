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

test.describe('M198 Network Portal 预约生命周期', () => {
  test('M198-10：伪造上下文失败关闭；有 NETWORK 上下文时展示改约/取消控件', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await page.goto('/network-portal/tasks')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-network-context').click()
    await expect(page.getByTestId('forge-network-result')).toContainText(
      '伪造 NETWORK 上下文被拒绝',
    )

    const navError = page.getByTestId('network-nav-error')
    const tasksNav = page.getByTestId('nav-network-tasks')
    if (await tasksNav.count()) {
      await tasksNav.click()
      await expect(page.getByTestId('network-portal-tasks')).toBeVisible()
      await expect(page.getByTestId('network-appointment-form')).toBeVisible()
      await expect(page.locator('[data-page-id="NETWORK.APPOINTMENT"]')).toBeVisible()
      await expect(page.getByTestId('appointment-propose-submit')).toBeVisible()
      await expect(page.getByTestId('appointment-reschedule-reason')).toBeVisible()
      await expect(page.getByTestId('appointment-cancel-reason')).toBeVisible()
      await expect(page.getByTestId('appointment-reschedule-start')).toBeVisible()
      await expect(page.getByTestId('appointment-cancel-reason')).toHaveValue('CUSTOMER_CANCELLED')
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
