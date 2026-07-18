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

test.describe('M284 Admin 配置设计器壳', () => {
  test('打开设计器并渲染 WORKFLOW 节点结构预览', async ({ page }) => {
    await page.route('**/api/v1/configuration/drafts**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
        return
      }
      await route.continue()
    })

    await loginWithLocalKeycloak(page)
    await page.goto('/configuration/designer')
    await expect(page.getByTestId('configuration-designer')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('workflow-preview')).toBeVisible()
    await expect(page.getByTestId('workflow-preview')).toContainText('TASK_A')
    await expect(page.getByTestId('workflow-preview')).toContainText('SERVICE_TASK')
  })
})
