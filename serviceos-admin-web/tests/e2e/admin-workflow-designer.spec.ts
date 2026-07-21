import { expect, test } from '@playwright/test'
import { mockProductizationApis, seedLocalSession } from './productization-fixtures'

test.describe('M386 工作流设计器', () => {
  test('产品页打开三栏结构且不展示定义 JSON 主编辑', async ({ page }) => {
    await seedLocalSession(page)
    await mockProductizationApis(page)

    await page.route('**/api/v1/configuration/drafts**', async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET' && url.includes('/configuration/drafts')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              draftId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
              assetType: 'WORKFLOW',
              assetKey: 'platform.workflow.home-charging',
              intendedSemanticVersion: '1.0.0',
              schemaVersion: '1.0.0',
              definitionJson: JSON.stringify({
                workflowKey: 'platform.workflow.home-charging',
                semanticVersion: '1.0.0',
                startNodeId: 'START',
                nodes: [
                  { nodeId: 'START', nodeType: 'START', name: '开始' },
                  {
                    nodeId: 'SURVEY',
                    nodeType: 'USER_TASK',
                    name: '上门勘测',
                    stageCode: 'SURVEY',
                    taskType: 'SURVEY',
                  },
                  { nodeId: 'END', nodeType: 'END', name: '完成' },
                ],
                transitions: [
                  { transitionId: 't1', from: 'START', to: 'SURVEY' },
                  { transitionId: 't2', from: 'SURVEY', to: 'END' },
                ],
                metadata: {
                  layout: {
                    START: { x: 40, y: 40 },
                    SURVEY: { x: 240, y: 40 },
                    END: { x: 440, y: 40 },
                  },
                },
              }),
              contentDigest: 'b'.repeat(64),
              status: 'DRAFT',
              baseVersionId: null,
              publishedVersionId: null,
              validationErrors: [],
              approvalRef: null,
              approvedBy: null,
              approvedAt: null,
              aggregateVersion: 1,
              createdBy: 'ops',
              updatedBy: 'ops',
              createdAt: '2026-07-20T00:00:00Z',
              updatedAt: '2026-07-20T00:00:00Z',
            },
          ]),
        })
        return
      }
      await route.fallback()
    })

    await page.goto('/configuration/workflows')
    await expect(page.getByRole('heading', { name: '工作流设计器' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByTestId('workflow-designer-page')).toBeVisible()
    await expect(page.getByTestId('workflow-draft-list')).toBeVisible()
    await expect(page.getByText('家充勘测安装流程')).toBeVisible()
    await expect(page.getByTestId('workflow-product-canvas')).toBeVisible()
    await expect(page.getByTestId('definition-json')).toHaveCount(0)
    await expect(page.getByText('UI_DATA_GAP')).toBeVisible()

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/workflow-designer-1440.png',
      fullPage: true,
    })
  })
})
