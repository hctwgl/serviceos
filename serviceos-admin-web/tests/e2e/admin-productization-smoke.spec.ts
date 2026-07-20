import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

/**
 * M377：不依赖真实 Keycloak 的产品化冒烟（mock API）。
 * 真实 OIDC smoke 仍以 verify-admin-smoke.sh 为准；本环境 Docker 不可用时不得谎报。
 */
test.describe('M377 Admin productization mock smoke', () => {
  test('壳层导航 → 工单中心 → 工单详情 → 项目详情', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/workbench')
    await expect(page.getByTestId('admin-workbench')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('app-brand')).toContainText('ServiceOS')
    await expect(page.getByText('菜单可见性来自服务端')).toHaveCount(0)

    await page.getByRole('link', { name: '工单中心' }).click()
    await expect(page.getByRole('heading', { name: '工单中心' })).toBeVisible()
    await expect(page.getByText('WO-DEMO-001')).toBeVisible()
    await expect(page.getByText('吉利汽车')).toBeVisible()

    await page.getByRole('button', { name: '打开详情' }).click()
    await expect(page.getByRole('heading', { name: '工单详情' })).toBeVisible()
    await expect(page.getByText('返回工单中心')).toBeVisible()
    await expect(page.getByText('allowed-actions')).toHaveCount(0)

    await page.goto('/projects/22222222-2222-4222-8222-222222222222')
    await expect(page.getByRole('heading', { name: '项目详情' })).toBeVisible()
    await expect(page.getByRole('button', { name: '保存服务范围调整' })).toBeVisible()
    await expect(page.getByText('revise-scope-relations')).toHaveCount(0)
  })
})
