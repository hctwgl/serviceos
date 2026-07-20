import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84a0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const CORRECTION_ID = 'corr-1111-4111-8111-111111111111'

async function loginWithLocalKeycloak(page: Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('input[type="submit"], button[type="submit"]').click()
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill('developer@serviceos.local')
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }
  await expect(page).toHaveURL(/\/work-orders$/)
}

async function stubIdentity(page: Page) {
  await page.unroute('**/api/v1/technician/**').catch(() => undefined)
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [{ contextId: CONTEXT_ID, portal: 'TECHNICIAN', scopeRef: NETWORK_ID, version: 'ctx-v1' }],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        capabilityCodes: ['task.readAssigned', 'evidence.read'],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        items: [
          {
            pageId: 'TECHNICIAN.SYNC.SUMMARY',
            routeKey: 'sync-summary',
            title: '同步',
            order: 3,
            section: '任务',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
}

test.describe('M395 Technician H5 整改与同步冲突中心产品化', () => {
  test('同步中心展示关注项、冲突指引与深链', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/technician/me/sync-summary**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          pendingFeedItemCount: 2,
          appointmentWindowCount: 1,
          tombstoneCount: 1,
          asOf: '2026-07-20T04:00:00Z',
        }),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, '/technician-portal/sync-summary')
    await expect(page.getByTestId('technician-portal-sync-summary')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-sync-attention')).toContainText('3')
    await expect(page.getByTestId('technician-sync-conflict-guidance')).toContainText('任务已改派')
    await expect(page.getByTestId('technician-sync-as-of')).toHaveText('2026-07-20T04:00:00Z')
    await expect(page.getByTestId('technician-sync-tombstone-deeplink')).toHaveAttribute(
      'href',
      '/technician-portal/task-feed',
    )
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/technician-sync-conflict-product-390.png',
      fullPage: true,
    })
  })

  test('整改详情展示步骤条与底部主操作', async ({ page }) => {
    await stubIdentity(page)
    await page.route('**/api/v1/technician/me/corrections**', async (route: Route) => {
      const url = route.request().url()
      if (url.includes('/evidence')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            correctionCaseId: CORRECTION_ID,
            sourceTaskId: 'source-task',
            correctionTaskId: 'corr-task',
            caseStatus: 'IN_PROGRESS',
            reasonCodes: ['MISSING_PHOTO'],
            taskStatus: 'READY',
            taskVersion: 1,
            latestResubmissionSnapshotId: null,
            resubmissionCount: 0,
          },
        ]),
      })
    })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/corrections/${CORRECTION_ID}`)
    await expect(page.getByTestId('technician-correction-detail')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-correction-steps')).toBeVisible()
    await expect(page.getByTestId('technician-correction-lifecycle')).toContainText('领取整改任务')
    await expect(page.getByTestId('technician-correction-sticky-button')).toContainText('领取整改任务')
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/technician-correction-product-390.png',
      fullPage: true,
    })
  })
})
