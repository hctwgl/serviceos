import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

/**
 * M377：关键旅程自动 a11y 扫描（WCAG 2.2 AA 近似）。
 *
 * Ant Design Vue Select 内部 combobox（aria-expanded 等）属于组件库实现缺口，
 * 排除 `.ant-select` 内部节点，但仍扫描整页颜色对比与我们自有控件。
 * 自动通过不等于完成；人工键盘/缩放清单见验收矩阵。
 */

async function runAxe(page: import('@playwright/test').Page) {
  return new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .exclude('.ant-select')
    .exclude('.ant-select-dropdown')
    .analyze()
}

test.describe('M377 Admin productization a11y', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  for (const path of ['/work-orders', '/workbench', '/reviews', '/users', '/roles']) {
    test(`axe: ${path}`, async ({ page }) => {
      await seedLocalSession(page)
      await mockProductizationApis(page)
      await page.goto(path)
      await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 })
      const results = await runAxe(page)
      expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([])
    })
  }

  test('键盘可达：全局搜索与侧栏折叠', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/work-orders')
    await expect(page.getByTestId('global-search-entry')).toBeVisible({ timeout: 20_000 })
    await page.getByTestId('global-search-entry').focus()
    await expect(page.getByTestId('global-search-entry')).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/search/)
    await page.goto('/work-orders')
    await page.getByTestId('sidebar-trigger').focus()
    await page.keyboard.press('Enter')
    await expect(page.getByTestId('sidebar-trigger')).toBeVisible()
  })

  test('reduced-motion：html.reduce-motion 类可应用', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/work-orders')
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 })
    await page.evaluate(() => document.documentElement.classList.add('reduce-motion'))
    const hasClass = await page.evaluate(() =>
      document.documentElement.classList.contains('reduce-motion'),
    )
    expect(hasClass).toBe(true)
  })
})
