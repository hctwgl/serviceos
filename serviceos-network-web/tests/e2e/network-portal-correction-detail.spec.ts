import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const CORRECTION_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0e8801'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0e8802'

async function loginWithLocalKeycloak(
  page: Page,
  username = 'developer',
  password = 'local-dev-change-me',
) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('input[type="submit"], button[type="submit"]').click()
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill(`${username}@serviceos.local`)
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }
  await expect(page).toHaveURL(/\/work-orders$/)
}

async function stubNetworkCorrectionDetail(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            displayLabel: '测试网点 A',
            contextVersion: 'ctx-v1',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextId: CONTEXT_ID,
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            pageId: 'NETWORK.CORRECTION.QUEUE',
            routeKey: 'corrections',
            title: '本网点整改',
            order: 27,
            section: '工单任务',
            requiredCapabilities: ['evidence.read'],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/correction-cases?**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            correctionCaseId: CORRECTION_ID,
            projectId: '019f84a0-cccc-7f8c-9505-36fe5c0e8803',
            taskId: TASK_ID,
            sourceReviewCaseId: '019f84a0-dddd-7f8c-9505-36fe5c0e8804',
            sourceReviewDecisionId: '019f84a0-eeee-7f8c-9505-36fe5c0e8805',
            reasonCodes: ['MISSING_PHOTO'],
            correctionTaskId: null,
            status: 'OPEN',
            createdAt: '2026-07-17T11:00:00Z',
            latestResubmissionSnapshotId: null,
            closedAt: null,
            waivedAt: null,
            resubmissionCount: 1,
          },
        ],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/correction-cases/${CORRECTION_ID}`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          correctionCaseId: CORRECTION_ID,
          projectId: '019f84a0-cccc-7f8c-9505-36fe5c0e8803',
          taskId: TASK_ID,
          sourceReviewCaseId: '019f84a0-dddd-7f8c-9505-36fe5c0e8804',
          sourceReviewDecisionId: '019f84a0-eeee-7f8c-9505-36fe5c0e8805',
          sourceEvidenceSetSnapshotId: '019f84a0-ffff-7f8c-9505-36fe5c0e8806',
          sourceSnapshotContentDigest: 'a'.repeat(64),
          reasonCodes: ['MISSING_PHOTO'],
          correctionTaskId: null,
          status: 'OPEN',
          createdBy: 'reviewer-1',
          createdAt: '2026-07-17T11:00:00Z',
          latestResubmissionSnapshotId: '019f84a0-1111-7f8c-9505-36fe5c0e8807',
          closedAt: null,
          waivedAt: null,
          resubmissions: [
            {
              correctionResubmissionId: '019f84a0-2222-7f8c-9505-36fe5c0e8808',
              correctionCaseId: CORRECTION_ID,
              resubmissionOrdinal: 1,
              evidenceSetSnapshotId: '019f84a0-1111-7f8c-9505-36fe5c0e8807',
              snapshotContentDigest: 'b'.repeat(64),
              submittedAt: '2026-07-17T11:30:00Z',
            },
          ],
        }),
      })
    },
  )
}

test.describe('M209 Network Portal 整改详情', () => {
  test('M209-01/02/03/05：列表深链详情并展示 CorrectionCase 只读字段', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubNetworkCorrectionDetail(page)
    await navigateNetwork(page, '/network-portal/corrections')
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-corrections-table')).toBeVisible()

    await page.getByTestId('correction-case-deeplink').click()
    await expect(page).toHaveURL(new RegExp(`/network-portal/corrections/${CORRECTION_ID}`))
    await expect(page.getByTestId('network-portal-correction-detail')).toBeVisible()
    await expect(page.getByTestId('correction-detail-status')).toHaveText(/待处理|OPEN/)
    await expect(page.getByTestId('correction-detail-source-snapshot')).toContainText(
      '019f84a0-ffff-7f8c-9505-36fe5c0e8806',
    )
    await expect(page.getByTestId('correction-resubmission-1')).toBeVisible()
    await expect(page.getByTestId('correction-detail-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
    await expect(page.getByRole('button', { name: /关闭|豁免|补传/ })).toHaveCount(0)
  })
})
