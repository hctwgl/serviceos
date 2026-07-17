import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const PRINCIPAL_ID = 'principal-tech-a'

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

async function stubTechnicianMe(page: Page) {
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
            pageId: 'TECHNICIAN.ME',
            routeKey: 'me',
            title: '我的',
            order: 40,
            section: '底部导航',
            requiredCapabilities: ['task.readAssigned'],
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
        contextId: CONTEXT_ID,
        portal: 'TECHNICIAN',
        capabilityCodes: ['task.readAssigned', 'appointment.read'],
        contextVersion: 'ctx-v1',
        asOf: '2026-07-17T12:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/me**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname.replace(/\/$/, '')
    if (path !== '/api/v1/me') {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        principalId: PRINCIPAL_ID,
        tenantId: 'tenant-a',
        displayName: '测试师傅',
        personas: [
          {
            id: 'persona-1',
            personaType: 'TECHNICIAN',
            status: 'ACTIVE',
            validFrom: '2026-01-01T00:00:00Z',
            validTo: null,
            version: 1,
          },
        ],
        contextVersion: 'ctx-v1',
        asOf: '2026-07-17T12:00:00Z',
      }),
    })
  })
}

test.describe('M219 Technician Portal TECHNICIAN.ME /me 页壳', () => {
  test('M219-01/02/03/04：导航进入 /me 并展示档案/上下文/能力', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubTechnicianMe(page)
    await page.goto('/technician-portal/me')
    await expect(page.getByTestId('technician-portal-me')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-portal-me')).toHaveAttribute(
      'data-page-id',
      'TECHNICIAN.ME',
    )
    await expect(page).toHaveURL(/\/technician-portal\/me$/)
    await expect(page.getByTestId('nav-technician-me')).toHaveAttribute(
      'href',
      '/technician-portal/me',
    )
    await expect(page.getByTestId('technician-me-principal-id')).toHaveText(PRINCIPAL_ID)
    await expect(page.getByTestId('technician-me-display-name')).toHaveText('测试师傅')
    await expect(page.getByTestId('technician-me-context-id')).toHaveText(CONTEXT_ID)
    await expect(page.getByTestId('technician-me-scope-ref')).toHaveText(NETWORK_ID)
    await expect(page.getByTestId('technician-me-capability-task.readAssigned')).toBeVisible()
    await expect(page.getByTestId('technician-me-capability-appointment.read')).toBeVisible()
  })
})
