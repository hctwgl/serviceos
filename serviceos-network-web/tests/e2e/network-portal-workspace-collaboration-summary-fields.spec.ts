import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0f001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0f002'
const APPOINTMENT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0f003'
const CONTACT_ID = '019f84a0-dddd-7f8c-9505-36fe5c0f004'
const CORRECTION_ID = '019f84a0-eeee-7f8c-9505-36fe5c0f005'
const REVIEW_ID = '019f84a0-ffff-7f8c-9505-36fe5c0f006'
const EXCEPTION_ID = '019f84a0-1111-7f8c-9505-36fe5c0f007'
const TECH_ID = '019f84a0-2222-7f8c-9505-36fe5c0f008'
const PROJECT_ID = '019f84a0-3333-7f8c-9505-36fe5c0f009'
const CORRECTION_TASK_ID = '019f84a0-4444-7f8c-9505-36fe5c0f00a'
const HANDLING_TASK_ID = '019f84a0-5555-7f8c-9505-36fe5c0f00b'
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

async function stubWorkspaceCollaboration(page: Page) {
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
          technicianId: TECH_ID,
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
              technicianId: TECH_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          appointments: [
            {
              appointmentId: APPOINTMENT_ID,
              taskId: TASK_ID,
              type: 'INSTALLATION',
              status: 'CONFIRMED',
              assignedNetworkId: NETWORK_ID,
              technicianId: TECH_ID,
              currentRevisionNo: 2,
              windowStart: '2026-07-18T02:00:00Z',
              windowEnd: '2026-07-18T04:00:00Z',
              timezone: 'Asia/Shanghai',
              estimatedDurationMinutes: 120,
              aggregateVersion: 3,
              createdAt: '2026-07-17T09:00:00Z',
            },
          ],
          contactAttempts: [
            {
              contactAttemptId: CONTACT_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              workOrderId: WORK_ORDER_ID,
              channel: 'PHONE',
              startedAt: '2026-07-17T08:00:00Z',
              endedAt: '2026-07-17T08:05:00Z',
              resultCode: 'NO_ANSWER',
              nextContactAt: '2026-07-17T14:00:00Z',
              createdAt: '2026-07-17T08:05:01Z',
            },
          ],
          corrections: [
            {
              correctionCaseId: CORRECTION_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              sourceReviewCaseId: REVIEW_ID,
              sourceReviewDecisionId: '019f84a0-6666-7f8c-9505-36fe5c0f00c',
              reasonCodes: ['PHOTO_BLUR'],
              correctionTaskId: CORRECTION_TASK_ID,
              status: 'OPEN',
              createdAt: '2026-07-17T07:00:00Z',
              latestResubmissionSnapshotId: '019f84a0-7777-7f8c-9505-36fe5c0f00d',
              closedAt: null,
              waivedAt: null,
              resubmissions: [
                {
                  correctionResubmissionId: '019f84a0-8888-7f8c-9505-36fe5c0f00e',
                  resubmissionOrdinal: 1,
                  evidenceSetSnapshotId: '019f84a0-7777-7f8c-9505-36fe5c0f00d',
                  submittedAt: '2026-07-17T07:30:00Z',
                },
              ],
            },
          ],
          reviews: [
            {
              reviewCaseId: REVIEW_ID,
              taskId: TASK_ID,
              projectId: PROJECT_ID,
              evidenceSetSnapshotId: '019f84a0-9999-7f8c-9505-36fe5c0f00f',
              scopeType: 'TASK',
              origin: 'INTERNAL',
              policyVersion: '1.0.0',
              status: 'DECIDED',
              createdAt: '2026-07-17T06:00:00Z',
              decidedAt: '2026-07-17T06:30:00Z',
              sourceReviewCaseId: null,
              externalSubmissionRef: 'ext-1',
              callbackBatchRef: 'batch-1',
              mappingVersionId: 'map-1',
              reopenedFromReviewCaseId: null,
              reopenTriggerRef: null,
              decisions: [
                {
                  reviewDecisionId: '019f84a0-aaaa-7f8c-9505-36fe5c0f010',
                  decisionOrdinal: 1,
                  decision: 'REJECTED',
                  decisionSource: 'HUMAN',
                  reasonCodes: ['PHOTO_BLUR'],
                  decidedAt: '2026-07-17T06:30:00Z',
                },
              ],
            },
          ],
          exceptions: [
            {
              exceptionId: EXCEPTION_ID,
              projectId: PROJECT_ID,
              sourceType: 'OUTBOUND_DELIVERY',
              category: 'INTEGRATION',
              severity: 'HIGH',
              errorCode: 'ACK_UNKNOWN',
              status: 'OPEN',
              workOrderId: WORK_ORDER_ID,
              taskId: TASK_ID,
              handlingTaskId: HANDLING_TASK_ID,
              occurrenceCount: 2,
              openedAt: '2026-07-17T05:00:00Z',
              lastDetectedAt: '2026-07-17T05:30:00Z',
              resolvedAt: null,
              resolutionCode: null,
              allowedActions: [],
            },
          ],
          technicians: [
            {
              membershipId: '019f84a0-bbbb-7f8c-9505-36fe5c0f011',
              technicianProfileId: TECH_ID,
              principalId: 'principal-tech-a',
              displayName: '师傅甲',
              profileStatus: 'ACTIVE',
              membershipStatus: 'ACTIVE',
              validFrom: '2026-01-01T00:00:00Z',
              validTo: null,
              membershipVersion: 4,
              openTaskCount: 0,
              approvedQualificationCount: 0,
              pendingQualificationCount: 0,
              qualificationSummary: '无资质记录',
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

test.describe('M240 Network Portal 工作区协作摘要 Accepted 字段展示', () => {
  test('M240-01：展示预约/联系/整改/审核/异常/师傅既有非 PII 摘要字段', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceCollaboration(page)
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })

    const appointment = page.getByTestId(`workspace-related-appointment-${APPOINTMENT_ID}`)
    await expect(appointment.getByTestId('workspace-appointment-network')).toContainText(NETWORK_ID)
    await expect(appointment.getByTestId('workspace-appointment-technician')).toContainText(TECH_ID)
    await expect(appointment.getByTestId('workspace-appointment-created-at')).toContainText(
      '2026-07-17T09:00:00Z',
    )
    await expect(appointment).toContainText('v3')

    const contact = page.getByTestId(`workspace-related-contact-${CONTACT_ID}`)
    await expect(contact.getByTestId('workspace-contact-scope')).toContainText(PROJECT_ID)
    await expect(contact.getByTestId('workspace-contact-window')).toContainText(
      '2026-07-17T08:00:00Z → 2026-07-17T08:05:00Z',
    )
    await expect(contact.getByTestId('workspace-contact-next')).toContainText(
      '2026-07-17T14:00:00Z',
    )

    const correction = page.getByTestId(`workspace-related-correction-${CORRECTION_ID}`)
    await expect(correction.getByTestId('workspace-correction-project')).toContainText(PROJECT_ID)
    await expect(correction.getByTestId('workspace-correction-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${CORRECTION_TASK_ID}`,
    )
    await expect(correction.getByTestId('workspace-correction-latest-resubmission')).toContainText(
      '#1',
    )

    const review = page.getByTestId(`workspace-related-review-${REVIEW_ID}`)
    await expect(review.getByTestId('workspace-review-project')).toContainText('scope TASK')
    await expect(review.getByTestId('workspace-review-latest-decision')).toContainText('REJECTED')

    const exception = page.getByTestId(`workspace-related-exception-${EXCEPTION_ID}`)
    await expect(exception.getByTestId('workspace-exception-taxonomy')).toContainText('INTEGRATION')
    await expect(exception.getByTestId('workspace-exception-handling-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${HANDLING_TASK_ID}`,
    )
    await expect(exception.getByTestId('workspace-exception-counts')).toContainText('occurrences 2')

    const tech = page.getByTestId(`workspace-technician-${TECH_ID}`)
    await expect(tech.getByTestId('workspace-technician-principal')).toContainText(
      'principal-tech-a',
    )
    await expect(tech.getByTestId('workspace-technician-validity')).toContainText('v4')
  })
})
