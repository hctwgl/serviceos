import { navigateTechnician, expect, test, type Page } from './support/fixture'

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

test.describe('M195 Technician Portal Feed', () => {
  test('M195-08：伪造 X-Technician-Context 失败关闭；有 TECHNICIAN 上下文时进入 Feed 壳', async ({
    page,
  }) => {
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, '/technician-portal/task-feed')
    await expect(page.getByTestId('technician-portal-shell')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('forge-technician-context').click()
    await expect(page.getByTestId('forge-technician-result')).toContainText(
      '伪造 TECHNICIAN 上下文被拒绝',
    )

    const navError = page.getByTestId('technician-nav-error')
    const feedNav = page.getByTestId('nav-technician-task-feed')
    if (await feedNav.count()) {
      await feedNav.click()
      await expect(page.getByTestId('technician-portal-task-feed')).toBeVisible()
      await expect(page.locator('[data-page-id="TECHNICIAN.TASK.LIST"]')).toBeVisible()
    } else {
      await expect(navError).toBeVisible()
    }
  })
})
