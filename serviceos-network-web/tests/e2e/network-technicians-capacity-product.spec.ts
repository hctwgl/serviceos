import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const MEMBERSHIP_ID = 'mem-aaaa-1111-4111-8111-aaaaaaaaaaaa'
const PROFILE_ID = 'tech-bbbb-2222-4222-8222-bbbbbbbbbbbb'

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
        capabilityCodes: [
          'technician.readOwnNetwork',
          'networkPortal.manageTechnician',
          'networkTask.read',
        ],
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
            pageId: 'NETWORK.TECHNICIAN.LIST',
            routeKey: 'technicians',
            title: '师傅',
            order: 20,
            section: '资源',
            requiredCapabilities: [],
          },
          {
            pageId: 'NETWORK.CAPACITY',
            routeKey: 'capacity',
            title: '产能',
            order: 21,
            section: '资源',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
}

test.describe('M396 Network 师傅与产能产品化', () => {
  test('师傅列表 SummaryStrip 与管理入口', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/network-portal/technician-memberships**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: MEMBERSHIP_ID,
              serviceNetworkId: NETWORK_ID,
              technicianProfileId: PROFILE_ID,
              status: 'ACTIVE',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              version: 3,
              createdAt: '2026-01-01T00:00:00Z',
              terminatedAt: null,
              terminateReason: null,
            },
          ],
          nextCursor: null,
          asOf: '2026-07-20T04:00:00Z',
        }),
      })
    })
    await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              membershipId: MEMBERSHIP_ID,
              technicianProfileId: PROFILE_ID,
              principalId: 'prin-1',
              displayName: '张师傅',
              profileStatus: 'ACTIVE',
              membershipStatus: 'ACTIVE',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              membershipVersion: 3,
              openTaskCount: 2,
              approvedQualificationCount: 1,
              pendingQualificationCount: 1,
              qualificationSummary: '已通过 1 项，待审 1 项',
            },
          ],
          nextCursor: null,
          asOf: '2026-07-20T04:00:00Z',
        }),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/technicians')
    await expect(page.getByTestId('network-portal-technicians')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technicians-summary-active')).toContainText('1')
    await expect(page.getByTestId('technicians-summary-open-tasks')).toContainText('2')
    await expect(page.getByTestId('technicians-summary-pending-qualifications')).toContainText('1')
    await expect(page.getByTestId('network-technicians-table')).toContainText('张师傅')
    await expect(page.getByTestId('technician-open-task-count')).toContainText('2')
    await expect(page.getByTestId('technician-qualification-summary')).toContainText(
      '已通过 1 项，待审 1 项',
    )
    await page.getByTestId('technicians-toggle-manage').click()
    await expect(page.getByTestId('network-manage-technician-forms')).toBeVisible()
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-technicians-product-1440.png',
      fullPage: true,
    })
  })

  test('产能页 SummaryStrip 与负载展示', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/network-portal/capacity**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              capacityCounterId: 'cap-1',
              businessType: 'INSTALLATION',
              maxUnits: 10,
              occupiedUnits: 7,
              availableUnits: 3,
              version: 2,
              updatedAt: '2026-07-20T04:00:00Z',
            },
          ],
          nextCursor: null,
          asOf: '2026-07-20T04:00:00Z',
        }),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/capacity')
    await expect(page.getByTestId('network-portal-capacity')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('capacity-summary-occupied')).toContainText('7')
    await expect(page.getByTestId('capacity-summary-available')).toContainText('3')
    await expect(page.getByTestId('capacity-as-of')).toContainText('2026-07-20T04:00:00Z')
    await expect(page.getByTestId('capacity-row-INSTALLATION')).toContainText('70%')
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-capacity-product-1440.png',
      fullPage: true,
    })
  })
})
