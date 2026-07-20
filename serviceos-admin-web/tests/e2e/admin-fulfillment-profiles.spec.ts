import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M385 项目履约配置中心', () => {
  test('项目详情可进入履约配置中心母版', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/projects/22222222-2222-4222-8222-222222222222')
    await expect(page.getByRole('heading', { name: '项目详情' })).toBeVisible({
      timeout: 20_000,
    })

    await page.getByRole('tab', { name: '工单类型与履约配置' }).click()
    await expect(page.getByText('履约配置资产编辑请使用配置设计器')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '打开工单类型与履约配置' })).toBeVisible()

    await page.getByRole('button', { name: '打开工单类型与履约配置' }).click()
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible()
    await expect(page.getByText('标准家充履约方案')).toBeVisible()
    await expect(page.getByText('家充勘测安装')).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('configuration-subnav')).toBeVisible()
    await expect(page.getByTestId('fulfillment-compare-impact')).toBeVisible()
  })

  test('新增工单类型进入向导而非硬编码创建', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible({
      timeout: 20_000,
    })
    await page.getByRole('button', { name: '新增工单类型' }).first().click()
    await expect(page.getByRole('heading', { name: '新增工单类型配置' })).toBeVisible()
    await expect(page.getByText('选择工单类型')).toBeVisible()
  })

  test('列表主操作可键盘聚焦', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible({
      timeout: 20_000,
    })
    const viewButton = page.getByRole('button', { name: '查看' }).first()
    await viewButton.focus()
    await expect(viewButton).toBeFocused()
    await expect(page.getByRole('button', { name: '新增工单类型' }).first()).toBeVisible()
  })

  test('发布页展示运行说明书且不渲染 Manifest JSON', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/publish',
    )
    await expect(page.getByRole('heading', { name: '发布履约配置新版本' })).toBeVisible({
      timeout: 20_000,
    })
    await page.getByRole('button', { name: '下一步' }).click()
    await expect(page.getByTestId('fulfillment-runbook-table')).toBeVisible()
    await expect(page.getByText('现场勘测')).toBeVisible()
    await expect(page.locator('pre.manifest-preview')).toHaveCount(0)
    await expect(page.getByText(/"stageCode"/)).toHaveCount(0)
  })
})
