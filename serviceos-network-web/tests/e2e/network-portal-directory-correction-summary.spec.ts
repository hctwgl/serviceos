import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const CORRECTION_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee006'

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

async function stubContexts(page: Page) {
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
            title: '工单',
            order: 1,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.TASK.QUEUE',
            routeKey: 'tasks',
            title: '任务',
            order: 2,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
        ],
      }),
    })
  })
}

const correctionSummary = {
  correctionCaseId: CORRECTION_ID,
  taskId: TASK_ID,
  projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ee005',
  sourceReviewCaseId: '019f84a0-ffff-7f8c-9505-36fe5c0ee007',
  sourceReviewDecisionId: '019f84a0-1111-7f8c-9505-36fe5c0ee008',
  reasonCodes: ['MISSING_PHOTO'],
  correctionTaskId: null,
  status: 'OPEN',
  createdAt: '2026-07-17T08:00:00Z',
  latestResubmissionSnapshotId: null,
  closedAt: null,
  waivedAt: null,
  resubmissions: [],
}

test.describe('M233 Network Portal 目录页资料整改服务端摘要', () => {
  test('M233-05a：工单目录展示整改列', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubContexts(page)
    await page.route('**/api/v1/network-portal/work-orders**', async (route: Route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskIds: [TASK_ID],
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          corrections: [correctionSummary],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-correction-summary')).toContainText('OPEN')
    await expect(page.getByTestId('work-order-correction-summary')).toContainText('MISSING_PHOTO')
  })

  test('M233-05b：缺 corrections 时省略整改列', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubContexts(page)
    await page.route('**/api/v1/network-portal/work-orders**', async (route: Route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskIds: [TASK_ID],
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-correction-summary')).toHaveCount(0)
  })

  test('M233-05c：任务目录展示整改列', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubContexts(page)
    await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ networkId: NETWORK_ID, asOf: '2026-07-17T12:00:00Z', items: [] }),
      })
    })
    await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
      const url = route.request().url()
      if (url.includes('/appointments') || url.includes('/contact-attempts')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          corrections: [correctionSummary],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/tasks')
    await expect(page.getByTestId('network-tasks-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('task-correction-summary')).toContainText('MISSING_PHOTO')
  })
})
