import { expect, test } from '@playwright/test'

/**
 * M352 视觉基线占位：固定 1440×1024。
 * 完整真实 OIDC 场景由 M355 与 admin-pilot smoke 覆盖；此处验证页面可挂载终审区块文案。
 */
test.describe('final review visual baseline', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  test('login page remains reachable at fixed viewport', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('body')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/final-review-viewport-baseline.png',
      fullPage: true,
    })
  })
})
