import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const APPOINTMENT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'
const CONTACT_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee004'

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

async function stubPortal(page: Page, options?: { denyAppointment?: boolean }) {
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
        }),
      })
    },
  )
  await page.route('**/api/v1/network-portal/correction-cases**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ networkId: NETWORK_ID, asOf: '2026-07-17T12:00:00Z', items: [] }),
    })
  })
  await page.route('**/api/v1/network-portal/operational-exceptions**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ networkId: NETWORK_ID, asOf: '2026-07-17T12:00:00Z', items: [] }),
    })
  })
  await page.route(`**/api/v1/network-portal/tasks/${TASK_ID}/appointments**`, async (route: Route) => {
    if (options?.denyAppointment) {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'ACCESS_DENIED', message: 'denied' }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          appointmentId: APPOINTMENT_ID,
          taskId: TASK_ID,
          type: 'SURVEY',
          status: 'PROPOSED',
          assignedNetworkId: NETWORK_ID,
          aggregateVersion: 1,
          currentRevisionNo: 1,
        },
      ]),
    })
  })
  await page.route(
    `**/api/v1/network-portal/tasks/${TASK_ID}/contact-attempts**`,
    async (route: Route) => {
      if (options?.denyAppointment) {
        await route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({ errorCode: 'ACCESS_DENIED', message: 'denied' }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            contactAttemptId: CONTACT_ID,
            taskId: TASK_ID,
            channel: 'PHONE',
            contactedPartyRef: 'party-1',
            resultCode: 'NO_ANSWER',
            actorId: 'actor-1',
            createdAt: '2026-07-17T11:00:00Z',
          },
        ]),
      })
    },
  )
}

test.describe('M215 Network Portal 工作区预约/联系 fan-in', () => {
  test('M215-01/02/03：展示预约与联系摘要并深链任务', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubPortal(page)
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId(`workspace-related-appointment-${APPOINTMENT_ID}`)).toBeVisible()
    await expect(page.getByTestId(`workspace-related-contact-${CONTACT_ID}`)).toBeVisible()
    await expect(page.getByTestId('workspace-appointment-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
    await expect(page.getByTestId('workspace-contact-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_ID}`,
    )
  })

  test('M215-04：缺预约能力时省略预约/联系区块', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubPortal(page, { denyAppointment: true })
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-related-appointments')).toHaveCount(0)
    await expect(page.getByTestId('workspace-related-contacts')).toHaveCount(0)
    await expect(page.getByTestId('workspace-related-corrections')).toBeVisible()
  })
})
