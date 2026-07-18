import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const TECH_PROFILE_ID = 'tech-profile-a'
const MEMBERSHIP_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ee005'

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
  // 故意让 technicians 列表失败，证明目录展示不再依赖 M217 client fan-in
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
    })
  })
}

const technicianSummary = {
  membershipId: MEMBERSHIP_ID,
  technicianProfileId: TECH_PROFILE_ID,
  principalId: 'principal-a',
  displayName: '王师傅',
  profileStatus: 'ACTIVE',
  membershipStatus: 'ACTIVE',
  validFrom: '2026-01-01T00:00:00Z',
  validTo: null,
  membershipVersion: 1,
}

test.describe('M230 Network Portal 目录页师傅服务端摘要', () => {
  test('M230-05a：工单目录消费 page.technicians（不依赖 /technicians）', async ({ page }) => {
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
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          technicians: [technicianSummary],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-technician-label')).toHaveText('王师傅')
  })

  test('M230-05b：缺 technicians 字段时回退且不伪装', async ({ page }) => {
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
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    // /technicians 被 stub 为 403，故保留原始 ID
    await expect(page.getByTestId('work-order-technician-label')).toHaveText(TECH_PROFILE_ID)
  })

  test('M230-05c：任务目录消费 page.technicians', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubContexts(page)
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
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          technicians: [technicianSummary],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/tasks')
    await expect(page.getByTestId('network-tasks-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('task-technician-label')).toHaveText('王师傅')
  })
})
