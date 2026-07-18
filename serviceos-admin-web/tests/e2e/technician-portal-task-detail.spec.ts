import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84b0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84b0-bbbb-7f8c-9505-36fe5c0ee002'
const WORK_ORDER_ID = '019f84b0-aaaa-7f8c-9505-36fe5c0ee001'
const APPOINTMENT_ID = '019f84b0-cccc-7f8c-9505-36fe5c0ee003'

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

async function stubTechnicianContext(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        asOf: '2026-07-18T03:00:00Z',
        contexts: [{
          contextId: CONTEXT_ID,
          portal: 'TECHNICIAN',
          personaType: 'TECHNICIAN',
          scopeType: 'NETWORK',
          scopeRef: NETWORK_ID,
          scopeSummary: { organizationIds: [], networkIds: [NETWORK_ID], projectIds: [] },
          version: '1',
        }],
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
        asOf: '2026-07-18T03:00:00Z',
        items: [{
          pageId: 'TECHNICIAN.TASK.LIST',
          routeKey: 'task-feed',
          title: '任务 Feed',
          order: 1,
          section: '任务',
          requiredCapabilities: ['task.readAssigned'],
        }],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/task-feed**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-18T03:00:00Z',
        nextCursor: null,
        items: [{
          itemType: 'ASSIGNMENT',
          taskId: TASK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
          serviceAssignmentId: '019f84b0-eeee-7f8c-9505-36fe5c0ee005',
          taskAssignmentId: null,
          taskType: 'INSTALLATION',
          taskKind: 'HUMAN',
          stageCode: 'INSTALL',
          taskStatus: 'READY',
          businessType: 'INSTALLATION',
          effectiveFrom: '2026-07-18T02:00:00Z',
          cursor: 'cursor-1',
          invalidationReason: null,
        }],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/tasks/**', async (route: Route) => {
    expect(route.request().headers()['x-technician-context']).toBe(CONTEXT_ID)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        taskId: TASK_ID,
        workOrderId: WORK_ORDER_ID,
        projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
        serviceAssignmentId: '019f84b0-eeee-7f8c-9505-36fe5c0ee005',
        taskAssignmentId: null,
        taskType: 'INSTALLATION',
        taskKind: 'HUMAN',
        stageCode: 'INSTALL',
        taskStatus: 'READY',
        businessType: 'INSTALLATION',
        effectiveFrom: '2026-07-18T02:00:00Z',
        executionGuarded: false,
        resourceVersion: 3,
        appointments: [{
          appointmentId: APPOINTMENT_ID,
          taskId: TASK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
          type: 'INSTALLATION',
          status: 'CONFIRMED',
          windowStart: '2026-07-19T01:00:00Z',
          windowEnd: '2026-07-19T03:00:00Z',
          timezone: 'Asia/Shanghai',
        }],
        asOf: '2026-07-18T03:00:00Z',
      }),
    })
  })
}

test.describe('M243 Technician Portal 当前责任任务在线详情', () => {
  test('M243-01/02：Feed 深链详情，携带可信上下文且只展示非 PII 摘要', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubTechnicianContext(page)

    await page.goto('/technician-portal/task-feed')
    await expect(page.getByTestId('technician-feed-task-detail-deeplink')).toHaveAttribute(
      'href',
      `/technician-portal/tasks/${TASK_ID}`,
    )
    await page.getByTestId('technician-feed-task-detail-deeplink').click()

    await expect(page).toHaveURL(new RegExp(`/technician-portal/tasks/${TASK_ID}$`))
    await expect(page.getByTestId('technician-portal-task-detail')).toBeVisible()
    await expect(page.getByTestId('technician-task-detail-task-id')).toHaveText(TASK_ID)
    await expect(page.getByTestId('technician-task-detail-status')).toHaveText('READY')
    await expect(page.getByTestId('technician-task-detail-appointments')).toContainText('CONFIRMED')
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('不返回地址')
    await expect(page.getByTestId('technician-task-detail-schedule-link')).toHaveAttribute(
      'href',
      `/technician-portal/schedule?taskId=${TASK_ID}`,
    )
  })
})
