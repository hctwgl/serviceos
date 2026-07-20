import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M398 Admin 项目管理与工作台母版产品化', () => {
  test('项目列表使用 SummaryStrip，新建进入专用流程', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/projects')
    await expect(page.getByTestId('project-directory-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '项目管理' })).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('project-directory-table')).toBeVisible()

    await page.getByTestId('project-directory-create').click()
    await expect(page.getByRole('heading', { name: '新建项目' })).toBeVisible()
    await expect(page.getByTestId('project-create-submit')).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-project-create-flow-1440.png',
      fullPage: true,
    })

    await page.getByRole('button', { name: '返回项目列表' }).click()
    await expect(page.getByTestId('project-directory-table')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-project-directory-product-1440.png',
      fullPage: true,
    })
  })

  test('运营工作台展示风险摘要 SummaryStrip', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/workbench')
    await expect(page.getByTestId('admin-workbench')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('workbench-summary')).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('workbench-primary')).toContainText('我的待办')

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-workbench-summary-1440.png',
      fullPage: true,
    })
  })
})
