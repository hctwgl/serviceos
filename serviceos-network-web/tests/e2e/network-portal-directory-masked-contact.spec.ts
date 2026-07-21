import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2255-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0eg001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0eg002'

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

async function stubContexts(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: '2026-07-20T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            displayLabel: '测试网点脱敏',
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
        asOf: '2026-07-20T12:00:00Z',
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

test.describe('M428 Network Portal 目录页脱敏客户联系', () => {
  test('M428-01：工单目录展示脱敏客户/电话/地址', async ({ page }) => {
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
          asOf: '2026-07-20T12:00:00Z',
          items: [
            {
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskIds: [TASK_ID],
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-20T10:00:00Z',
              brandCode: 'BYD_OCEAN',
              serviceProductCode: 'HOME_CHARGING',
              provinceCode: '330000',
              cityCode: '330100',
              districtCode: '330106',
              receivedAt: '2026-07-20T02:00:00Z',
              maskedCustomerName: '王*',
              maskedCustomerPhone: '*******5678',
              maskedServiceAddress: '杭州市西湖区***',
            },
          ],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-masked-customer-name')).toContainText('王*')
    await expect(page.getByTestId('work-order-masked-customer-phone')).toContainText('*******5678')
    await expect(page.getByTestId('work-order-masked-service-address')).toContainText(
      '杭州市西湖区***',
    )
    await expect(page.getByTestId('work-order-masked-customer-phone')).not.toContainText('138')

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-directory-masked-contact-1440.png',
      fullPage: true,
    })
  })

  test('M428-02：任务目录展示所属工单脱敏客户联系', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubContexts(page)
    await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-20T12:00:00Z',
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
              effectiveFrom: '2026-07-20T10:00:00Z',
              serviceProductCode: 'HOME_CHARGING',
              provinceCode: '330000',
              cityCode: '330100',
              districtCode: '330106',
              receivedAt: '2026-07-20T02:00:00Z',
              maskedCustomerName: '王*',
              maskedCustomerPhone: '*******5678',
              maskedServiceAddress: '杭州市西湖区***',
            },
          ],
        }),
      })
    })
    await navigateNetwork(page, '/network-portal/tasks')
    await expect(page.getByTestId('network-tasks-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('task-masked-customer-name')).toContainText('王*')
    await expect(page.getByTestId('task-masked-customer-phone')).toContainText('*******5678')
    await expect(page.getByTestId('task-masked-service-address')).toContainText('杭州市西湖区***')
  })
})
