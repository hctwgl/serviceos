import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M414 Admin 主数据治理台', () => {
  test('车企品牌治理与行政区树可浏览', async ({ page }) => {
    await seedLocalSession(page)
    await page.addInitScript(() => {
      localStorage.setItem('serviceos.admin.activeContextId', 'ctx-admin')
    })
    await mockProductizationApis(page)

    await page.goto('/master-data')
    await expect(page.getByTestId('master-data-catalog-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '主数据治理' })).toBeVisible()
    await expect(page.getByTestId('client-directory-table')).toContainText('吉利汽车')
    await expect(page.getByTestId('client-create-form')).toBeVisible()

    await page.getByRole('button', { name: '管理品牌' }).click()
    await expect(page.getByTestId('brand-panel')).toBeVisible()
    await expect(page.getByTestId('brand-directory-table')).toContainText('几何')

    await page.getByRole('tab', { name: '行政区树' }).click()
    await expect(page.getByTestId('region-tree-panel')).toBeVisible()
    await expect(page.getByTestId('region-catalog-tree')).toContainText('广东省')

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-master-data-catalog-1440.png',
      fullPage: true,
    })
  })
})
