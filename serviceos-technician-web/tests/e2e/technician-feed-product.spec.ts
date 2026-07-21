import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84a0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'

async function loginWithLocalKeycloak(page: Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('input[type="submit"], button[type="submit"]').click()
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill('developer@serviceos.local')
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }
  await expect(page).toHaveURL(/\/work-orders$/)
}

async function stubFeedProduct(page: Page) {
  await page.unroute('**/api/v1/technician/**').catch(() => undefined)
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'TECHNICIAN',
            scopeRef: NETWORK_ID,
            version: 'ctx-v1',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        capabilityCodes: ['task.readAssigned', 'evidence.read'],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        items: [
          {
            pageId: 'TECHNICIAN.TASK.LIST',
            routeKey: 'task-feed',
            title: '今日任务',
            order: 1,
            section: '任务',
            requiredCapabilities: [],
          },
          {
            pageId: 'TECHNICIAN.SCHEDULE',
            routeKey: 'schedule',
            title: '日程',
            order: 2,
            section: '任务',
            requiredCapabilities: [],
          },
          {
            pageId: 'TECHNICIAN.SYNC.SUMMARY',
            routeKey: 'sync-summary',
            title: '同步',
            order: 3,
            section: '任务',
            requiredCapabilities: [],
          },
          {
            pageId: 'TECHNICIAN.ME',
            routeKey: 'me',
            title: '我的',
            order: 4,
            section: '账户',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/task-feed**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-20T04:00:00Z',
        nextCursor: null,
        items: [
          {
            itemType: 'ASSIGNMENT',
            taskId: TASK_ID,
            workOrderId: 'wo-1',
            projectId: 'project-1',
            serviceAssignmentId: 'sa-1',
            taskAssignmentId: 'ta-1',
            taskType: 'INSTALL',
            taskKind: 'HUMAN',
            stageCode: 'S1',
            taskStatus: 'READY',
            businessType: 'INSTALLATION',
            effectiveFrom: '2026-07-20T03:00:00Z',
            cursor: 'c1',
            invalidationReason: null,
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/corrections**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })
}

test.describe('M393 Technician H5 今日任务产品化', () => {
  test('移动壳、概览与任务卡片主操作', async ({ page }) => {
    await stubFeedProduct(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, '/technician-portal/task-feed')

    await expect(page.getByTestId('technician-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-bottom-nav')).toBeVisible()
    await expect(page.getByTestId('technician-feed-summary')).toBeVisible()
    await expect(page.getByTestId('technician-feed-count-today')).toHaveText('1')
    await expect(page.getByTestId(`technician-feed-row-${TASK_ID}`)).toBeVisible()
    await expect(page.getByTestId('technician-feed-task-detail-deeplink')).toContainText(/开始处理|打开任务/)
    await expect(page.getByTestId('technician-browser-boundary')).toContainText('H5 仅承诺')

    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/technician-feed-product-390.png',
      fullPage: true,
    })
  })
})
