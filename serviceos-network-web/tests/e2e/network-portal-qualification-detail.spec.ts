import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const QUALIFICATION_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ea001'

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

async function stubNetworkQualificationDetail(page: Page) {
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
            pageId: 'NETWORK.QUALIFICATION',
            routeKey: 'technicians/qualifications',
            title: '资质与到期',
            order: 32,
            section: '人员与能力',
            requiredCapabilities: ['technician.readOwnNetwork'],
          },
        ],
      }),
    })
  })
  const item = {
    id: QUALIFICATION_ID,
    technicianProfileId: '019f84a0-bbbb-7f8c-9505-36fe5c0ea002',
    qualificationCode: 'HV_INSTALL',
    status: 'PENDING',
    validFrom: '2026-01-01T00:00:00Z',
    validTo: null,
    submittedBy: 'network-op-1',
    submittedAt: '2026-07-17T09:00:00Z',
    decidedBy: null,
    decidedAt: null,
    decisionReason: null,
    version: 1,
  }
  await page.route('**/api/v1/network-portal/technician-qualifications?**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [item],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technician-qualifications', async (route: Route) => {
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
        items: [item],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/technician-qualifications/${QUALIFICATION_ID}`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(item),
      })
    },
  )
}

test.describe('M211 Network Portal 资质详情', () => {
  test('M211-01/02/03：列表深链详情并强调无 decide', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkQualificationDetail(page)
    await navigateNetwork(page, '/network-portal/qualifications')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-qualifications-table')).toBeVisible()

    await page.getByTestId('qualification-case-deeplink').click()
    await expect(page).toHaveURL(new RegExp(`/network-portal/qualifications/${QUALIFICATION_ID}`))
    await expect(page.getByTestId('network-portal-qualification-detail')).toBeVisible()
    await expect(page.getByTestId('qualification-detail-status')).toHaveText('PENDING')
    await expect(page.getByTestId('qualification-detail-version')).toHaveText('1')
    await expect(page.getByTestId('qualification-detail-decided-by')).toHaveText('—')
    await expect(page.getByRole('button', { name: /批准|驳回|decide|approve|reject/i })).toHaveCount(0)
  })
})
