import { expect, test, type Page } from '@playwright/test'

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

test.describe('M192 Admin 受控全局搜索', () => {
  test('登录 → 搜索已知工单/网点 → 结果深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)

    // 从工单目录取一个可搜索的外部单号（本地 fixture / pilot 数据）
    const workOrdersPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/v1/work-orders' &&
        response.status() === 200,
    )
    await page.goto('/work-orders')
    const workOrdersResponse = await workOrdersPromise
    const workOrdersBody = (await workOrdersResponse.json()) as {
      items?: Array<{ id?: string; externalOrderCode?: string }>
    }
    const workOrder = workOrdersBody.items?.[0]
    expect(workOrder?.id).toBeTruthy()
    const searchTerm = workOrder?.externalOrderCode || workOrder!.id!

    const networksPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/service-networks') &&
        response.status() === 200,
    )
    await page.goto('/networks')
    const networksResponse = await networksPromise
    const networksBody = (await networksResponse.json()) as {
      items?: Array<{ id?: string; networkCode?: string; networkName?: string }>
    }
    const network = networksBody.items?.[0]
    const networkTerm = network?.networkCode || network?.networkName

    await page.goto('/search')
    await expect(page.getByTestId('admin-search-page')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('search-q').fill(searchTerm)
    const searchWoPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/search') &&
        response.url().includes(encodeURIComponent(searchTerm)),
    )
    await page.getByTestId('search-submit').click()
    expect((await searchWoPromise).status()).toBe(200)
    await expect(page.getByTestId('search-hit-WORK_ORDER').or(page.getByTestId('search-hit-EXTERNAL_ORDER')).first())
      .toBeVisible({ timeout: 10_000 })

    await page.getByTestId('search-hit-WORK_ORDER').or(page.getByTestId('search-hit-EXTERNAL_ORDER')).first().click()
    await expect(page).toHaveURL(new RegExp(`/work-orders/${workOrder!.id}`))

    if (networkTerm) {
      await page.goto('/search')
      await page.getByTestId('search-q').fill(networkTerm)
      const searchNetPromise = page.waitForResponse(
        (response) =>
          response.request().method() === 'GET' && response.url().includes('/api/v1/search'),
      )
      await page.getByTestId('search-submit').click()
      expect((await searchNetPromise).status()).toBe(200)
      await expect(page.getByTestId('search-hit-NETWORK').first()).toBeVisible({ timeout: 10_000 })
      await page.getByTestId('search-hit-NETWORK').first().click()
      await expect(page).toHaveURL(/\/networks\//)
    }
  })
})
