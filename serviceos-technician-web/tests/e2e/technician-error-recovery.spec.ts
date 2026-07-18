import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

async function login(page: Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('button[type="submit"]').click()
  await expect(page).toHaveURL(/\/work-orders$/)
}

test('409/5xx 使用固定可行动文案并允许重试', async ({ page }) => {
  let status = 409
  await page.route('**/api/v1/technician/me/task-feed**', async (route: Route) => {
    await route.fulfill({
      status,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: '不得向现场端回显的内部标题', detail: 'sensitive-internal-detail' }),
    })
  })
  await login(page)
  await navigateTechnician(page, '/technician-portal/task-feed')
  await expect(page.getByTestId('technician-portal-error')).toHaveText('数据已变化，请刷新后重试')
  await expect(page.getByText('sensitive-internal-detail')).toHaveCount(0)

  status = 503
  await page.getByTestId('technician-feed-refresh').click()
  await expect(page.getByTestId('technician-portal-error')).toHaveText('服务暂时不可用，请稍后重试')
})
