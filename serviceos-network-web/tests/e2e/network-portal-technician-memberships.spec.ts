import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

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

const MEMBERSHIP_ID = '019f86d0-7777-7f8c-9505-36fe5c0e8801'
const PROFILE_ID = '019f86d0-5555-7f8c-9505-36fe5c0e8806'
const NETWORK_ID = '019f86d0-2222-7f8c-9505-36fe5c0e8803'
const PRINCIPAL_ID = '019f86d0-6666-7f8c-9505-36fe5c0e8807'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`

async function mockNetworkPortalMembershipApis(page: Page) {
  await page.route('**/api/v1/me/contexts', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
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
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            pageId: 'NETWORK.TECHNICIAN.LIST',
            routeKey: 'technicians',
            title: '本网点师傅',
            order: 30,
            section: '人员与能力',
            requiredCapabilities: ['technician.readOwnNetwork'],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            membershipId: MEMBERSHIP_ID,
            technicianProfileId: PROFILE_ID,
            principalId: PRINCIPAL_ID,
            displayName: '师傅甲',
            profileStatus: 'ACTIVE',
            membershipStatus: 'ACTIVE',
            validFrom: '2026-07-17T10:00:00Z',
            validTo: null,
            membershipVersion: 7,
            openTaskCount: 0,
            approvedQualificationCount: 0,
            pendingQualificationCount: 0,
            qualificationSummary: '无资质记录',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technician-memberships**', async (route: Route) => {
    if (route.request().method() !== 'GET') {
      await route.continue()
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
            id: MEMBERSHIP_ID,
            serviceNetworkId: NETWORK_ID,
            technicianProfileId: PROFILE_ID,
            status: 'ACTIVE',
            validFrom: '2026-07-17T10:00:00Z',
            validTo: null,
            version: 7,
            createdAt: '2026-07-17T10:00:00Z',
            terminatedAt: null,
            terminateReason: null,
          },
        ],
      }),
    })
  })
}

test.describe('M206 Network Portal 师傅关系只读列表', () => {
  test('M206-09：终止表单版本来自 memberships 列表，非硬编码 1', async ({ page }) => {
    await mockNetworkPortalMembershipApis(page)
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/technicians')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-technicians')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('fill-terminate-from-row').first()).toBeVisible()

    // 默认版本输入不得再硬编码为 1
    await expect(page.getByTestId('terminate-membership-version')).toHaveValue('')

    await page.getByTestId('fill-terminate-from-row').first().click()
    await expect(page.getByTestId('terminate-membership-id')).toHaveValue(MEMBERSHIP_ID)
    await expect(page.getByTestId('terminate-membership-version')).toHaveValue('7')
    await expect(page.getByTestId('terminate-membership-version')).not.toHaveValue('1')
  })
})
