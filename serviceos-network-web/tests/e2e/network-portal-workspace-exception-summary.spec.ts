import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2255-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const EXCEPTION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee004'

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

async function stubWorkspaceExceptions(
  page: Page,
  options?: { includeExceptions?: boolean },
) {
  const includeExceptions = options?.includeExceptions !== false
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
      if (includeExceptions) {
        body.exceptions = [
          {
            exceptionId: EXCEPTION_ID,
            projectId: null,
            sourceType: 'OUTBOUND_DELIVERY',
            category: 'INTEGRATION',
            severity: 'P2',
            errorCode: 'OUTBOUND_UNKNOWN',
            status: 'OPEN',
            workOrderId: WORK_ORDER_ID,
            taskId: TASK_ID,
            handlingTaskId: null,
            occurrenceCount: 1,
            openedAt: '2026-07-17T11:00:00Z',
            lastDetectedAt: '2026-07-17T11:00:00Z',
            resolvedAt: null,
            resolutionCode: null,
            allowedActions: [],
          },
        ]
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
  for (const pattern of [
    '**/api/v1/network-portal/correction-cases**',
    '**/api/v1/network-portal/operational-exceptions**',
    '**/api/v1/network-portal/tasks/*/appointments**',
    '**/api/v1/network-portal/tasks/*/contact-attempts**',
    '**/api/v1/network-portal/technicians**',
  ]) {
    await page.route(pattern, async (route: Route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
      })
    })
  }
}

test.describe('M226 Network Portal 工作区运营异常摘要', () => {
  test('M226-05a：展示 exceptions 摘要与深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceExceptions(page)
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-related-exceptions')).toBeVisible()
    await expect(page.getByTestId(`workspace-related-exception-${EXCEPTION_ID}`)).toContainText(
      'OUTBOUND_UNKNOWN',
    )
    await expect(
      page.getByTestId(`workspace-related-exception-${EXCEPTION_ID}`).locator('a'),
    ).toHaveAttribute('href', `/network-portal/exceptions/${EXCEPTION_ID}`)
  })

  test('M226-05b：缺字段时省略区块（不得伪装为空列表）', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceExceptions(page, { includeExceptions: false })
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-header-fields')).toBeVisible()
    await expect(page.getByTestId('workspace-related-exceptions')).toHaveCount(0)
  })
})
