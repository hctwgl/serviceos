import { expect, test as base, type Page, type Route } from '@playwright/test'

const networkContext = 'NETWORK|NETWORK|019f80a0-2222-7f8c-9505-36fe5c0e8802'

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installNetworkIdentity(page: Page) {
  await page.route('**/realms/serviceos/protocol/openid-connect/auth**', async (route) => {
    const authorize = new URL(route.request().url())
    const callback = new URL(authorize.searchParams.get('redirect_uri')!)
    callback.searchParams.set('code', 'network-migration-code')
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
    json(route, { access_token: 'network-migration-token', expires_in: 300 }))
  await page.route('**/api/v1/me/contexts', (route) => json(route, {
    contextVersion: 'role-grant-v3:migration',
    contexts: [{ contextId: networkContext, portal: 'NETWORK', scopeRef: '迁移验收网点', version: 'role-grant-v3:migration' }],
  }))
  await page.route('**/api/v1/me/capabilities**', (route) => json(route, {
    portal: 'NETWORK', contextVersion: 'role-grant-v3:migration', capabilityCodes: [
      'networkTask.read', 'technician.readOwnNetwork', 'networkPortal.assignTechnician',
      'networkPortal.reassignTechnician', 'networkPortal.manageAppointment',
      'networkPortal.manageTechnician', 'evidence.read', 'evidence.submit',
      'appointment.read', 'appointment.propose', 'appointment.manage',
      'appointment.cancel', 'appointment.recordContact', 'sla.read',
      'operations.exception.read',
    ],
  }))
  await page.route('**/api/v1/me/navigation**', (route) => json(route, {
    portal: 'NETWORK', contextVersion: 'role-grant-v3:migration',
    items: [
      ['NETWORK.WORKBENCH', 'workbench', '工作台'],
      ['NETWORK.WORKORDER.LIST', 'work-orders', '工单'],
      ['NETWORK.TASK.QUEUE', 'tasks', '任务'],
      ['NETWORK.TECHNICIAN.LIST', 'technicians', '师傅'],
      ['NETWORK.QUALIFICATION', 'qualifications', '资质'],
      ['NETWORK.CORRECTION.QUEUE', 'corrections', '整改'],
      ['NETWORK.EXCEPTION.QUEUE', 'exceptions', '异常'],
      ['NETWORK.CAPACITY', 'capacity', '产能'],
    ].map(([pageId, routeKey, title], order) => ({ pageId, routeKey, title, order, section: 'Network', requiredCapabilities: [] })),
  }))
  // 旧规格未显式 stub 的业务请求稳定返回可行动错误；各规格后注册的精确 route 优先覆盖本兜底。
  await page.route('**/api/v1/network-portal/**', (route) => route.fulfill({
    status: 503,
    contentType: 'application/problem+json',
    body: JSON.stringify({ title: '迁移规格未提供业务夹具', errorCode: 'E2E_FIXTURE_MISSING' }),
  }))
}

export const test = base.extend({
  page: async ({ page }, use) => {
    await installNetworkIdentity(page)
    await use(page)
  },
})

/** 保持内存 Token 的同文档路由跳转；整页刷新按产品规则必须重新 OIDC，不在测试中绕过。 */
export async function navigateNetwork(page: Page, path: string) {
  await page.evaluate((target) => {
    window.history.pushState({}, '', target)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, path)
}

export { expect }
export type { Page, Route }
