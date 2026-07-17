import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const MEMBERSHIP_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0eb001'
const PROFILE_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0eb002'

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

async function stubNetworkMembershipDetail(page: Page) {
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
        navigationCatalogVersion: 'page-registry-v15',
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
            principalId: 'principal-1',
            displayName: '张师傅',
            profileStatus: 'ACTIVE',
            membershipStatus: 'ACTIVE',
            validFrom: '2026-01-01T00:00:00Z',
            validTo: null,
            membershipVersion: 3,
          },
        ],
      }),
    })
  })
  const membership = {
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
  }
  await page.route('**/api/v1/network-portal/technician-memberships?**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [membership],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/technician-memberships/${MEMBERSHIP_ID}`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(membership),
      })
    },
  )
}

test.describe('M212 Network Portal 师傅关系详情', () => {
  test('M212-01/02：师傅列表深链详情并展示 version', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkMembershipDetail(page)
    await page.goto('/network-portal/technicians')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-technicians-table')).toBeVisible()

    await page.getByTestId('membership-case-deeplink').click()
    await expect(page).toHaveURL(
      new RegExp(`/network-portal/technicians/memberships/${MEMBERSHIP_ID}`),
    )
    await expect(page.getByTestId('network-portal-membership-detail')).toBeVisible()
    await expect(page.getByTestId('membership-detail-status')).toHaveText('ACTIVE')
    await expect(page.getByTestId('membership-detail-version')).toHaveText('3')
  })
})
