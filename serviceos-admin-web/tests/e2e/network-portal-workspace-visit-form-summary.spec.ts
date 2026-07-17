import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ec001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ec002'
const VISIT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ec003'
const SUBMISSION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ec004'

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

async function stubWorkspaceVisitForm(
  page: Page,
  options?: { includeVisits?: boolean; includeForms?: boolean },
) {
  const includeVisits = options?.includeVisits !== false
  const includeForms = options?.includeForms !== false
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
        ],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/work-orders/${WORK_ORDER_ID}/workspace`,
    async (route: Route) => {
      const body: Record<string, unknown> = {
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
      }
      if (includeVisits) {
        body.visits = [
          {
            visitId: VISIT_ID,
            taskId: TASK_ID,
            appointmentId: '019f84a0-eeee-7f8c-9505-36fe5c0ec005',
            visitSequence: 1,
            technicianId: 'tech-a',
            networkId: NETWORK_ID,
            status: 'IN_PROGRESS',
            checkInCapturedAt: '2026-07-17T11:00:00Z',
            checkInReceivedAt: '2026-07-17T11:00:01Z',
            geofenceResult: 'WITHIN_GEOFENCE',
            policyDecision: 'ACCEPTED',
            checkOutCapturedAt: null,
            checkOutReceivedAt: null,
            resultCode: null,
            exceptionCode: null,
            aggregateVersion: 1,
          },
        ]
      }
      if (includeForms) {
        body.formSubmissions = [
          {
            submissionId: SUBMISSION_ID,
            taskId: TASK_ID,
            projectId: '019f84a0-ffff-7f8c-9505-36fe5c0ec006',
            formVersionId: '019f84a0-1111-7f8c-9505-36fe5c0ec007',
            formKey: 'install.form',
            submissionVersion: 1,
            contentDigest: 'a'.repeat(64),
            validationStatus: 'VALIDATED',
            errorCount: 0,
            warningCount: 1,
            submittedAt: '2026-07-17T11:30:00Z',
          },
        ]
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
  for (const pattern of [
    '**/api/v1/network-portal/correction-cases**',
    '**/api/v1/network-portal/operational-exceptions**',
    '**/api/v1/network-portal/tasks/*/appointments**',
    '**/api/v1/network-portal/tasks/*/contact-attempts**',
    '**/api/v1/network-portal/technicians**',
  ]) {
    await page.route(pattern, async (route: Route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
      })
    })
  }
}

test.describe('M222 Network Portal 工作区 Visit/表单提交摘要', () => {
  test('M222-06a：展示 visits 与 formSubmissions 摘要', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceVisitForm(page)
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-visits')).toBeVisible()
    await expect(page.getByTestId(`workspace-visit-${VISIT_ID}`)).toContainText('IN_PROGRESS')
    await expect(page.getByTestId('workspace-form-submissions')).toBeVisible()
    await expect(page.getByTestId(`workspace-form-submission-${SUBMISSION_ID}`)).toContainText(
      'install.form',
    )
  })

  test('M222-06b：缺字段时省略区块（不得伪装为空列表）', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceVisitForm(page, { includeVisits: false, includeForms: false })
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-header-fields')).toBeVisible()
    await expect(page.getByTestId('workspace-visits')).toHaveCount(0)
    await expect(page.getByTestId('workspace-form-submissions')).toHaveCount(0)
  })
})
