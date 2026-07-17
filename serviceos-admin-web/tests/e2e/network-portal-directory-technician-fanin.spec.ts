import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const TECH_PROFILE_ID = 'tech-profile-a'
const MEMBERSHIP_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ee005'
const CORRECTION_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'
const CORRECTION_TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee022'
const EXCEPTION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee004'

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
            pageId: 'NETWORK.WORKORDER.LIST',
            routeKey: 'work-orders',
            title: '工单',
            order: 1,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.TASK.LIST',
            routeKey: 'tasks',
            title: '任务',
            order: 2,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.WORKBENCH',
            routeKey: 'workbench',
            title: '工作台',
            order: 3,
            section: '工作台',
            requiredCapabilities: ['networkTask.read'],
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
            technicianProfileId: TECH_PROFILE_ID,
            principalId: 'principal-a',
            displayName: '李师傅',
            profileStatus: 'ACTIVE',
            membershipStatus: 'ACTIVE',
            validFrom: '2026-01-01T00:00:00Z',
            validTo: null,
            membershipVersion: 1,
          },
        ],
      }),
    })
  })
}

test.describe('M217 Network Portal 目录页师傅 fan-in', () => {
  test('M217-01：工单目录展示师傅 displayName', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/work-orders?**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          items: [
            {
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskIds: [TASK_ID],
              businessType: 'INSTALLATION',
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    })
    await page.route('**/api/v1/network-portal/work-orders', async (route: Route) => {
      if (route.request().method() !== 'GET') {
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
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskIds: [TASK_ID],
              businessType: 'INSTALLATION',
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    })
    await page.goto('/network-portal/work-orders')
    await expect(page.getByTestId('network-work-orders-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('work-order-technician-label')).toHaveText('李师傅')
    await expect(page.getByTestId('work-order-project-id')).toHaveText('project-1')
  })

  test('M217-02：任务目录展示师傅名并深链工单工作区', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
      const url = route.request().url()
      if (url.includes('/appointments') || url.includes('/contact-attempts')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
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
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: 'project-1',
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
        }),
      })
    })
    await page.goto('/network-portal/tasks')
    await expect(page.getByTestId('network-tasks-table')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('task-technician-label')).toHaveText('李师傅')
    await expect(page.getByTestId('task-work-order-workspace-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/work-orders/${WORK_ORDER_ID}`,
    )
    await expect(page.getByTestId('task-stage-code')).toHaveText('S1')
  })

  test('M217-03：工作台 ACTIVE 基数深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route('**/api/v1/network-portal/workbench**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          asOf: '2026-07-17T12:00:00Z',
          activeWorkOrderCount: 2,
          activeTaskCount: 3,
          activeTechnicianCount: 1,
          capacity: [],
        }),
      })
    })
    await page.goto('/network-portal/workbench')
    await expect(page.getByTestId('network-workbench-counts')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('workbench-active-work-orders')).toHaveAttribute(
      'href',
      '/network-portal/work-orders',
    )
    await expect(page.getByTestId('workbench-active-tasks')).toHaveAttribute(
      'href',
      '/network-portal/tasks',
    )
    await expect(page.getByTestId('workbench-active-technicians')).toHaveAttribute(
      'href',
      '/network-portal/technicians',
    )
  })

  test('M217-04：整改 correctionTaskId 与异常 workOrderId 深链', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubCommon(page)
    await page.route(
      `**/api/v1/network-portal/correction-cases/${CORRECTION_ID}**`,
      async (route: Route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            correctionCaseId: CORRECTION_ID,
            projectId: 'project-1',
            taskId: TASK_ID,
            sourceReviewCaseId: 'review-1',
            sourceReviewDecisionId: 'decision-1',
            sourceEvidenceSetSnapshotId: 'snap-1',
            sourceSnapshotContentDigest: 'digest-1',
            reasonCodes: ['MISSING_PHOTO'],
            correctionTaskId: CORRECTION_TASK_ID,
            status: 'OPEN',
            createdBy: 'actor-1',
            createdAt: '2026-07-17T10:00:00Z',
            resubmissions: [],
          }),
        })
      },
    )
    await page.route(
      `**/api/v1/network-portal/operational-exceptions/${EXCEPTION_ID}**`,
      async (route: Route) => {
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
            handlingTaskId: null,
            occurrenceCount: 1,
            openedAt: '2026-07-17T10:00:00Z',
            lastDetectedAt: '2026-07-17T10:00:00Z',
            resolvedAt: null,
            resolutionCode: null,
            allowedActions: [],
          }),
        })
      },
    )
    await page.goto(`/network-portal/corrections/${CORRECTION_ID}`)
    await expect(page.getByTestId('correction-detail-correction-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${CORRECTION_TASK_ID}`,
    )
    await page.goto(`/network-portal/exceptions/${EXCEPTION_ID}`)
    await expect(page.getByTestId('exception-detail-work-order-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/work-orders/${WORK_ORDER_ID}`,
    )
  })
})
