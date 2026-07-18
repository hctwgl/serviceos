import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0e8801'
const APPOINTMENT_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0e8802'
const CONTACT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0e8803'
const PROJECT_ID = '019f84a0-dddd-7f8c-9505-36fe5c0e8804'
const WORK_ORDER_ID = '019f84a0-eeee-7f8c-9505-36fe5c0e8805'
const TECH_ID = '019f84a0-ffff-7f8c-9505-36fe5c0e8806'
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

async function stubHistoryResidual(page: Page) {
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
  await page.route('**/api/v1/me**', async (route: Route) => {
    if (route.request().url().includes('/me/')) {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        principalId: 'principal-network-1',
        displayName: '网点操作员',
        asOf: AS_OF,
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
            pageId: 'NETWORK.TASK.QUEUE',
            routeKey: 'tasks',
            title: '工单任务',
            order: 20,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
    const url = route.request().url()
    if (url.includes('/appointments') || url.includes('/contact-attempts')) {
      await route.fallback()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: AS_OF,
        items: [
          {
            taskId: TASK_ID,
            workOrderId: WORK_ORDER_ID,
            projectId: PROJECT_ID,
            businessType: 'INSTALLATION',
            status: 'READY',
            technicianId: TECH_ID,
            effectiveFrom: AS_OF,
          },
        ],
        nextCursor: null,
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ asOf: AS_OF, items: [], nextCursor: null }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/tasks/${TASK_ID}/appointments**`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            appointmentId: APPOINTMENT_ID,
            projectId: PROJECT_ID,
            workOrderId: WORK_ORDER_ID,
            taskId: TASK_ID,
            type: 'INSTALLATION',
            status: 'CONFIRMED',
            assignedNetworkId: NETWORK_ID,
            technicianId: TECH_ID,
            aggregateVersion: 2,
            currentRevisionNo: 2,
            createdBy: 'network-operator-1',
            createdAt: '2026-07-17T09:00:00Z',
            allowedActions: ['RESCHEDULE', 'CANCEL', 'MARK_NO_SHOW'],
            revisions: [
              {
                revisionId: '019f84a0-1111-7f8c-9505-36fe5c0e8807',
                revisionNo: 2,
                window: {
                  start: '2026-07-18T02:00:00Z',
                  end: '2026-07-18T04:00:00Z',
                  timezone: 'Asia/Shanghai',
                  estimatedDurationMinutes: 120,
                },
                addressRef: 'addr-must-not-render',
                addressVersion: 'v1',
                note: 'secret-note',
                confirmationChannel: 'NETWORK_PORTAL',
                confirmedPartyType: 'NETWORK_MEMBER',
                createdBy: 'network-operator-1',
              },
            ],
          },
        ]),
      })
    },
  )
  await page.route(
    `**/api/v1/network-portal/tasks/${TASK_ID}/contact-attempts**`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            contactAttemptId: CONTACT_ID,
            projectId: PROJECT_ID,
            workOrderId: WORK_ORDER_ID,
            taskId: TASK_ID,
            channel: 'PHONE',
            contactedPartyRef: 'party-must-not-drive-primary-label',
            resultCode: 'NO_ANSWER',
            actorId: 'network-operator-1',
            createdAt: '2026-07-17T08:05:01Z',
            startedAt: '2026-07-17T08:00:00Z',
            endedAt: '2026-07-17T08:05:00Z',
            nextContactAt: '2026-07-17T14:00:00Z',
            note: 'secret-note',
            recordingRef: 'rec-must-not-render',
          },
        ]),
      })
    },
  )
}

test.describe('M241 Network Portal 预约/联系历史残余 Accepted 字段展示', () => {
  test('M241-01：展示范围/时间/allowedActions 等既有非 PII 字段', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubHistoryResidual(page)
    await page.goto(`/network-portal/tasks?taskId=${TASK_ID}`)
    await expect(page.getByTestId('network-portal-shell')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-portal-tasks')).toBeVisible()

    const appointment = page.getByTestId(`appointment-history-${APPOINTMENT_ID}`)
    await expect(appointment).toBeVisible({ timeout: 15_000 })
    await expect(appointment.getByTestId('appointment-history-project')).toHaveText(PROJECT_ID)
    await expect(appointment.getByTestId('appointment-history-work-order')).toHaveText(
      WORK_ORDER_ID,
    )
    await expect(appointment.getByTestId('appointment-history-network')).toHaveText(NETWORK_ID)
    await expect(appointment.getByTestId('appointment-history-technician')).toHaveText(TECH_ID)
    await expect(appointment.getByTestId('appointment-history-created-at')).toHaveText(
      '2026-07-17T09:00:00Z',
    )
    await expect(appointment.getByTestId('appointment-history-allowed-actions')).toContainText(
      'RESCHEDULE',
    )
    await expect(appointment.getByTestId('appointment-history-window')).toContainText('120min')
    await expect(appointment.getByTestId('appointment-history-summary')).not.toContainText(
      'addr-must-not-render',
    )

    const contact = page.getByTestId(`contact-history-${CONTACT_ID}`)
    await expect(contact.getByTestId('contact-history-project')).toHaveText(PROJECT_ID)
    await expect(contact.getByTestId('contact-history-work-order')).toHaveText(WORK_ORDER_ID)
    await expect(contact.getByTestId('contact-history-created-at')).toHaveText(
      '2026-07-17T08:05:01Z',
    )
    await expect(contact.getByTestId('contact-history-summary')).not.toContainText(
      'party-must-not-drive-primary-label',
    )
    await expect(contact.getByTestId('contact-history-summary')).not.toContainText(
      'rec-must-not-render',
    )
  })
})
