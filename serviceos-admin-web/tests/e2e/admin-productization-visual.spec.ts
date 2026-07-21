import { expect, test, type Page } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

/**
 * M377：产品化视觉基线（1440×1024）。人工审查后入库；禁止抬高阈值。
 */

async function shot(page: Page, name: string) {
  await page.screenshot({
    path: `tests/e2e/__screenshots__/admin-${name}.png`,
    fullPage: true,
  })
}

test.describe('M377 Admin productization visual baselines', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  test('AppShell + 工单中心', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/work-orders')
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('scope-bar')).toBeVisible()
    await expect(page.getByRole('heading', { name: '工单中心' })).toBeVisible()
    await expect(page.getByText('吉利汽车')).toBeVisible()
    await expect(page.getByText(/11111111-1111-4111-8111-111111111111/)).toHaveCount(0)
    // M429：目录客户脱敏列
    await expect(page.getByTestId('work-order-masked-customer-name')).toContainText('王*')
    await expect(page.getByTestId('work-order-masked-customer-phone')).toContainText('*******5678')
    await expect(page.getByTestId('work-order-masked-service-address')).toContainText(
      '杭州市西湖区***',
    )
    await expect(page.getByTestId('work-order-masked-customer-phone')).not.toContainText('138')
    // M431：目录服务区域中文名（region-catalog）；未命中仍回退国标码
    await expect(page.getByTestId('work-order-region')).toContainText('浙江省/杭州市/西湖区', {
      timeout: 10_000,
    })
    // M432：目录当前阶段（currentStageCode → statusLabel）
    await expect(page.getByTestId('work-order-current-stage')).toContainText('勘测')
    // M433：目录当前责任人（Persona 显示名）
    await expect(page.getByTestId('work-order-current-assignee')).toContainText('演示师傅')
    // M439：目录网点/师傅列
    await expect(page.getByTestId('work-order-network-technician')).toContainText('杭州西湖网点')
    await expect(page.getByTestId('work-order-network-technician')).toContainText('现场师傅甲')
    // M434：目录 SLA 风险旁载
    await expect(page.getByTestId('work-order-sla-risk')).toContainText('开放 1 / 超时 0')
    // M435：目录独立 updatedAt（非 receivedAt MVP 映射）
    const updatedAtCell = page.getByTestId('work-order-updated-at')
    await expect(updatedAtCell).toContainText('2026-07-21')
    // M436：目录列表封顶总数
    await expect(page.getByTestId('list-toolbar-count')).toContainText('共 1 条')
    // M437：更多筛选中启用服务区域
    await page.getByRole('button', { name: '更多筛选' }).click()
    await expect(page.getByTestId('work-order-region-filter')).toBeVisible()
    // M438：更多筛选中启用当前阶段
    await expect(page.getByTestId('work-order-stage-filter')).toBeVisible()
    // M440：更多筛选中启用服务网点
    await expect(page.getByTestId('work-order-network-filter')).toBeVisible()
    await shot(page, 'work-order-directory-productized')
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-region-names-1440.png',
      fullPage: true,
    })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-current-stage-1440.png',
      fullPage: true,
    })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-current-assignee-1440.png',
      fullPage: true,
    })
    await page.getByTestId('work-order-sla-risk').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-sla-risk-1440.png',
      fullPage: true,
    })
    await updatedAtCell.scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-updated-at-1440.png',
      fullPage: true,
    })
    await page.getByTestId('list-toolbar-count').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-list-total-1440.png',
      fullPage: true,
    })
    await page.getByTestId('work-order-region-filter').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-region-filter-1440.png',
      fullPage: true,
    })
    await page.getByTestId('work-order-stage-filter').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-stage-filter-1440.png',
      fullPage: true,
    })
    await page.getByTestId('work-order-network-filter').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-network-filter-1440.png',
      fullPage: true,
    })
    await page.getByTestId('work-order-network-technician').scrollIntoViewIfNeeded()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/work-order-directory-network-technician-1440.png',
      fullPage: true,
    })
  })

  test('工单中心 Empty', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page, { workOrdersEmpty: true })
    await page.goto('/work-orders')
    await expect(page.getByRole('heading', { name: '工单中心' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText(/当前没有工单|暂无数据/)).toBeVisible()
    await shot(page, 'work-order-directory-empty')
  })

  test('工单中心 Error', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page, { workOrdersError: true })
    await page.goto('/work-orders')
    await expect(page.getByRole('heading', { name: '工单中心' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText(/问题编号：ERR-/)).toBeVisible()
    await shot(page, 'work-order-directory-error')
  })

  test('工单详情产品化（无 UUID 标题）', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/work-orders/11111111-1111-4111-8111-111111111111')
    await expect(page.getByRole('heading', { name: '工单详情' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('WO-DEMO-001')).toBeVisible()
    await expect(page.getByText('吉利汽车')).toBeVisible()
    // 标题区不直接展示完整 UUID
    await expect(page.locator('.page-container__title-area')).not.toContainText(
      '11111111-1111-4111-8111-111111111111',
    )
    await shot(page, 'work-order-detail-productized')
  })

  test('项目详情产品化', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222')
    await expect(page.getByRole('heading', { name: '项目详情' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('detail-summary').getByText('演示家充项目')).toBeVisible()
    await expect(page.getByRole('button', { name: '保存服务范围调整' })).toBeVisible()
    await expect(page.getByText('revise-scope-relations')).toHaveCount(0)
    await shot(page, 'project-detail-productized')
  })

  test('审核队列产品化壳', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/reviews')
    await expect(page.getByRole('heading', { name: '审核队列' })).toBeVisible({ timeout: 20_000 })
    await shot(page, 'review-queue-productized')
  })

  test('用户目录产品化壳', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/users')
    await expect(page.getByTestId('user-directory-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '用户目录' })).toBeVisible()
    await shot(page, 'user-directory-productized')
  })

  test('角色目录产品化壳', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/roles')
    await expect(page.getByRole('heading', { name: '角色与 Capability' })).toBeVisible({
      timeout: 20_000,
    })
    await shot(page, 'role-directory-productized')
  })

  test('工作台产品化', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/workbench')
    await expect(page.getByTestId('admin-workbench')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '运营工作台' })).toBeVisible()
    await shot(page, 'workbench-productized')
  })
})
