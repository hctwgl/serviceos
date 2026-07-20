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

test.describe('M191 Admin 共享 SavedView', () => {
  test('创建视图 → 共享 TENANT → 列表可见共享徽标 → 取消共享', async ({ page }) => {
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
    const viewName = `e2e-shared-${Date.now()}`

    const createPromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/me/saved-views'),
    )
    await page.getByTestId('saved-view-save').click()
    await expect(page.getByTestId('saved-view-name')).toBeVisible()
    await page.getByTestId('saved-view-name').fill(viewName)
    await page.getByRole('button', { name: '保存' }).click()
    expect((await createPromise).status()).toBe(200)
    await expect(page.getByTestId('saved-view-message')).toContainText('已保存')

    const sharePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/saved-views/') &&
        response.url().includes(':share'),
    )
    await page.getByTestId('saved-view-share').click()
    await expect(page.getByTestId('saved-view-share-panel')).toBeVisible()
    await page.getByTestId('saved-view-share-mode').getByText('租户').click()
    await page.getByRole('button', { name: '确认分享' }).click()
    expect((await sharePromise).status()).toBe(200)
    await expect(page.getByTestId('saved-view-message')).toContainText('已共享')
    await expect(page.getByTestId('saved-view-visibility-badge')).toContainText('租户共享')

    await page.getByTestId('saved-view-picker').click()
    await expect(page.getByRole('option', { name: new RegExp(viewName) })).toContainText('租户共享')
    await page.keyboard.press('Escape')

    const unsharePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/saved-views/') &&
        response.url().includes(':share'),
    )
    await page.getByTestId('saved-view-unshare').click()
    expect((await unsharePromise).status()).toBe(200)
    await expect(page.getByTestId('saved-view-message')).toContainText('已取消共享')
    await expect(page.getByTestId('saved-view-visibility-badge')).toContainText('私有')

    const deletePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'DELETE' &&
        response.url().includes('/api/v1/me/saved-views/'),
    )
    await page.getByTestId('saved-view-delete').click()
    expect((await deletePromise).status()).toBe(204)
  })
})
