import { expect, test, type Page, type Route } from '@playwright/test'

const contextA = 'TECHNICIAN|NETWORK|019f90a0-2222-7f8c-9505-36fe5c0e8802'
const contextB = 'TECHNICIAN|NETWORK|019f90a0-3333-7f8c-9505-36fe5c0e8803'
const taskA = '019f90a0-aaaa-7f8c-9505-36fe5c0ee001'
const taskB = '019f90a0-bbbb-7f8c-9505-36fe5c0ee002'

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installOidcAndApi(page: Page) {
  const observedBusinessContexts: string[] = []
  await page.route('**/realms/serviceos/protocol/openid-connect/auth**', async (route) => {
    const authorize = new URL(route.request().url())
    const callback = new URL(authorize.searchParams.get('redirect_uri')!)
    callback.searchParams.set('code', 'technician-e2e-code')
    callback.searchParams.set('state', authorize.searchParams.get('state')!)
    await route.fulfill({ status: 200, contentType: 'text/html', body: `<script>location.replace(${JSON.stringify(callback.toString())})</script>` })
  })
  await page.route('**/realms/serviceos/protocol/openid-connect/token', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postData()).toContain('code_verifier=')
    await json(route, { access_token: 'technician-e2e-access-token', expires_in: 300 })
  })
  await page.route('**/api/v1/me/contexts', async (route) => {
    expect(route.request().headers().authorization).toBe('Bearer technician-e2e-access-token')
    expect(route.request().headers()['x-serviceos-client-kind']).toBe('TECHNICIAN_WEB')
    expect(route.request().headers()['x-serviceos-client-version']).toBe('0.1.0-e2e.1')
    await json(route, { contextVersion: 'role-grant-v3:h5', contexts: [
      { contextId: 'ADMIN|TENANT|tenant-local', portal: 'ADMIN', scopeRef: 'tenant-local', version: 'role-grant-v3:h5' },
      { contextId: contextA, portal: 'TECHNICIAN', scopeRef: '济南师傅上下文', version: 'role-grant-v3:h5' },
      { contextId: contextB, portal: 'TECHNICIAN', scopeRef: '青岛师傅上下文', version: 'role-grant-v3:h5' },
    ] })
  })
  await page.route('**/api/v1/me/capabilities**', async (route) => {
    const contextId = new URL(route.request().url()).searchParams.get('contextId')
    expect([contextA, contextB]).toContain(contextId)
    await json(route, { portal: 'TECHNICIAN', contextVersion: 'role-grant-v3:h5', capabilityCodes: ['task.readAssigned'] })
  })
  await page.route('**/api/v1/me/navigation**', async (route) => {
    const url = new URL(route.request().url())
    expect(url.searchParams.get('expectedContextVersion')).toBe('role-grant-v3:h5')
    await json(route, { portal: 'TECHNICIAN', contextVersion: 'role-grant-v3:h5', items: [
      { pageId: 'TECHNICIAN.TASK.LIST', routeKey: 'task-feed', title: '任务 Feed', order: 10, section: '任务', requiredCapabilities: [] },
    ] })
  })
  await page.route('**/api/v1/technician/me/task-feed**', async (route) => {
    const context = route.request().headers()['x-technician-context']
    observedBusinessContexts.push(context)
    const isA = context === contextA
    await json(route, {
      networkId: isA ? 'network-a' : 'network-b',
      nextCursor: null,
      asOf: '2026-07-18T08:00:00Z',
      items: [{
        itemType: isA ? 'TOMBSTONE' : 'ASSIGNMENT',
        taskId: isA ? taskA : taskB,
        workOrderId: isA ? null : 'work-order-b',
        projectId: isA ? null : 'project-b',
        serviceAssignmentId: null,
        taskAssignmentId: null,
        taskType: isA ? null : 'INSTALL',
        taskKind: isA ? null : 'HUMAN',
        stageCode: isA ? null : 'S1',
        taskStatus: isA ? null : 'READY',
        businessType: isA ? null : 'INSTALLATION',
        effectiveFrom: '2026-07-18T07:30:00Z',
        cursor: isA ? 'cursor-a' : 'cursor-b',
        invalidationReason: isA ? 'REASSIGNED' : null,
      }],
    })
  })
  return observedBusinessContexts
}

test('独立 PKCE、深链、tombstone、客户端元数据与上下文切换隔离', async ({ page }) => {
  const observedBusinessContexts = await installOidcAndApi(page)
  await page.goto('/technician-portal/task-feed')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()

  await expect(page).toHaveURL(/\/technician-portal\/task-feed$/)
  await expect(page.locator('#technician-context')).toHaveValue(contextA)
  await expect(page.getByTestId(`technician-feed-row-${taskA}`)).toContainText('REASSIGNED')
  await expect(page.getByTestId('technician-browser-boundary')).toContainText('不承诺原生级定位')
  expect(await page.evaluate(() => Object.keys(localStorage))).not.toContainEqual(expect.stringMatching(/token/i))

  await page.locator('#technician-context').selectOption(contextB)
  await expect(page.getByTestId(`technician-feed-row-${taskB}`)).toBeVisible()
  await expect(page.getByTestId(`technician-feed-row-${taskA}`)).toHaveCount(0)

  await page.locator('#technician-context').evaluate((select: HTMLSelectElement) => {
    select.add(new Option('伪造上下文', 'TECHNICIAN|NETWORK|forged'))
    select.value = 'TECHNICIAN|NETWORK|forged'
    select.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await expect(page.getByText('TECHNICIAN 上下文不属于当前主体')).toBeVisible()
  expect(observedBusinessContexts).toEqual([contextA, contextB])
})
