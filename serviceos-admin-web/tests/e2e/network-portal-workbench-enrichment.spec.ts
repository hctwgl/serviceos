import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
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

async function stubNetworkWorkbench(page: Page) {
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
          {
            pageId: 'NETWORK.TASK.QUEUE',
            routeKey: 'tasks',
            title: '工单任务',
            order: 20,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.CORRECTION.QUEUE',
            routeKey: 'corrections',
            title: '本网点整改',
            order: 27,
            section: '工单任务',
            requiredCapabilities: ['evidence.read'],
          },
          {
            pageId: 'NETWORK.EXCEPTION.QUEUE',
            routeKey: 'exceptions',
            title: '本网点异常',
            order: 26,
            section: '工单任务',
            requiredCapabilities: ['operations.exception.read'],
          },
          {
            pageId: 'NETWORK.QUALIFICATION',
            routeKey: 'technicians/qualifications',
            title: '资质与到期',
            order: 32,
            section: '人员与能力',
            requiredCapabilities: ['technician.readOwnNetwork'],
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
            updatedAt: '2026-07-17T12:00:00Z',
          },
        ],
        asOf: '2026-07-17T12:00:00Z',
        unassignedTechnicianTaskCount: 1,
        openCorrectionCaseCount: 2,
        openOperationalExceptionCount: 0,
        pendingQualificationCount: 4,
      }),
    })
  })
}

test.describe('M207 Network Portal 工作台能力门控计数增强', () => {
  test('M207-08：展示 capacity 与 enrichment 计数深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkWorkbench(page)
    await page.goto('/network-portal/workbench')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-workbench')).toBeVisible()

    await expect(page.getByTestId('network-workbench-counts')).toContainText('ACTIVE 工单：2')
    await expect(page.getByTestId('workbench-unassigned-count')).toContainText('待指派任务：1')
    await expect(page.getByTestId('workbench-correction-count')).toContainText('待处理整改：2')
    await expect(page.getByTestId('workbench-exception-count')).toContainText('待处理异常：0')
    await expect(page.getByTestId('workbench-qualification-count')).toContainText('待审资质：4')
    await expect(page.getByTestId('workbench-capacity-INSTALLATION')).toContainText(
      '占用 3 / 上限 10',
    )

    await page.getByTestId('workbench-correction-count').click()
    await expect(page).toHaveURL(/\/network-portal\/corrections/)
  })
})
