import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M379/M383 项目履约配置入口', () => {
  test('项目详情不再显示履约空壳，并可进入履约列表', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/projects/22222222-2222-4222-8222-222222222222')
    await expect(page.getByRole('heading', { name: '项目详情' })).toBeVisible({
      timeout: 20_000,
    })

    await page.getByRole('tab', { name: '工单类型与履约配置' }).click()
    await expect(page.getByText('履约配置资产编辑请使用配置设计器')).toHaveCount(0)
    await expect(page.getByText('审核与 SLA 策略绑定未随项目详情返回')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '打开工单类型与履约配置' })).toBeVisible()

    await page.getByRole('button', { name: '打开工单类型与履约配置' }).click()
    await expect(page.getByRole('heading', { name: '工单类型与履约配置' })).toBeVisible()
    await expect(page.getByText('标准家充履约方案')).toBeVisible()
    await expect(page.getByText('家充勘测安装')).toBeVisible()
  })
})
