import { expect, test, type Page } from '@playwright/test'
import {
  TARGET_A,
  WORK_ORDER_ID,
  finalReviewFixture,
  mockFinalReviewApis,
  seedLocalSession,
  seedStaleDraft,
} from './final-review-fixtures'

/**
 * M360：终审工作台固定视口 8 态视觉基线。
 *
 * 状态清单（人工采信 + Playwright 截图门禁）：
 * 1. loading
 * 2. empty（无终审数据）
 * 3. error（加载失败）
 * 4. pending（主操作「提交终审」禁用）
 * 5. approved-ready（「审核通过」可点）
 * 6. rejected-ready（「驳回整改」可点）
 * 7. readonly（无 DECIDE 能力）
 * 8. conflict（409 版本冲突弹窗）
 *
 * 附加：stale-draft 警告条（计入 rejected-ready/pending 夹具可复现，独立截图归档）。
 */
test.describe('M360 final review 8-state visual baseline', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  async function shot(page: Page, name: string) {
    await page.screenshot({
      path: `tests/e2e/__screenshots__/final-review-${name}.png`,
      fullPage: true,
    })
  }

  test('1 loading：骨架屏可见', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page, { finalReviewDelayMs: 8_000 })
    const goto = page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('async-content-loading')).toBeVisible({ timeout: 5_000 })
    await shot(page, 'loading')
    await goto
  })

  test('2 empty：暂无终审数据', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page, {
      fixture: {
        data: null,
        meta: {
          asOf: '2026-07-19T03:00:00Z',
          projectionCheckpoint: 'final-review.v1:empty',
          freshnessStatus: 'FRESH',
          scopeVersion: 3,
          queryId: 'frq-empty',
        },
      },
    })
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('async-content-empty')).toBeVisible()
    await expect(page.getByText('暂无终审数据')).toBeVisible()
    await shot(page, 'empty')
  })

  test('3 error：加载失败关闭', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page, { finalReviewStatus: 500 })
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('async-content-error')).toBeVisible()
    await expect(page.getByText('终审工作区暂时不可用')).toBeVisible()
    await shot(page, 'error')
  })

  test('4 pending：提交终审禁用', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page)
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW&targetId=${TARGET_A}`)
    const workspace = page.getByTestId('final-review-workspace')
    await expect(workspace).toBeVisible({ timeout: 20_000 })
    await expect(workspace.getByText('审核案例待审')).toBeVisible({ timeout: 15_000 })
    const primary = page.getByTestId('allowed-action-button').first()
    await expect(primary).toContainText('提交终审')
    await expect(primary).toBeDisabled()
    await shot(page, 'pending')
  })

  test('5 approved-ready：审核通过可提交', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page)
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW&targetId=${TARGET_A}`)
    const workspace = page.getByTestId('final-review-workspace')
    await expect(workspace).toBeVisible({ timeout: 20_000 })
    await page.getByRole('radio', { name: '通过' }).check()
    await workspace.getByTestId('review-target-list').getByText('设备铭牌照片').click()
    await page.getByRole('radio', { name: '通过' }).check()
    const primary = page.getByTestId('allowed-action-button').first()
    await expect(primary).toContainText('审核通过')
    await expect(primary).toBeEnabled()
    await shot(page, 'approved-ready')
  })

  test('6 rejected-ready：驳回整改可提交', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page)
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW&targetId=${TARGET_A}`)
    const workspace = page.getByTestId('final-review-workspace')
    await expect(workspace).toBeVisible({ timeout: 20_000 })
    await page.getByRole('radio', { name: '通过' }).check()
    await workspace.getByTestId('review-target-list').getByText('设备铭牌照片').click()
    await page.getByRole('radio', { name: '驳回' }).check()
    await page.getByText('图片模糊').click()
    await page.getByPlaceholder('请填写整改要求').fill('请重新现场拍摄清晰铭牌。')
    const primary = page.getByTestId('allowed-action-button').first()
    await expect(primary).toContainText('驳回整改')
    await expect(primary).toBeEnabled()
    await shot(page, 'rejected-ready')
  })

  test('7 readonly：无 DECIDE 能力', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page, {
      fixture: finalReviewFixture({
        allowedActions: [
          { action: 'DECIDE', enabled: false, reason: '缺少 evidence.review 能力' },
          { action: 'PREVIEW_EVIDENCE', enabled: true, reason: null },
          { action: 'VIEW_ONLY', enabled: true, reason: null },
          { action: 'OPEN_CORRECTION', enabled: false, reason: null },
        ],
      }),
    })
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('缺少 evidence.review 能力')).toBeVisible()
    await expect(page.getByTestId('allowed-action-button').first()).toBeDisabled()
    await shot(page, 'readonly')
  })

  test('8 conflict：版本冲突弹窗', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page, { decideStatus: 409 })
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW&targetId=${TARGET_A}`)
    const workspace = page.getByTestId('final-review-workspace')
    await expect(workspace).toBeVisible({ timeout: 20_000 })
    await page.getByRole('radio', { name: '通过' }).check()
    await workspace.getByTestId('review-target-list').getByText('设备铭牌照片').click()
    await page.getByRole('radio', { name: '通过' }).check()
    await page.getByTestId('allowed-action-button').first().click()
    await page.getByRole('button', { name: '确认提交' }).click()
    // Ant Design Modal teleport 不保证 data-testid 落在可检索节点；以 dialog 角色为准。
    const dialog = page.getByRole('dialog', { name: '版本冲突' })
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await expect(dialog.getByText('审核案例版本冲突或已被他人处理')).toBeVisible()
    await shot(page, 'conflict')
  })

  test('附加 stale-draft：旧草稿警告', async ({ page }) => {
    await seedLocalSession(page)
    await seedStaleDraft(page, 0)
    await mockFinalReviewApis(page)
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByTestId('final-review-stale-draft')).toBeVisible()
    await shot(page, 'stale-draft')
  })
})
