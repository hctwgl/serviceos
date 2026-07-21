import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M389 工单详情统一履约工作区', () => {
  test('展示履约进度、审核记录与资料预览', async ({ page }) => {
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
    await expect(page.getByTestId('workspace-masked-customer-name')).toContainText('王*')
    await expect(page.getByTestId('workspace-masked-customer-phone')).toContainText('*******5678')
    await expect(page.getByTestId('workspace-masked-service-address')).toContainText('杭州市西湖区***')
    await expect(page.getByTestId('workspace-masked-customer-phone')).not.toContainText('138')

    await page.getByRole('tab', { name: '审核与整改' }).click()
    await expect(page.getByTestId('workspace-review-records')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('workspace-review-case')).toBeVisible()
    await expect(page.getByTestId('workspace-review-decision-row')).toContainText('已驳回')
    await expect(page.getByTestId('workspace-review-decision-row')).toContainText('内部')
    await expect(page.getByTestId('workspace-correction-resubmission-row')).toBeVisible()

    await page.getByRole('tab', { name: '表单资料' }).click()
    await expect(page.getByTestId('workspace-evidence-previews')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('workspace-evidence-preview-card')).toBeVisible()
    await expect(page.getByTestId('workspace-evidence-preview-image')).toBeVisible({
      timeout: 10_000,
    })
    await expect(page.getByTestId('workspace-evidence-preview-image')).toHaveAttribute(
      'src',
      /workspace-evidence-preview\.jpg/,
    )

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-workspace-evidence-preview-1440.png',
      fullPage: true,
    })
  })
})
