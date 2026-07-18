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

async function stubWorkspaceSla(
  page: Page,
  options?: { includeSlaSummary?: boolean },
) {
  const includeSlaSummary = options?.includeSlaSummary !== false
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
      const body: Record<string, unknown> = {
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
      }
      if (includeSlaSummary) {
        body.slaSummary = { openCount: 2, breachedCount: 1 }
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
  // Soft-omit related fan-in noise for focused SLA assertions.
  await page.route('**/api/v1/network-portal/correction-cases**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
  await page.route('**/api/v1/network-portal/operational-exceptions**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks/*/appointments**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks/*/contact-attempts**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
}

test.describe('M221 Network Portal 工作区薄 SLA 摘要', () => {
  test('M221-05a：有 slaSummary 时展示 open/breached 计数', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceSla(page, { includeSlaSummary: true })
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-sla-summary')).toBeVisible()
    await expect(page.getByTestId('workspace-sla-open-count')).toHaveText('2')
    await expect(page.getByTestId('workspace-sla-breached-count')).toHaveText('1')
  })

  test('M221-05b：缺 slaSummary 时省略区块（不得伪装为 0）', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceSla(page, { includeSlaSummary: false })
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-header-fields')).toBeVisible()
    await expect(page.getByTestId('workspace-sla-summary')).toHaveCount(0)
  })
})
