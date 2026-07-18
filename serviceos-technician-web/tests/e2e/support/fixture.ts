import { expect, test as base, type Page, type Route } from '@playwright/test'

const technicianContext = 'TECHNICIAN|NETWORK|019f90a0-2222-7f8c-9505-36fe5c0e8802'

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installTechnicianIdentity(page: Page) {
  await page.route('**/realms/serviceos/protocol/openid-connect/auth**', async (route) => {
    const authorize = new URL(route.request().url())
    const callback = new URL(authorize.searchParams.get('redirect_uri')!)
    callback.searchParams.set('code', 'technician-migration-code')
    callback.searchParams.set('state', authorize.searchParams.get('state')!)
    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: `<!doctype html><form id="login">
        <input name="username" autocomplete="username">
        <input name="password" type="password" autocomplete="current-password">
        <button type="submit">登录</button>
      </form><script>
        document.getElementById('login').addEventListener('submit', (event) => {
          event.preventDefault(); location.replace(${JSON.stringify(callback.toString())})
        })
      </script>`,
    })
  })
  await page.route('**/realms/serviceos/protocol/openid-connect/token', (route) =>
    json(route, { access_token: 'technician-migration-token', expires_in: 300 }))
  await page.route('**/api/v1/me/contexts**', (route) => json(route, {
    contextVersion: 'role-grant-v3:technician-migration',
    asOf: '2026-07-18T07:00:00Z',
    contexts: [{
      contextId: technicianContext,
      portal: 'TECHNICIAN',
      personaType: 'TECHNICIAN',
      scopeType: 'NETWORK',
      scopeRef: '迁移验收师傅',
      scopeSummary: { organizationIds: [], networkIds: ['network-a'], projectIds: [] },
      version: 'role-grant-v3:technician-migration',
    }],
  }))
  await page.route('**/api/v1/me/capabilities**', (route) => json(route, {
    contextId: technicianContext,
    portal: 'TECHNICIAN',
    contextVersion: 'role-grant-v3:technician-migration',
    capabilityCodes: ['task.readAssigned', 'appointment.read', 'visit.read', 'form.read'],
    asOf: '2026-07-18T07:00:00Z',
  }))
  await page.route('**/api/v1/me/navigation**', (route) => json(route, {
    contextId: technicianContext,
    portal: 'TECHNICIAN',
    contextVersion: 'role-grant-v3:technician-migration',
    navigationCatalogVersion: 'page-registry-v16',
    asOf: '2026-07-18T07:00:00Z',
    items: [
      ['TECHNICIAN.TASK.LIST', 'task-feed', '任务 Feed'],
      ['TECHNICIAN.SCHEDULE', 'schedule', '日程'],
      ['TECHNICIAN.SYNC.SUMMARY', 'sync-summary', '同步摘要'],
      ['TECHNICIAN.ME', 'me', '我的'],
    ].map(([pageId, routeKey, title], order) => ({ pageId, routeKey, title, order, section: '在线工作', requiredCapabilities: [] })),
  }))
  await page.route('**/api/v1/technician/me/**', (route) => route.fulfill({
    status: 503,
    contentType: 'application/problem+json',
    body: JSON.stringify({ title: '迁移规格未提供业务夹具', errorCode: 'E2E_FIXTURE_MISSING' }),
  }))
}

export const test = base.extend({
  page: async ({ page }, use) => {
    await installTechnicianIdentity(page)
    await use(page)
  },
})

/** 保持内存 Token 的同文档跳转；整页刷新必须重新 OIDC，测试不绕过该产品规则。 */
export async function navigateTechnician(page: Page, path: string) {
  await page.evaluate((target) => {
    window.history.pushState({}, '', target)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

export { expect }
export type { Page, Route }
