import { expect, test, type Page } from '@playwright/test'

/**
 * M377：产品化视觉基线采集（1440×1024）。
 * 使用本地会话种子 + API mock；金标需人工审查后入库。
 */

async function seedLocalSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('serviceos.accessToken', 'visual-mock-token')
    localStorage.setItem('serviceos.accessTokenExpiresAt', String(Date.now() + 60 * 60 * 1000))
  })
}

async function fulfillJson(
  route: { fulfill: (r: { status: number; contentType: string; body: string }) => Promise<void> },
  body: unknown,
) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

async function mockShellApis(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/me/contexts')) {
      await fulfillJson(route, {
        contexts: [
          {
            contextId: 'ctx-admin',
            portal: 'ADMIN',
            personaType: 'INTERNAL_EMPLOYEE',
            scopeType: 'TENANT',
            scopeRef: 'tenant-demo',
            scopeSummary: { organizationIds: [], networkIds: [], projectIds: [] },
            version: '1',
          },
        ],
        contextVersion: '1',
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/me/navigation')) {
      await fulfillJson(route, {
        contextId: 'ctx-admin',
        portal: 'ADMIN',
        contextVersion: '1',
        navigationCatalogVersion: '1',
        items: [
          {
            pageId: 'ADMIN.WORKBENCH',
            routeKey: 'workbench',
            title: '工作台',
            order: 1,
            section: '工作台',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.WORKORDER.LIST',
            routeKey: 'work-orders',
            title: '工单中心',
            order: 2,
            section: '工单运营',
            requiredCapabilities: [],
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/recent-resources')) {
      await fulfillJson(route, { items: [], nextCursor: null })
      return
    }
    if (url.includes('/ui-preferences') || url.includes('/saved-views')) {
      await fulfillJson(route, { items: [], preferences: {} })
      return
    }
    if (url.match(/\/api\/v1\/me(\?|$)/)) {
      await fulfillJson(route, {
        principalId: 'p1',
        tenantId: 't1',
        displayName: '演示运营',
        personas: [],
        contextVersion: '1',
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/work-orders') && !url.includes('/work-orders/')) {
      await fulfillJson(route, {
        items: [
          {
            id: '11111111-1111-4111-8111-111111111111',
            projectId: '22222222-2222-4222-8222-222222222222',
            clientCode: 'GEELY',
            brandCode: 'GEELY',
            serviceProductCode: 'INSTALLATION',
            externalOrderCode: 'WO-DEMO-001',
            status: 'ACTIVE',
            receivedAt: '2026-07-20T03:00:00Z',
            version: 1,
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    await fulfillJson(route, {})
  })
}

test.describe('M377 Admin productization visual baselines', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  test('AppShell + 工单中心产品化截图', async ({ page }) => {
    await seedLocalSession(page)
    await mockShellApis(page)
    await page.goto('/work-orders')
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('scope-bar')).toBeVisible()
    await expect(page.getByRole('heading', { name: '工单中心' })).toBeVisible()
    await expect(page.getByText('吉利汽车')).toBeVisible()
    await expect(page.getByText(/11111111-1111-4111-8111-111111111111/)).toHaveCount(0)
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-work-order-directory-productized.png',
      fullPage: true,
    })
  })
})
