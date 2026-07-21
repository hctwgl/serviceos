import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const TASK_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const TECH_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
const WO_ID = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const APPOINTMENT_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

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

async function stubAppointmentCalendar(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            scopeRef: NETWORK_ID,
            version: 'ctx-v1',
          },
        ],
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
        capabilityCodes: ['networkTask.read', 'networkPortal.manageAppointment', 'technician.readOwnNetwork'],
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
            pageId: 'NETWORK.APPOINTMENT',
            routeKey: 'appointments',
            title: '预约日历',
            order: 28,
            section: '工单任务',
            requiredCapabilities: ['networkPortal.manageAppointment'],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/appointment-calendar**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        timezone: 'Asia/Shanghai',
        rangeStart: '2026-07-21',
        rangeEnd: '2026-08-03',
        totalAppointmentCount: 1,
        truncated: false,
        days: Array.from({ length: 14 }, (_, index) => {
          const date = new Date(Date.UTC(2026, 6, 21 + index)).toISOString().slice(0, 10)
          if (index === 0) {
            return {
              date,
              appointmentCount: 1,
              items: [
                {
                  appointmentId: APPOINTMENT_ID,
                  taskId: TASK_ID,
                  workOrderId: WO_ID,
                  type: 'INSTALLATION',
                  status: 'CONFIRMED',
                  windowStart: '2026-07-21T02:00:00Z',
                  windowEnd: '2026-07-21T03:00:00Z',
                  timezone: 'Asia/Shanghai',
                  technicianId: TECH_ID,
                  technicianDisplayName: '张师傅',
                },
              ],
            }
          }
          return { date, appointmentCount: 0, items: [] }
        }),
        asOf: '2026-07-21T04:00:00Z',
      }),
    })
  })
}

test.describe('M413 网点预约日历产品化', () => {
  test('展示运营日条与当日预约明细', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1024 })
    await stubAppointmentCalendar(page)
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/appointments')

    await expect(page.getByTestId('network-portal-appointments')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-appointments')).toHaveAttribute(
      'data-page-id',
      'NETWORK.APPOINTMENT',
    )
    await expect(page.getByTestId('appointment-calendar-total')).toContainText('1')
    await expect(page.getByTestId('appointment-calendar-day-2026-07-21')).toContainText('1')
    await expect(page.getByTestId(`appointment-calendar-item-${APPOINTMENT_ID}`)).toContainText('张师傅')

    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-appointment-calendar-product-1440.png',
      fullPage: true,
    })
  })
})
