import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M385 fulfillment visual evidence', () => {
  test('capture config center create wizard and publish flow screenshots', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible({
      timeout: 20_000,
    })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-config-center-1440.png',
      fullPage: true,
    })

    await page.setViewportSize({ width: 1280, height: 900 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-config-center-1280.png',
      fullPage: true,
    })

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.getByRole('button', { name: '新增工单类型' }).first().click()
    await expect(page.getByRole('heading', { name: '新增工单类型配置' })).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-create-wizard-1440.png',
      fullPage: true,
    })

    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/publish',
    )
    await expect(page.getByRole('heading', { name: '发布履约配置新版本' })).toBeVisible({
      timeout: 20_000,
    })
    await page.getByRole('button', { name: '下一步' }).click()
    await expect(page.getByTestId('fulfillment-runbook-table')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-publish-runbook-1440.png',
      fullPage: true,
    })
    await page.getByRole('button', { name: '下一步' }).click()
    await expect(page.getByTestId('fulfillment-compare-impact')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-publish-impact-1440.png',
      fullPage: true,
    })
    await page.getByRole('button', { name: '下一步' }).click()
    await expect(page.getByLabel('生效时间')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-publish-confirm-1440.png',
      fullPage: true,
    })
    await expect(page.getByRole('button', { name: '确认发布' })).toBeEnabled({ timeout: 10_000 })
    await page.getByRole('button', { name: '确认发布' }).click()
    await expect(page.getByText(/已发布版本/)).toBeVisible({ timeout: 10_000 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-publish-success-1440.png',
      fullPage: true,
    })
  })

  test('capture empty config center screenshot', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page, { fulfillmentEmpty: true })
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '项目履约配置中心' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByText(/还没有工单类型配置/)).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-config-center-empty-1440.png',
      fullPage: true,
    })
  })

  test('capture suspended readonly detail screenshot', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page, { fulfillmentSuspended: true })
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    )
    await expect(page.getByText('当前配置为只读状态')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('button', { name: '发布' })).toHaveCount(0)
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-detail-suspended-1440.png',
      fullPage: true,
    })
  })

  test('capture structured draft editor screenshot', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/edit',
    )
    await expect(page.getByRole('heading', { name: '履约配置编辑工作区' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByRole('button', { name: '1. 现场勘测' })).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/fulfillment-editor-structured-1440.png',
      fullPage: true,
    })
  })
})
