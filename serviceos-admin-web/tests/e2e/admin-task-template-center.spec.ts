import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M387 任务模板中心', () => {
  test('打开产品页并展示服务端投影模板', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)
    await page.route('**/api/v1/configuration/task-templates**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            templateKey: 'SURVEY',
            templateName: '上门勘测任务模板',
            taskTypeCode: 'SURVEY',
            category: 'SURVEY',
            categoryLabel: '勘测类',
            executionRoleLabel: '服务师傅',
            assignmentStrategyLabel: '待配置分配策略',
            formSummary: '已绑定表单 form.survey',
            evidenceSummary: '已绑定资料 ev.survey',
            slaSummary: '已绑定 SLA sla.survey',
            status: 'DRAFT',
            statusLabel: '草稿',
            referencedWorkflowCount: 1,
            referencedWorkflowNames: ['家充勘测安装流程'],
            lastUpdatedAt: '2026-07-20T04:00:00Z',
            gaps: ['分配策略与升级规则尚未形成独立任务模板资产'],
          },
          {
            templateKey: 'INSTALL',
            templateName: '上门安装任务模板',
            taskTypeCode: 'INSTALL',
            category: 'INSTALL',
            categoryLabel: '安装类',
            executionRoleLabel: '服务师傅',
            assignmentStrategyLabel: '待配置分配策略',
            formSummary: '已绑定表单 form.install',
            evidenceSummary: '未绑定资料要求',
            slaSummary: '未绑定 SLA',
            status: 'PUBLISHED',
            statusLabel: '已发布',
            referencedWorkflowCount: 1,
            referencedWorkflowNames: ['家充勘测安装流程'],
            lastUpdatedAt: '2026-07-20T04:00:00Z',
            gaps: ['分配策略与升级规则尚未形成独立任务模板资产'],
          },
        ]),
      })
    })

    await page.goto('/configuration/task-templates')
    await expect(page.getByRole('heading', { name: '任务模板中心' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByTestId('task-template-center')).toBeVisible()
    await expect(page.getByTestId('summary-strip')).toBeVisible()
    await expect(page.getByRole('cell', { name: '上门勘测任务模板' })).toBeVisible()
    await expect(page.getByText('家充勘测安装流程')).toBeVisible()
    await expect(page.getByText(/分配策略与升级规则尚未形成独立任务模板资产/)).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/task-template-center-1440.png',
      fullPage: true,
    })
  })
})
