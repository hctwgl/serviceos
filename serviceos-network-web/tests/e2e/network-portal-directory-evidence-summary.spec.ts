import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const SLOT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee009'
const ITEM_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee00a'

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

const evidenceSlot = {
  slotId: SLOT_ID,
  taskId: TASK_ID,
  projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ee005',
  templateKey: 'survey.site',
  templateVersion: '1.0.0',
  requirementCode: 'site.photo',
  occurrenceKey: 'default',
  requirementName: '现场照片',
  mediaType: 'PHOTO',
  required: true,
  minCount: 1,
  maxCount: 2,
  status: 'MISSING',
  resolvedAt: '2026-07-17T08:00:00Z',
  slotGeneration: 1,
  active: true,
  transition: 'ACTIVATED',
  requiredDisposition: 'NONE',
}

const evidenceItem = {
  evidenceItemId: ITEM_ID,
  taskId: TASK_ID,
  projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ee005',
  evidenceSlotId: SLOT_ID,
  itemOrdinal: 1,
  status: 'OPEN',
  revisionCount: 0,
  latestRevisionNumber: null,
  latestRevisionStatus: null,
  latestRevisionId: null,
  latestMimeType: null,
}

test.describe('M235 Network Portal 目录页资料 Evidence 服务端摘要', () => {
  test('M235-05a：工单目录展示资料列', async ({ page }) => {
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
          evidenceSlots: [evidenceSlot],
          evidenceItems: [evidenceItem],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-evidence-summary')).toContainText('缺失')
    await expect(page.getByTestId('work-order-evidence-summary')).toContainText('待处理')
  })

  test('M235-05b：缺 evidenceSlots 时省略资料列', async ({ page }) => {
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
    await expect(page.getByTestId('work-order-evidence-summary')).toHaveCount(0)
  })

  test('M235-05c：任务目录展示资料列', async ({ page }) => {
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
          evidenceSlots: [evidenceSlot],
          evidenceItems: [evidenceItem],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/tasks')
    await expect(page.getByTestId('network-tasks-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('task-evidence-summary')).toContainText('缺失')
  })
})
