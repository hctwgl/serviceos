import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2244-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`

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

async function stubWorkbench(
  page: Page,
  options?: { includeSla?: boolean },
) {
  const includeSla = options?.includeSla !== false
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            displayLabel: '测试网点 A',
            contextVersion: 'ctx-v1',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextId: CONTEXT_ID,
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            pageId: 'NETWORK.WORKBENCH',
            routeKey: 'workbench',
            title: '本网点工作台',
            order: 10,
            section: '核心',
            requiredCapabilities: ['networkTask.read'],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/workbench', async (route: Route) => {
    const body: Record<string, unknown> = {
      networkId: NETWORK_ID,
      activeWorkOrderCount: 2,
      activeTaskCount: 3,
      activeTechnicianCount: 1,
      capacity: [],
      asOf: '2026-07-17T12:00:00Z',
      unassignedTechnicianTaskCount: 1,
    }
    if (includeSla) {
      body.slaSummary = { openCount: 2, breachedCount: 1 }
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
  })
}

test.describe('M224 Network Portal 工作台薄 SLA 风险计数', () => {
  test('M224-05a：展示 slaSummary 风险计数', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkbench(page)
    await page.goto('/network-portal/workbench')
    await expect(page.getByTestId('network-portal-workbench')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('workbench-sla-summary')).toBeVisible()
    await expect(page.getByTestId('workbench-sla-open-count')).toContainText('2')
    await expect(page.getByTestId('workbench-sla-breached-count')).toContainText('1')
  })

  test('M224-05b：缺字段时省略区块（不得伪装为 0）', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkbench(page, { includeSla: false })
    await page.goto('/network-portal/workbench')
    await expect(page.getByTestId('network-portal-workbench')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('workbench-active-work-orders')).toBeVisible()
    await expect(page.getByTestId('workbench-sla-summary')).toHaveCount(0)
  })
})
