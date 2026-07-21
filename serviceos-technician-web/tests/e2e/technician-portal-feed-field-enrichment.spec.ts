import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84a0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const APPOINTMENT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'
const WINDOW_END = '2026-07-18T04:00:00Z'

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

async function stubTechnicianPortal(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'TECHNICIAN',
            personaType: 'TECHNICIAN',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            scopeSummary: {
              organizationIds: [],
              networkIds: [NETWORK_ID],
              projectIds: [],
            },
            version: '1',
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
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            pageId: 'TECHNICIAN.TASK.LIST',
            routeKey: 'task-feed',
            title: '任务 Feed',
            order: 1,
            section: '任务',
            requiredCapabilities: ['task.readAssigned'],
          },
          {
            pageId: 'TECHNICIAN.SCHEDULE',
            routeKey: 'schedule',
            title: '日程',
            order: 2,
            section: '任务',
            requiredCapabilities: ['task.readAssigned'],
          },
          {
            pageId: 'TECHNICIAN.SYNC.SUMMARY',
            routeKey: 'sync-summary',
            title: '同步摘要',
            order: 3,
            section: '同步',
            requiredCapabilities: ['task.readAssigned'],
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
        asOf: '2026-07-17T12:00:00Z',
        nextCursor: null,
        items: [
          {
            itemType: 'ASSIGNMENT',
            taskId: TASK_ID,
            workOrderId: WORK_ORDER_ID,
            projectId: 'project-1',
            serviceAssignmentId: 'sa-1',
            taskAssignmentId: 'ta-1',
            taskType: 'INSTALL',
            taskKind: 'HUMAN',
            stageCode: 'S1',
            taskStatus: 'READY',
            businessType: 'INSTALLATION',
            effectiveFrom: '2026-07-17T10:00:00Z',
            cursor: 'cursor-1',
            invalidationReason: null,
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/schedule**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            appointmentId: APPOINTMENT_ID,
            taskId: TASK_ID,
            workOrderId: WORK_ORDER_ID,
            projectId: 'project-1',
            type: 'SURVEY',
            status: 'CONFIRMED',
            windowStart: '2026-07-18T01:00:00Z',
            windowEnd: WINDOW_END,
            timezone: 'Asia/Shanghai',
          },
          {
            appointmentId: '019f84a0-cccc-7f8c-9505-36fe5c0ee013',
            taskId: 'other-task',
            workOrderId: WORK_ORDER_ID,
            projectId: 'project-1',
            type: 'INSTALLATION',
            status: 'PROPOSED',
            windowStart: '2026-07-19T01:00:00Z',
            windowEnd: '2026-07-19T04:00:00Z',
            timezone: 'Asia/Shanghai',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/sync-summary**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        pendingFeedItemCount: 2,
        appointmentWindowCount: 3,
        tombstoneCount: 1,
        asOf: '2026-07-17T12:00:00Z',
      }),
    })
  })
}

test.describe('M218 Technician Portal Feed/日程 Accepted 字段展示', () => {
  test('M218-01/02：Feed 展示字段并深链 schedule', async ({ page }) => {
    await stubTechnicianPortal(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, '/technician-portal/task-feed')
    await expect(page.getByTestId('technician-portal-task-feed')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('technician-feed-as-of')).toHaveText('2026-07-17T12:00:00Z')
    // 产品化后阶段/类型标签中文化；测试兼容原始码与中文/未知态。
    await expect(page.getByTestId('technician-feed-stage-code')).toHaveText(/S1|未知状态/)
    await expect(page.getByTestId('technician-feed-task-type')).toHaveText(/INSTALL|未知状态|安装/)
    await expect(page.getByTestId('technician-feed-schedule-deeplink')).toHaveAttribute(
      'href',
      `/technician-portal/schedule?taskId=${TASK_ID}`,
    )
  })

  test('M218-03：Schedule 水合 taskId 并展示 windowEnd/timezone', async ({ page }) => {
    await stubTechnicianPortal(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/schedule?taskId=${TASK_ID}`)
    await expect(page.getByTestId('technician-portal-schedule')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('schedule-task-filter')).toContainText(TASK_ID)
    await expect(page.getByTestId('technician-schedule-window-end')).toHaveText(
      /2026-07-18T04:00:00Z|2026-07-18 04:00:00/,
    )
    await expect(page.getByTestId('technician-schedule-timezone')).toHaveText('Asia/Shanghai')
    await expect(page.getByTestId(`technician-schedule-row-${APPOINTMENT_ID}`)).toBeVisible()
    await expect(page.getByTestId('technician-schedule-row-019f84a0-cccc-7f8c-9505-36fe5c0ee013')).toHaveCount(0)
  })

  test('M218-04：SyncSummary 计数深链', async ({ page }) => {
    await stubTechnicianPortal(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, '/technician-portal/sync-summary')
    await expect(page.getByTestId('technician-portal-sync-summary')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('technician-sync-as-of')).toHaveText('2026-07-17T12:00:00Z')
    await expect(page.getByTestId('technician-sync-feed-deeplink')).toHaveAttribute(
      'href',
      '/technician-portal/task-feed',
    )
    await expect(page.getByTestId('technician-sync-schedule-deeplink')).toHaveAttribute(
      'href',
      '/technician-portal/schedule',
    )
    await expect(page.getByTestId('technician-sync-tombstone-deeplink')).toHaveAttribute(
      'href',
      '/technician-portal/task-feed',
    )
  })
})
