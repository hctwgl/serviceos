import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const CORRECTION_ID = 'corr-aaaa-1111-4111-8111-aaaaaaaaaaaa'
const EXCEPTION_ID = 'exc-bbbb-2222-4222-8222-bbbbbbbbbbbb'
const TASK_ID = 'task-cccc-3333-4333-8333-cccccccccccc'
const WORK_ORDER_ID = 'wo-dddd-4444-4444-8444-dddddddddddd'

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

async function stubIdentity(page: Page) {
  await page.unroute('**/api/v1/network-portal/**').catch(() => undefined)
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [{ contextId: CONTEXT_ID, portal: 'NETWORK', scopeRef: NETWORK_ID, version: 'ctx-v1' }],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        capabilityCodes: ['evidence.read', 'operations.exception.read', 'networkTask.read'],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        items: [
          {
            pageId: 'NETWORK.CORRECTION.QUEUE',
            routeKey: 'corrections',
            title: '整改',
            order: 30,
            section: '协作',
            requiredCapabilities: [],
          },
          {
            pageId: 'NETWORK.EXCEPTION.QUEUE',
            routeKey: 'exceptions',
            title: '异常',
            order: 40,
            section: '协作',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
}

test.describe('M392 Network 整改与异常中心产品化', () => {
  test('整改队列 SummaryStrip 与代补入口', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/network-portal/correction-cases**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-20T04:00:00Z',
          items: [
            {
              correctionCaseId: CORRECTION_ID,
              projectId: 'project-1',
              taskId: TASK_ID,
              sourceReviewCaseId: 'review-1',
              sourceReviewDecisionId: 'decision-1',
              reasonCodes: ['MISSING_PHOTO'],
              correctionTaskId: TASK_ID,
              status: 'OPEN',
              createdAt: '2026-07-20T03:00:00Z',
              latestResubmissionSnapshotId: null,
              closedAt: null,
              waivedAt: null,
              resubmissionCount: 1,
            },
          ],
        }),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/corrections')
    await expect(page.getByTestId('network-portal-corrections')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-summary-strip')).toBeVisible()
    await expect(page.getByTestId('corrections-summary-open')).toContainText('1')
    await expect(page.getByTestId('correction-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-corrections-product-1440.png',
      fullPage: true,
    })
  })

  test('异常队列建议动作深链到领域入口', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/network-portal/operational-exceptions**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-20T04:00:00Z',
          items: [
            {
              exceptionId: EXCEPTION_ID,
              status: 'OPEN',
              severity: 'HIGH',
              errorCode: 'DISPATCH_FAILED',
              category: 'DISPATCH',
              sourceType: 'SYSTEM',
              projectId: 'project-1',
              workOrderId: WORK_ORDER_ID,
              taskId: TASK_ID,
              handlingTaskId: TASK_ID,
              occurrenceCount: 2,
              openedAt: '2026-07-20T03:00:00Z',
              lastDetectedAt: '2026-07-20T03:30:00Z',
              resolvedAt: null,
              resolutionCode: null,
              allowedActions: [],
            },
          ],
        }),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/exceptions')
    await expect(page.getByTestId('network-portal-exceptions')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('exceptions-summary-open')).toContainText('1')
    await expect(page.getByTestId('exception-suggested-action')).toContainText('分配师傅')
    await expect(page.getByTestId('exception-suggested-action')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
    await expect(page.getByTestId('exception-source-category')).toContainText('DISPATCH')
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-exceptions-product-1440.png',
      fullPage: true,
    })
  })
})
