import { expect, test, type Page, type Route } from '@playwright/test'

const networkA = 'NETWORK|NETWORK|019f80a0-2222-7f8c-9505-36fe5c0e8802'
const networkB = 'NETWORK|NETWORK|019f80a0-3333-7f8c-9505-36fe5c0e8803'

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installOidcAndApi(page: Page) {
  const observedBusinessContexts: string[] = []
  await page.route('**/realms/serviceos/protocol/openid-connect/auth**', async (route) => {
    const authorize = new URL(route.request().url())
    const callback = new URL(authorize.searchParams.get('redirect_uri')!)
    callback.searchParams.set('code', 'network-e2e-code')
    callback.searchParams.set('state', authorize.searchParams.get('state')!)
    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: `<script>location.replace(${JSON.stringify(callback.toString())})</script>`,
    })
  })
  await page.route('**/realms/serviceos/protocol/openid-connect/token', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postData()).toContain('code_verifier=')
    await json(route, { access_token: 'network-e2e-access-token', expires_in: 300 })
  })
  await page.route('**/api/v1/me/contexts', async (route) => {
    expect(route.request().headers().authorization).toBe('Bearer network-e2e-access-token')
    expect(route.request().headers()['x-serviceos-client-kind']).toBe('NETWORK_WEB')
    expect(route.request().headers()['x-serviceos-client-version']).toBe('0.1.0-e2e.1')
    await json(route, { contextVersion: 'role-grant-v3:g7', contexts: [
      { contextId: 'ADMIN|TENANT|tenant-local', portal: 'ADMIN', scopeRef: 'tenant-local', version: 'role-grant-v3:g7' },
      { contextId: networkA, portal: 'NETWORK', scopeRef: '济南一网点', version: 'role-grant-v3:g7' },
      { contextId: networkB, portal: 'NETWORK', scopeRef: '青岛二网点', version: 'role-grant-v3:g7' },
    ] })
  })
  await page.route('**/api/v1/me/capabilities**', async (route) => {
    const contextId = new URL(route.request().url()).searchParams.get('contextId')
    expect([networkA, networkB]).toContain(contextId)
    await json(route, { portal: 'NETWORK', contextVersion: 'role-grant-v3:g7', capabilityCodes: ['networkTask.read'] })
  })
  await page.route('**/api/v1/me/navigation**', async (route) => {
    const url = new URL(route.request().url())
    expect(url.searchParams.get('expectedContextVersion')).toBe('role-grant-v3:g7')
    await json(route, { portal: 'NETWORK', contextVersion: 'role-grant-v3:g7', items: [
      { pageId: 'NETWORK.WORKBENCH', routeKey: 'workbench', title: '工作台', order: 10, section: '核心', requiredCapabilities: [] },
    ] })
  })
  await page.route('**/api/v1/network-portal/workbench', async (route) => {
    const context = route.request().headers()['x-network-context']
    observedBusinessContexts.push(context)
    const isA = context === networkA
    await json(route, {
      networkId: isA ? '019f80a0-2222-7f8c-9505-36fe5c0e8802' : '019f80a0-3333-7f8c-9505-36fe5c0e8803',
      activeWorkOrderCount: isA ? 7 : 2,
      activeTaskCount: isA ? 11 : 3,
      activeTechnicianCount: isA ? 5 : 1,
      unassignedTechnicianTaskCount: 0,
      openCorrectionCaseCount: 0,
      openOperationalExceptionCount: 0,
      pendingQualificationCount: 0,
      capacity: [],
      asOf: '2026-07-18T08:00:00Z',
    })
  })
  return observedBusinessContexts
}

test('独立 OIDC 会话、客户端元数据与跨网点切换失败关闭', async ({ page }) => {
  const observedBusinessContexts = await installOidcAndApi(page)
  await page.goto('/network-portal/workbench')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()

  await expect(page).toHaveURL(/\/network-portal\/workbench$/)
  await expect(page.locator('#network-context')).toHaveValue(networkA)
  await expect(page.locator('#network-context')).toContainText('济南一网点')
  await expect(page.getByTestId('workbench-active-work-orders')).toContainText('7')
  expect(await page.evaluate(() => Object.keys(localStorage))).not.toContainEqual(
    expect.stringMatching(/token/i),
  )

  await page.locator('#network-context').selectOption(networkB)
  await expect(page.getByTestId('workbench-active-work-orders')).toContainText('2')

  await page.locator('#network-context').evaluate((select: HTMLSelectElement) => {
    select.add(new Option('伪造网点', 'NETWORK|NETWORK|forged'))
    select.value = 'NETWORK|NETWORK|forged'
    select.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await expect(page.getByText('NETWORK 上下文不属于当前主体')).toBeVisible()
  expect(observedBusinessContexts).toEqual([networkA, networkB])
})
