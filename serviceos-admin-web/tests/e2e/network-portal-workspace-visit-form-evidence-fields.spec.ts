import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ef001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ef002'
const VISIT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ef003'
const SUBMISSION_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ef004'
const SLOT_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ef005'
const ITEM_ID = '019f84a0-ffff-7f8c-9505-36fe5c0ef006'
const APPOINTMENT_ID = '019f84a0-1111-7f8c-9505-36fe5c0ef007'
const PROJECT_ID = '019f84a0-2222-7f8c-9505-36fe5c0ef008'
const FORM_VERSION_ID = '019f84a0-3333-7f8c-9505-36fe5c0ef009'
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

async function stubWorkspaceFields(page: Page) {
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
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: PROJECT_ID,
          taskIds: [TASK_ID],
          businessType: 'INSTALLATION',
          technicianId: 'tech-a',
          effectiveFrom: '2026-07-17T10:00:00Z',
          asOf: AS_OF,
          tasks: [
            {
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: PROJECT_ID,
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: 'tech-a',
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          visits: [
            {
              visitId: VISIT_ID,
              taskId: TASK_ID,
              appointmentId: APPOINTMENT_ID,
              visitSequence: 1,
              technicianId: 'tech-a',
              networkId: NETWORK_ID,
              status: 'CHECKED_OUT',
              checkInCapturedAt: '2026-07-17T11:00:00Z',
              checkInReceivedAt: '2026-07-17T11:00:01Z',
              geofenceResult: 'WITHIN_GEOFENCE',
              policyDecision: 'ACCEPTED',
              checkOutCapturedAt: '2026-07-17T12:00:00Z',
              checkOutReceivedAt: '2026-07-17T12:00:02Z',
              resultCode: 'COMPLETED',
              exceptionCode: null,
              aggregateVersion: 2,
            },
          ],
          formSubmissions: [
            {
              submissionId: SUBMISSION_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              formVersionId: FORM_VERSION_ID,
              formKey: 'install.form',
              submissionVersion: 1,
              contentDigest: 'b'.repeat(64),
              validationStatus: 'VALIDATED',
              errorCount: 0,
              warningCount: 1,
              submittedAt: '2026-07-17T11:30:00Z',
            },
          ],
          evidenceSlots: [
            {
              slotId: SLOT_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              templateKey: 'survey.site',
              templateVersion: '1.0.0',
              requirementCode: 'site.photo',
              occurrenceKey: 'default',
              requirementName: '现场照片',
              mediaType: 'PHOTO',
              required: true,
              minCount: 1,
              maxCount: 2,
              status: 'SATISFIED',
              resolvedAt: '2026-07-17T10:05:00Z',
              slotGeneration: 1,
              active: true,
              transition: 'ACTIVATED',
              requiredDisposition: 'NONE',
            },
          ],
          evidenceItems: [
            {
              evidenceItemId: ITEM_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              evidenceSlotId: SLOT_ID,
              itemOrdinal: 1,
              status: 'VALIDATED',
              revisionCount: 1,
              latestRevisionNumber: 1,
              latestRevisionStatus: 'VALIDATED',
            },
          ],
        }),
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

test.describe('M239 Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示', () => {
  test('M239-01：展示 Visit/表单/Evidence 既有非 PII 摘要字段', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceFields(page)
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })

    const visit = page.getByTestId(`workspace-visit-${VISIT_ID}`)
    await expect(visit.getByTestId('workspace-visit-appointment')).toContainText(APPOINTMENT_ID)
    await expect(visit.getByTestId('workspace-visit-technician')).toContainText('tech-a')
    await expect(visit.getByTestId('workspace-visit-network')).toContainText(NETWORK_ID)
    await expect(visit.getByTestId('workspace-visit-checkin')).toContainText('2026-07-17T11:00:00Z')
    await expect(visit.getByTestId('workspace-visit-checkout')).toContainText('2026-07-17T12:00:00Z')
    await expect(visit.getByTestId('workspace-visit-result')).toContainText('COMPLETED')
    await expect(visit).toContainText('v2')

    const form = page.getByTestId(`workspace-form-submission-${SUBMISSION_ID}`)
    await expect(form.getByTestId('workspace-form-project')).toContainText(PROJECT_ID)
    await expect(form.getByTestId('workspace-form-version')).toContainText(FORM_VERSION_ID)
    await expect(form.getByTestId('workspace-form-submitted-at')).toContainText(
      '2026-07-17T11:30:00Z',
    )
    await expect(form.getByTestId('workspace-form-digest')).toContainText('b'.repeat(64))

    const slot = page.getByTestId(`workspace-evidence-slot-${SLOT_ID}`)
    await expect(slot.getByTestId('workspace-evidence-slot-template')).toContainText(
      'survey.site@1.0.0',
    )
    await expect(slot.getByTestId('workspace-evidence-slot-counts')).toContainText('min 1 / max 2')
    await expect(slot.getByTestId('workspace-evidence-slot-state')).toContainText('disposition NONE')
    await expect(slot.getByTestId('workspace-evidence-slot-resolved')).toContainText(
      'occurrence default',
    )

    const item = page.getByTestId(`workspace-evidence-item-${ITEM_ID}`)
    await expect(item).toContainText('#1')
    await expect(item.getByTestId('workspace-evidence-item-project')).toContainText(PROJECT_ID)
  })
})
