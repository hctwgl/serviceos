import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const AS_OF = '2026-07-17T12:00:00Z'
const CAPACITY_UPDATED_AT = '2026-07-17T11:45:00Z'

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

async function stubNetworkWorkbench(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: AS_OF,
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
        asOf: AS_OF,
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
  await page.route('**/api/v1/network-portal/workbench**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        activeWorkOrderCount: 2,
        activeTaskCount: 3,
        activeTechnicianCount: 1,
        capacity: [
          {
            capacityCounterId: '019f84a0-dddd-7f8c-9505-36fe5c0e880e',
            businessType: 'INSTALLATION',
            maxUnits: 10,
            occupiedUnits: 3,
            availableUnits: 7,
            version: 1,
            updatedAt: CAPACITY_UPDATED_AT,
          },
        ],
        asOf: AS_OF,
      }),
    })
  })
}

test.describe('M237 Network Portal 工作台统计时间展示', () => {
  test('M237-01：展示页级统计时间与容量行更新时间', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkWorkbench(page)
    await page.goto('/network-portal/workbench')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-workbench')).toBeVisible()

    await expect(page.getByTestId('network-workbench-as-of')).toContainText(`统计时间：${AS_OF}`)
    await expect(page.getByTestId('workbench-capacity-INSTALLATION')).toContainText(
      '占用 3 / 上限 10',
    )
    await expect(page.getByTestId('workbench-capacity-updated-at')).toContainText(
      `更新时间 ${CAPACITY_UPDATED_AT}`,
    )
  })
})
