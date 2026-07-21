import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84a0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const APPOINTMENT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'

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

async function stubTaskDetail(page: Page) {
  await page.unroute('**/api/v1/technician/**').catch(() => undefined)
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [{ contextId: CONTEXT_ID, portal: 'TECHNICIAN', scopeRef: NETWORK_ID, version: 'ctx-v1' }],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        capabilityCodes: ['task.readAssigned', 'appointment.read', 'visit.read', 'form.read', 'evidence.read'],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        items: [
          {
            pageId: 'TECHNICIAN.TASK.LIST',
            routeKey: 'task-feed',
            title: '今日任务',
            order: 1,
            section: '任务',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
  await page.route(`**/api/v1/technician/me/tasks/${TASK_ID}/forms**`, async (route: Route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route(`**/api/v1/technician/me/tasks/${TASK_ID}/evidence-slots**`, async (route: Route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route(`**/api/v1/technician/me/tasks/${TASK_ID}/evidence-items**`, async (route: Route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route(`**/api/v1/technician/me/tasks/${TASK_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        taskId: TASK_ID,
        workOrderId: 'wo-1',
        projectId: 'project-1',
        taskType: 'INSTALL',
        taskKind: 'HUMAN',
        stageCode: 'S1',
        taskStatus: 'RUNNING',
        businessType: 'INSTALLATION',
        executionGuarded: false,
        resourceVersion: 3,
        asOf: '2026-07-20T04:00:00Z',
        appointments: [
          {
            appointmentId: APPOINTMENT_ID,
            type: 'SERVICE',
            status: 'CONFIRMED',
            windowStart: '2026-07-20T08:00:00Z',
            windowEnd: '2026-07-20T10:00:00Z',
            timezone: 'Asia/Shanghai',
          },
        ],
        formSubmissions: [],
        visits: [],
        contactAttempts: [],
      }),
    })
  })
}

test.describe('M394 Technician H5 任务详情作业闭环产品化', () => {
  test('展示步骤条、提交前检查与底部主操作', async ({ page }) => {
    await stubTaskDetail(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await expect(page.getByTestId('technician-portal-task-detail')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-work-steps')).toBeVisible()
    await expect(page.getByTestId('technician-work-step-checkin')).toHaveAttribute('data-status', 'current')
    await expect(page.getByTestId('technician-visit-check-in')).toBeVisible()
    await expect(page.getByTestId('technician-presubmit-checks')).toBeVisible()
    await expect(page.getByTestId('technician-presubmit-checkin')).toHaveAttribute('data-ok', 'false')
    await expect(page.getByTestId('technician-sticky-action')).toBeVisible()
    await expect(page.getByTestId('technician-sticky-action-button')).toContainText('到场签到')

    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/technician-task-detail-workloop-390.png',
      fullPage: true,
    })
  })
})
