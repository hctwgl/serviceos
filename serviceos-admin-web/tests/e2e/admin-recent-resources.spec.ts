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

test.describe('M193 Admin 最近访问', () => {
  test('打开工单详情 → 侧栏最近访问出现该项', async ({ page }) => {
    await loginWithLocalKeycloak(page)

    const workOrdersPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/v1/work-orders' &&
        response.status() === 200,
    )
    await page.goto('/work-orders')
    const workOrdersBody = (await (await workOrdersPromise).json()) as {
      items?: Array<{ id?: string; externalOrderCode?: string }>
    }
    const workOrder = workOrdersBody.items?.[0]
    expect(workOrder?.id).toBeTruthy()

    const touchPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().includes('/api/v1/me/recent-resources'),
    )
    await page.goto(`/work-orders/${workOrder!.id}`)
    expect((await touchPromise).status()).toBe(200)

    const listPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/me/recent-resources') &&
        response.status() === 200,
    )
    await page.getByTestId('recent-refresh').click()
    await listPromise

    await expect(page.getByTestId('recent-resources')).toBeVisible()
    await expect(
      page.getByTestId(`recent-WORK_ORDER-${workOrder!.id}`),
    ).toBeVisible({ timeout: 10_000 })

    await page.getByTestId(`recent-WORK_ORDER-${workOrder!.id}`).click()
    await expect(page).toHaveURL(new RegExp(`/work-orders/${workOrder!.id}`))
  })
})
