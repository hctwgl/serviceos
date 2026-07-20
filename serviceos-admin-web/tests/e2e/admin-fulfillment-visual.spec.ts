import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M385 fulfillment visual evidence', () => {
  test('capture config center and create wizard screenshots', async ({ page }) => {
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
  })
})
