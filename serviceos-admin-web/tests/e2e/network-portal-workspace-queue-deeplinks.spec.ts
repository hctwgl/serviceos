import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ed001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ed002'
const CORRECTION_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ed003'
const EXCEPTION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ed004'

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

async function stubPortal(page: Page) {
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
            pageId: 'NETWORK.WORKORDER.WORKSPACE',
            routeKey: 'work-order-workspace',
            title: '工单工作区',
            order: 16,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.TASK.QUEUE',
            routeKey: 'tasks',
            title: '工单任务',
            order: 20,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
          {
            pageId: 'NETWORK.CORRECTION.QUEUE',
            routeKey: 'corrections',
            title: '本网点整改',
            order: 27,
            section: '工单任务',
            requiredCapabilities: ['evidence.read'],
          },
          {
            pageId: 'NETWORK.EXCEPTION.QUEUE',
            routeKey: 'exceptions',
            title: '本网点异常',
            order: 26,
            section: '工单任务',
            requiredCapabilities: ['operations.exception.read'],
          },
        ],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/work-orders/${WORK_ORDER_ID}/workspace`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: null,
          taskIds: [TASK_ID],
          businessType: 'INSTALLATION',
          technicianId: 'tech-a',
          effectiveFrom: '2026-07-17T10:00:00Z',
          asOf: '2026-07-17T12:00:00Z',
          tasks: [
            {
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: null,
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          // M225：整改摘要由 workspace.corrections 交付（不再依赖列表 fan-in）
          corrections: [
            {
              correctionCaseId: CORRECTION_ID,
              taskId: TASK_ID,
              projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ed005',
              sourceReviewCaseId: '019f84a0-ffff-7f8c-9505-36fe5c0ed006',
              sourceReviewDecisionId: '019f84a0-1111-7f8c-9505-36fe5c0ed007',
              reasonCodes: ['MISSING_PHOTO'],
              correctionTaskId: null,
              status: 'OPEN',
              createdAt: '2026-07-17T11:00:00Z',
              latestResubmissionSnapshotId: null,
              closedAt: null,
              waivedAt: null,
              resubmissions: [],
            },
          ],
        }),
      })
    },
  )
  await page.route('**/api/v1/network-portal/correction-cases**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            correctionCaseId: CORRECTION_ID,
            projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ed005',
            taskId: TASK_ID,
            sourceReviewCaseId: '019f84a0-ffff-7f8c-9505-36fe5c0ed006',
            sourceReviewDecisionId: '019f84a0-1111-7f8c-9505-36fe5c0ed007',
            reasonCodes: ['MISSING_PHOTO'],
            correctionTaskId: null,
            status: 'OPEN',
            createdAt: '2026-07-17T11:00:00Z',
            latestResubmissionSnapshotId: null,
            closedAt: null,
            waivedAt: null,
            resubmissionCount: 0,
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/operational-exceptions**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            exceptionId: EXCEPTION_ID,
            projectId: null,
            sourceType: 'OUTBOUND_DELIVERY',
            category: 'INTEGRATION',
            severity: 'P2',
            errorCode: 'OUTBOUND_UNKNOWN',
            status: 'OPEN',
            workOrderId: WORK_ORDER_ID,
            taskId: TASK_ID,
            handlingTaskId: null,
            occurrenceCount: 1,
            openedAt: '2026-07-17T11:00:00Z',
            lastDetectedAt: '2026-07-17T11:00:00Z',
            resolvedAt: null,
            resolutionCode: null,
            allowedActions: [],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
    const url = route.request().url()
    if (url.includes('/appointments') || url.includes('/contact-attempts')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ networkId: NETWORK_ID, asOf: '2026-07-17T12:00:00Z', items: [] }),
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
            projectId: null,
            taskType: 'INSTALL',
            taskKind: 'HUMAN',
            stageCode: 'S1',
            status: 'READY',
            businessType: 'INSTALLATION',
            technicianId: 'tech-a',
            effectiveFrom: '2026-07-17T10:00:00Z',
          },
          {
            taskId: '019f84a0-9999-7f8c-9505-36fe5c0ed099',
            workOrderId: WORK_ORDER_ID,
            projectId: null,
            taskType: 'OTHER',
            taskKind: 'HUMAN',
            stageCode: 'S2',
            status: 'READY',
            businessType: 'INSTALLATION',
            technicianId: null,
            effectiveFrom: null,
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ networkId: NETWORK_ID, asOf: '2026-07-17T12:00:00Z', items: [] }),
    })
  })
}

test.describe('M214 Network Portal 工作区协作队列深链', () => {
  test('M214-01/02/03/04/05：工作区深链并水合 taskId；展示 related 摘要', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubPortal(page)
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })

    await expect(page.getByTestId('workspace-correction-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/corrections?taskId=${TASK_ID}`,
    )
    await expect(page.getByTestId('workspace-exception-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/exceptions?taskId=${TASK_ID}`,
    )
    await expect(page.getByTestId(`workspace-related-correction-${CORRECTION_ID}`)).toBeVisible()
    await expect(page.getByTestId(`workspace-related-exception-${EXCEPTION_ID}`)).toBeVisible()

    await page.getByTestId('workspace-task-deeplink').click()
    await expect(page).toHaveURL(new RegExp(`[?&]taskId=${TASK_ID}`))
    await expect(page.getByTestId('tasks-task-filter')).toContainText(TASK_ID)

    await page.goto(`/network-portal/corrections?taskId=${TASK_ID}`)
    await expect(page.getByTestId('corrections-task-filter')).toContainText(TASK_ID)
    await expect(page.getByTestId('network-corrections-table')).toBeVisible()

    await page.goto(`/network-portal/exceptions?taskId=${TASK_ID}`)
    await expect(page.getByTestId('exceptions-task-filter')).toContainText(TASK_ID)
    await expect(page.getByTestId('network-exceptions-table')).toBeVisible()
  })
})
