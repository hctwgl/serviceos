import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M397 Admin 用户管理母版产品化', () => {
  test('用户目录使用 ListPageLayout 与 SummaryStrip', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/users')
    await expect(page.getByTestId('user-directory-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toContainText('演示用户')
    await expect(page.getByTestId('user-directory-create-disabled')).toBeDisabled()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-directory-product-1440.png',
      fullPage: true,
    })
  })
})
