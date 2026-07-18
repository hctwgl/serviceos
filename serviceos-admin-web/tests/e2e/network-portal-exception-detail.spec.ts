import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const EXCEPTION_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0e9901'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0e9902'

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

async function stubNetworkExceptionDetail(page: Page) {
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
            pageId: 'NETWORK.EXCEPTION.QUEUE',
            routeKey: 'exceptions',
            title: '本网点异常',
            order: 26,
            section: '工单任务',
            requiredCapabilities: ['operations.exception.read'],
          },
        ],
      }),
    })
  })
  const item = {
    exceptionId: EXCEPTION_ID,
    projectId: '019f84a0-cccc-7f8c-9505-36fe5c0e9903',
    sourceType: 'OUTBOUND_DELIVERY',
    category: 'INTEGRATION',
    severity: 'P1',
    errorCode: 'OUTBOUND_UNKNOWN',
    status: 'OPEN',
    workOrderId: '019f84a0-dddd-7f8c-9505-36fe5c0e9904',
    taskId: TASK_ID,
    handlingTaskId: null,
    occurrenceCount: 2,
    openedAt: '2026-07-17T10:00:00Z',
    lastDetectedAt: '2026-07-17T11:00:00Z',
    resolvedAt: null,
    resolutionCode: null,
    allowedActions: [],
  }
  await page.route('**/api/v1/network-portal/operational-exceptions?**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [item],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/operational-exceptions/${EXCEPTION_ID}`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(item),
      })
    },
  )
}

test.describe('M210 Network Portal 运营异常详情', () => {
  test('M210-01/02/03/04：列表深链详情并强调无 ACK', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkExceptionDetail(page)
    await page.goto('/network-portal/exceptions')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-exceptions-table')).toBeVisible()

    await page.getByTestId('exception-case-deeplink').click()
    await expect(page).toHaveURL(new RegExp(`/network-portal/exceptions/${EXCEPTION_ID}`))
    await expect(page.getByTestId('network-portal-exception-detail')).toBeVisible()
    await expect(page.getByTestId('exception-detail-status')).toHaveText('OPEN')
    await expect(page.getByTestId('exception-detail-severity')).toHaveText('P1')
    await expect(page.getByTestId('exception-detail-allowed-actions')).toHaveText('[]')
    await expect(page.getByTestId('exception-detail-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
    await expect(page.getByRole('button', { name: /ACK|确认|解决|resolve/i })).toHaveCount(0)
  })
})
