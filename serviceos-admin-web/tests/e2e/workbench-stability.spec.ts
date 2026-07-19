import { expect, test } from '@playwright/test'

/**
 * M351：工作台路由不再白屏；未匹配路由进入中文 404，而不是空 RouterView。
 * 本用例不依赖登录态下的队列数据。
 */
test.describe('Admin workbench stability', () => {
  test('未注册深层路径显示中文 404 而不是白屏', async ({ page }) => {
    await page.goto('/this-route-does-not-exist-m351')
    await expect(page.getByTestId('page-state-notfound')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('页面不存在或功能已被调整')).toBeVisible()
    await expect(page.getByTestId('page-state-workbench')).toBeVisible()
  })

  test('工作台路由已注册（登录前也能命中壳层，不出现空白主内容）', async ({ page }) => {
    await page.goto('/workbench')
    // 未登录时可能跳转登录或显示空导航，但不应是完全空白 document
    const bodyText = await page.locator('body').innerText()
    expect(bodyText.trim().length).toBeGreaterThan(0)
    // 若已有会话，应看到工作台
    const workbench = page.getByTestId('admin-workbench')
    if (await workbench.count()) {
      await expect(workbench).toBeVisible()
      await expect(page.getByRole('heading', { name: '运营工作台' })).toBeVisible()
    }
  })
})
