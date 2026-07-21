import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M402–M405 Admin 用户登记到变更时间线', () => {
  test('用户目录与详情任职/登录/变更时间线产品化', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.goto('/users')
    await expect(page.getByTestId('user-directory-page')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toBeVisible()
    await expect(page.getByTestId('user-directory-table')).toContainText('演示用户')
    await expect(page.getByTestId('user-org-summary').first()).toHaveText('演示总部')
    await expect(page.getByTestId('user-role-summary').first()).toHaveText('OPS')
    await expect(page.getByTestId('user-last-login').first()).toContainText('2026-07-20')
    await expect(page.getByTestId('user-directory-create')).toBeEnabled()

    await page.getByTestId('user-directory-create').click()
    await expect(page.getByTestId('user-create-flow')).toBeVisible()
    await expect(page.getByTestId('user-create-display-name')).toBeVisible()
    await expect(page.getByTestId('user-create-submit')).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-create-flow-1440.png',
      fullPage: true,
    })

    await page.getByRole('button', { name: '返回用户列表' }).click()
    await expect(page.getByTestId('user-directory-table')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-directory-product-1440.png',
      fullPage: true,
    })

    await page.getByRole('link', { name: '打开' }).first().click()
    await expect(page.getByRole('tab', { name: '登录与安全' })).toBeVisible({ timeout: 20_000 })
    await page.getByRole('tab', { name: '登录与安全' }).click()
    await expect(page.getByTestId('section-recent-logins')).toBeVisible()
    await expect(page.getByTestId('user-recent-login-list')).toContainText('admin-web')
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-recent-logins-1440.png',
      fullPage: true,
    })

    await page.getByRole('tab', { name: '组织归属' }).click()
    await expect(page.getByTestId('section-org-memberships')).toBeVisible()
    await expect(page.getByTestId('user-org-membership-list')).toContainText('演示总部')
    await expect(page.getByTestId('user-org-membership-list')).toContainText('运营部')
    await expect(page.getByTestId('user-org-membership-create')).toBeVisible()
    await expect(page.getByTestId('user-org-membership-create-submit')).toBeVisible()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-org-memberships-1440.png',
      fullPage: true,
    })

    await page.getByRole('tab', { name: '变更记录' }).click()
    await expect(page.getByTestId('section-change-timeline')).toBeVisible()
    await expect(page.getByTestId('user-change-timeline')).toContainText('OIDC 登录成功')
    await expect(page.getByTestId('user-change-timeline')).toContainText('主体已登记')
    await expect(page.getByTestId('user-change-timeline')).toContainText('任职已创建')
    await expect(page.getByTestId('user-change-timeline')).toContainText('角色授权已批准')
    await expect(page.getByTestId('user-change-timeline')).toContainText('审批运营')
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/admin-user-change-timeline-1440.png',
      fullPage: true,
    })
  })
})
