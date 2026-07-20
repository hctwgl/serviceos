import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M402 Admin 用户登记与目录摘要', () => {
  test('用户目录展示组织/角色摘要，并可进入新建流程', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/users')
    await expect(page.getByTestId('user-directory-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toContainText('演示用户')
    await expect(page.getByTestId('user-org-summary').first()).toHaveText('演示总部')
    await expect(page.getByTestId('user-role-summary').first()).toHaveText('OPS')
    await expect(page.getByTestId('user-directory-create')).toBeEnabled()

    await page.getByTestId('user-directory-create').click()
    await expect(page.getByTestId('user-create-flow')).toBeVisible()
    await expect(page.getByTestId('user-create-display-name')).toBeVisible()
    await expect(page.getByTestId('user-create-submit')).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-create-flow-1440.png',
      fullPage: true,
    })

    await page.getByRole('button', { name: '返回用户列表' }).click()
    await expect(page.getByTestId('user-directory-table')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-directory-product-1440.png',
      fullPage: true,
    })
  })
})
