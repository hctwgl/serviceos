import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M389 工单详情统一履约工作区', () => {
  test('展示履约进度、当前任务与决策上下文', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/work-orders/11111111-1111-4111-8111-111111111111')
    await expect(page.getByRole('heading', { name: /WO-DEMO-001|工单详情/ })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByTestId('work-order-fulfillment-workspace')).toBeVisible()
    await expect(page.getByTestId('business-progress')).toBeVisible()
    await expect(page.getByTestId('allowed-action-bar')).toBeVisible()
    await expect(page.getByTestId('work-order-context-rail')).toBeVisible()
    await expect(page.getByText('决策上下文')).toBeVisible()
    await expect(page.getByTestId('current-task-card')).toBeVisible()
    await expect(page.getByTestId('business-progress')).toContainText('当前')
    await expect(page.getByRole('tab', { name: '基本信息' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '表单资料' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '外部回传' })).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-workspace-product-1440.png',
      fullPage: true,
    })
  })
})
