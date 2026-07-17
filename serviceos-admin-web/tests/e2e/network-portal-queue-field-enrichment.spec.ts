import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const CORRECTION_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'
const CORRECTION_TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee022'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const EXCEPTION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee004'
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const HANDLING_TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee032'
const QUAL_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ee005'
const MEMBERSHIP_ID = '019f84a0-ffff-7f8c-9505-36fe5c0ee006'

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

async function stubCommon(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            scopeSummary: {
              organizationIds: [],
              networkIds: [NETWORK_ID],
              projectIds: [],
            },
            version: '1',
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
            title: '整改',
            order: 1,
            section: '协作',
            requiredCapabilities: ['correction.read'],
          },
          {
            pageId: 'NETWORK.EXCEPTION.QUEUE',
            routeKey: 'exceptions',
            title: '异常',
            order: 2,
            section: '协作',
            requiredCapabilities: ['exception.read'],
          },
          {
            pageId: 'NETWORK.QUALIFICATION',
            routeKey: 'qualifications',
            title: '资质',
            order: 3,
            section: '师傅',
            requiredCapabilities: ['technician.readOwnNetwork'],
          },
          {
            pageId: 'NETWORK.TECHNICIAN.LIST',
            routeKey: 'technicians',
            title: '师傅',
            order: 4,
            section: '师傅',
            requiredCapabilities: ['technician.readOwnNetwork'],
          },
        ],
      }),
    })
  })
}

test.describe('M220 Network Portal 队列/列表 Accepted 字段展示', () => {
  test('M220-01/02：整改列表字段与 correctionTaskId 深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/correction-cases**', async (route: Route) => {
      if (route.request().url().includes(`/${CORRECTION_ID}`)) {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              correctionCaseId: CORRECTION_ID,
              projectId: 'project-1',
              taskId: TASK_ID,
              sourceReviewCaseId: 'review-1',
              sourceReviewDecisionId: 'decision-1',
              reasonCodes: ['MISSING_PHOTO'],
              correctionTaskId: CORRECTION_TASK_ID,
              status: 'OPEN',
              createdAt: '2026-07-17T10:00:00Z',
              latestResubmissionSnapshotId: null,
              closedAt: null,
              waivedAt: null,
              resubmissionCount: 2,
            },
          ],
        }),
      })
    })
    await page.goto('/network-portal/corrections')
    await expect(page.getByTestId('network-corrections-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('correction-project-id')).toHaveText('project-1')
    await expect(page.getByTestId('correction-resubmission-count')).toHaveText('2')
    await expect(page.getByTestId('correction-correction-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${CORRECTION_TASK_ID}`,
    )
  })

  test('M220-03/04：异常列表深链与详情 handlingTaskId', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/operational-exceptions**', async (route: Route) => {
      const url = route.request().url()
      if (url.includes(`/${EXCEPTION_ID}`)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            exceptionId: EXCEPTION_ID,
            status: 'OPEN',
            severity: 'HIGH',
            errorCode: 'DISPATCH_FAILED',
            category: 'DISPATCH',
            sourceType: 'SYSTEM',
            projectId: 'project-1',
            workOrderId: WORK_ORDER_ID,
            taskId: TASK_ID,
            handlingTaskId: HANDLING_TASK_ID,
            occurrenceCount: 3,
            openedAt: '2026-07-17T10:00:00Z',
            lastDetectedAt: '2026-07-17T11:00:00Z',
            resolvedAt: null,
            resolutionCode: null,
            allowedActions: [],
          }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              exceptionId: EXCEPTION_ID,
              status: 'OPEN',
              severity: 'HIGH',
              errorCode: 'DISPATCH_FAILED',
              category: 'DISPATCH',
              sourceType: 'SYSTEM',
              projectId: 'project-1',
              workOrderId: WORK_ORDER_ID,
              taskId: TASK_ID,
              handlingTaskId: HANDLING_TASK_ID,
              occurrenceCount: 3,
              openedAt: '2026-07-17T10:00:00Z',
              lastDetectedAt: '2026-07-17T11:00:00Z',
              resolvedAt: null,
              resolutionCode: null,
              allowedActions: [],
            },
          ],
        }),
      })
    })
    await page.goto('/network-portal/exceptions')
    await expect(page.getByTestId('network-exceptions-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('exception-source-category')).toContainText('DISPATCH')
    await expect(page.getByTestId('exception-work-order-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/work-orders/${WORK_ORDER_ID}`,
    )
    await expect(page.getByTestId('exception-handling-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${HANDLING_TASK_ID}`,
    )
    await page.goto(`/network-portal/exceptions/${EXCEPTION_ID}`)
    await expect(page.getByTestId('exception-detail-handling-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${HANDLING_TASK_ID}`,
    )
  })

  test('M220-05/06：资质 decided*/version 与师傅 principal/valid', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/technician-qualifications**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              id: QUAL_ID,
              technicianProfileId: 'tech-profile-a',
              qualificationCode: 'INSTALL_L1',
              status: 'PENDING',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              submittedBy: 'actor-1',
              submittedAt: '2026-07-17T09:00:00Z',
              decidedBy: null,
              decidedAt: null,
              decisionReason: null,
              version: 1,
            },
          ],
        }),
      })
    })
    await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              membershipId: MEMBERSHIP_ID,
              technicianProfileId: 'tech-profile-a',
              principalId: 'principal-a',
              displayName: '王师傅',
              profileStatus: 'ACTIVE',
              membershipStatus: 'ACTIVE',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              membershipVersion: 2,
            },
          ],
        }),
      })
    })
    await page.route('**/api/v1/network-portal/technician-memberships**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              id: MEMBERSHIP_ID,
              serviceNetworkId: NETWORK_ID,
              technicianProfileId: 'tech-profile-a',
              status: 'ACTIVE',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              version: 2,
              createdAt: '2026-01-01T00:00:00Z',
              terminatedAt: null,
              terminateReason: null,
            },
          ],
        }),
      })
    })
    await page.goto('/network-portal/qualifications')
    await expect(page.getByTestId('network-qualifications-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('qualification-version')).toHaveText('1')
    await expect(page.getByTestId('qualification-decided')).toContainText('—')
    await expect(page.getByRole('button', { name: /裁决|decide|approve/i })).toHaveCount(0)
    await page.goto('/network-portal/technicians')
    await expect(page.getByTestId('network-technicians-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('technician-principal-id')).toHaveText('principal-a')
    await expect(page.getByTestId('technician-valid-range')).toContainText('2026-01-01T00:00:00Z')
  })
})
