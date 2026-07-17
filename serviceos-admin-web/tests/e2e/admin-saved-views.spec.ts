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

test.describe('M189 Admin 个人 SavedView', () => {
  test('登录 → 任务目录保存筛选 → 重载应用 → 删除', async ({ page }) => {
    await loginWithLocalKeycloak(page)

    const listPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/me/saved-views') &&
        response.url().includes('ADMIN.TASK.QUEUE'),
    )
    await page.goto('/tasks')
    await expect(page.getByTestId('saved-view-bar')).toBeVisible({ timeout: 15_000 })
    expect((await listPromise).status()).toBe(200)

    await page.getByLabel('task status filter').selectOption('READY')
    const viewName = `e2e-ready-${Date.now()}`
    await page.getByTestId('saved-view-name').fill(viewName)

    const createPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/me/saved-views'),
    )
    await page.getByTestId('saved-view-save').click()
    expect((await createPromise).status()).toBe(200)
    await expect(page.getByTestId('saved-view-message')).toContainText('已保存')

    // 清空筛选后重载，再应用视图应恢复 READY
    await page.getByLabel('task status filter').selectOption('')
    await page.reload()
    await expect(page.getByTestId('saved-view-picker')).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('saved-view-picker').selectOption({ label: viewName })
    await page.getByTestId('saved-view-apply').click()
    await expect(page.getByLabel('task status filter')).toHaveValue('READY')
    await expect(page.getByTestId('saved-view-message')).toContainText('已应用')

    const deletePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'DELETE' &&
        response.url().includes('/api/v1/me/saved-views/'),
    )
    await page.getByTestId('saved-view-delete').click()
    expect((await deletePromise).status()).toBe(204)
    await expect(page.getByTestId('saved-view-message')).toContainText('已删除')
  })
})
