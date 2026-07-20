import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M379/M385 项目履约配置入口', () => {
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

  test('列表主操作进入独立新建向导', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.goto('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
    await expect(page.getByRole('heading', { name: '工单类型与履约配置' })).toBeVisible({
      timeout: 20_000,
    })
    const viewButton = page.getByRole('button', { name: '查看配置' })
    await viewButton.focus()
    await expect(viewButton).toBeFocused()

    await page.getByRole('button', { name: '新增工单类型' }).click()
    await expect(page).toHaveURL(/\/fulfillment-profiles\/new$/)
    await expect(page.getByRole('heading', { name: '新增工单类型配置' })).toBeVisible()
    await expect(page.getByText('选择工单类型', { exact: true }).first()).toBeVisible()
  })

  test('向导可选择工单类型和空白方案并创建草稿', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    let submittedBody: Record<string, unknown> | null = null
    await page.route(
      '**/api/v1/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles',
      async (route) => {
        if (route.request().method() !== 'POST') {
          await route.fallback()
          return
        }
        submittedBody = route.request().postDataJSON() as Record<string, unknown>
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          headers: { ETag: '"1"' },
          body: JSON.stringify({
            profileId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            projectId: '22222222-2222-4222-8222-222222222222',
            serviceProductCode: 'REPAIR',
            profileName: '维修履约方案',
            status: 'DRAFT',
            allowedActions: ['VIEW', 'EDIT_DRAFT'],
            aggregateVersion: 1,
            createdAt: '2026-07-20T05:00:00Z',
            updatedAt: '2026-07-20T05:00:00Z',
            asOf: '2026-07-20T05:00:00Z',
          }),
        })
      },
    )

    await page.goto(
      '/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles/new',
    )
    await expect(page.getByRole('heading', { name: '新增工单类型配置' })).toBeVisible({
      timeout: 20_000,
    })

    await page.getByRole('combobox').first().click()
    await page.getByText('维修服务', { exact: true }).click()
    await page.getByRole('button', { name: '下一步' }).click()

    await page.locator('input[placeholder="例如：山东家充勘测安装方案"]').fill('维修履约方案')
    await expect(page.getByRole('radio', { name: '从空白方案开始' })).toBeChecked()
    await page.getByRole('button', { name: '下一步' }).click()

    await expect(page.getByText('仅创建草稿，不立即生效')).toBeVisible()
    await page.getByRole('button', { name: '创建草稿并继续配置' }).click()

    await expect(page).toHaveURL(
      /\/fulfillment-profiles\/dddddddd-dddd-4ddd-8ddd-dddddddddddd\/edit$/,
    )
    expect(submittedBody).toMatchObject({
      serviceProductCode: 'REPAIR',
      profileName: '维修履约方案',
      templateCode: 'BLANK',
    })
  })
})
