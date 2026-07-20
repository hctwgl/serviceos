import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M383 履约配置可访问性', () => {
  test('履约列表页无严重 a11y 违规', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '工单类型与履约配置' })).toBeVisible({
      timeout: 20_000,
    })
    const results = await new AxeBuilder({ page }).analyze()
    const critical = results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  })
})
