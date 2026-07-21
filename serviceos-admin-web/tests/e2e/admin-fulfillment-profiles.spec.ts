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
    await expect(page.getByRole('button', { name: '打开项目履约配置中心' })).toBeVisible()

    await page.getByRole('button', { name: '打开项目履约配置中心' }).click()
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '标准家充履约方案' })).toBeVisible()
    await expect(page.getByRole('cell', { name: '家充勘测安装' })).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('configuration-subnav')).toBeVisible()
    await expect(page.getByTestId('fulfillment-compare-impact')).toBeVisible()
    await expect(page.getByTestId('fulfillment-version-timeline')).toBeVisible()
    await expect(page.getByTestId('fulfillment-version-timeline')).toContainText('标准家充履约方案')
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

  test('M422 使用中工单摘要来自服务端计数', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByTestId('summary-strip-in-use')).toContainText('使用中工单')
    await expect(page.getByTestId('summary-strip-in-use')).toContainText('3')
    await expect(page.getByText('待服务端计数')).toHaveCount(0)
    await expect(page.getByText(/UI_DATA_GAP：跨模块工单计数/)).toHaveCount(0)
  })

  test('编辑器加载结构化草稿且不依赖 documentJson 主路径', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/edit',
    )
    await expect(page.getByRole('heading', { name: '履约配置编辑工作区' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByRole('button', { name: '1. 现场勘测' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '阶段规则' })).toBeVisible()
    await expect(page.getByRole('combobox').first()).toBeVisible()
    await expect(page.locator('pre')).toHaveCount(0)
    await expect(page.getByText('documentJson', { exact: true })).toHaveCount(0)
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
