import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const CORRECTION_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0f101'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0f102'
const AS_OF = '2026-07-17T12:00:00Z'

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

async function stubWaivedCorrectionDetail(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: AS_OF,
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
        asOf: AS_OF,
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
  await page.route(
    `**/api/v1/network-portal/correction-cases/${CORRECTION_ID}`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          correctionCaseId: CORRECTION_ID,
          projectId: '019f84a0-cccc-7f8c-9505-36fe5c0f103',
          taskId: TASK_ID,
          sourceReviewCaseId: '019f84a0-dddd-7f8c-9505-36fe5c0f104',
          sourceReviewDecisionId: '019f84a0-eeee-7f8c-9505-36fe5c0f105',
          sourceEvidenceSetSnapshotId: '019f84a0-ffff-7f8c-9505-36fe5c0f106',
          sourceSnapshotContentDigest: 'a'.repeat(64),
          reasonCodes: ['MISSING_PHOTO'],
          correctionTaskId: null,
          status: 'WAIVED',
          createdBy: 'reviewer-1',
          createdAt: '2026-07-17T11:00:00Z',
          latestResubmissionSnapshotId: '019f84a0-1111-7f8c-9505-36fe5c0f107',
          closedBy: 'closer-1',
          closedAt: '2026-07-17T12:00:00Z',
          waivedBy: 'waver-1',
          waivedAt: '2026-07-17T12:00:00Z',
          waiveApprovalRef: 'APPROVAL-REF-42',
          waiveNote: '网点现场不可复拍，已批准豁免',
          resubmissions: [
            {
              correctionResubmissionId: '019f84a0-2222-7f8c-9505-36fe5c0f108',
              correctionCaseId: CORRECTION_ID,
              resubmissionOrdinal: 1,
              evidenceSetSnapshotId: '019f84a0-1111-7f8c-9505-36fe5c0f107',
              snapshotContentDigest: 'b'.repeat(64),
              submittedBy: 'network-operator-1',
              submittedAt: '2026-07-17T11:30:00Z',
            },
          ],
        }),
      })
    },
  )
}

test.describe('M242 Network Portal 整改详情残余 Accepted 字段展示', () => {
  test('M242-01：展示 closed/waived 操作者与补传 submittedBy', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWaivedCorrectionDetail(page)
    await navigateNetwork(page, `/network-portal/corrections/${CORRECTION_ID}`)
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-correction-detail')).toBeVisible()

    await expect(page.getByTestId('correction-detail-status')).toHaveText(/已豁免|WAIVED/)
    await expect(page.getByTestId('correction-detail-closed-by')).toHaveText('closer-1')
    await expect(page.getByTestId('correction-detail-closed-at')).toHaveText(
      '2026-07-17T12:00:00Z',
    )
    await expect(page.getByTestId('correction-detail-waived-by')).toHaveText('waver-1')
    await expect(page.getByTestId('correction-detail-waive-approval-ref')).toHaveText(
      'APPROVAL-REF-42',
    )
    await expect(page.getByTestId('correction-detail-waive-note')).toContainText('已批准豁免')
    await expect(page.getByTestId('correction-resubmission-1')).toBeVisible()
    await expect(page.getByTestId('correction-resubmission-submitted-by')).toHaveText(
      'network-operator-1',
    )
    await expect(page.getByRole('button', { name: /关闭|豁免|补传/ })).toHaveCount(0)
  })
})
