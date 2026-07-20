import { expect, test } from '@playwright/test'
import {
  TARGET_A,
  WORK_ORDER_ID,
  finalReviewFixture,
  mockFinalReviewApis,
  seedLocalSession,
} from './final-review-fixtures'

test.describe('M352/M353 Final Review workspace (mocked API)', () => {
  test.use({ viewport: { width: 1440, height: 1024 } })

  test('终审工作台加载、禁用主操作、逐项决定与本地暂存', async ({ page }) => {
    await seedLocalSession(page)
    await mockFinalReviewApis(page)
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW&targetId=${TARGET_A}`)

    const workspace = page.getByTestId('final-review-workspace')
    await expect(workspace).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('tab', { name: '平台终审' })).toBeVisible()
    await expect(workspace.getByText('审核案例待审')).toBeVisible({ timeout: 15_000 })
    await expect(workspace.getByTestId('review-target-list')).toContainText('立柱安装照片')
    await expect(workspace.getByTestId('review-target-list')).toContainText('设备铭牌照片')
    await expect(workspace).not.toContainText(/\bAPPROVED\b/)
    await expect(workspace).not.toContainText(/\bINTERNAL\b/)

    const primary = page.getByTestId('allowed-action-button').first()
    await expect(primary).toContainText('提交终审')
    await expect(primary).toBeDisabled()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/final-review-pending.png',
      fullPage: true,
    })

    await page.getByRole('radio', { name: '通过' }).check()
    await workspace.getByTestId('review-target-list').getByText('设备铭牌照片').click()
    await page.getByRole('radio', { name: '驳回' }).check()
    await page.getByText('图片模糊').click()
    await page.getByPlaceholder('请填写整改要求').fill('请重新现场拍摄清晰铭牌。')

    await expect(page.getByTestId('draft-status')).toContainText('已暂存到当前浏览器')
    await expect(page.getByTestId('allowed-action-button').first()).toContainText('驳回整改')
    await expect(page.getByTestId('allowed-action-button').first()).toBeEnabled()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/final-review-rejected-ready.png',
      fullPage: true,
    })
  })

  test('只读无权限模式不能提交', async ({ page }) => {
    await seedLocalSession(page)
    const fixture = finalReviewFixture({
      allowedActions: [
        { action: 'DECIDE', enabled: false, reason: '缺少 evidence.review 能力' },
        { action: 'PREVIEW_EVIDENCE', enabled: true, reason: null },
        { action: 'VIEW_ONLY', enabled: true, reason: null },
        { action: 'OPEN_CORRECTION', enabled: false, reason: null },
      ],
    })
    await mockFinalReviewApis(page, { fixture })
    await page.goto(`/work-orders/${WORK_ORDER_ID}?tab=FINAL_REVIEW`)
    await expect(page.getByTestId('final-review-workspace')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText('缺少 evidence.review 能力')).toBeVisible()
    await expect(page.getByTestId('allowed-action-button').first()).toBeDisabled()
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/final-review-readonly.png',
      fullPage: true,
    })
  })
})
