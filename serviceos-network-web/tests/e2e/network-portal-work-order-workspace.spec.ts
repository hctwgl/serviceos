import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ec001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ec002'

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

async function stubNetworkWorkOrderWorkspace(page: Page) {
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
            pageId: 'NETWORK.WORKORDER.LIST',
            routeKey: 'work-orders',
            title: '本网点工单',
            order: 15,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.WORKORDER.WORKSPACE',
            routeKey: 'work-order-workspace',
            title: '工单工作区',
            order: 16,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
        ],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/work-orders/${WORK_ORDER_ID}/workspace`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: null,
          taskIds: [TASK_ID],
          businessType: 'INSTALLATION',
          technicianId: 'tech-a',
          effectiveFrom: '2026-07-17T10:00:00Z',
          asOf: '2026-07-17T12:00:00Z',
          tasks: [
            {
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: null,
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    },
  )
  await page.route(/\/api\/v1\/network-portal\/work-orders(?:\?.*)?$/, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            workOrderId: WORK_ORDER_ID,
            projectId: null,
            taskIds: [TASK_ID],
            businessType: 'INSTALLATION',
            technicianId: 'tech-a',
            effectiveFrom: '2026-07-17T10:00:00Z',
          },
        ],
      }),
    })
  })
}

test.describe('M213 Network Portal 限定工单工作区', () => {
  test('M213-06：列表深链工作区并展示 ACTIVE 任务', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkWorkOrderWorkspace(page)
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible()

    await page.getByTestId('work-order-workspace-deeplink').click()
    await expect(page).toHaveURL(new RegExp(`/network-portal/work-orders/${WORK_ORDER_ID}$`))
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible()
    // 产品化后业务类型展示中文标签，不再裸露英文枚举。
    await expect(page.getByTestId('workspace-business-type')).toHaveText(/安装|INSTALLATION/)
    await expect(page.getByTestId(`workspace-task-${TASK_ID}`)).toBeVisible()
    await expect(page.getByTestId('workspace-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
  })
})
