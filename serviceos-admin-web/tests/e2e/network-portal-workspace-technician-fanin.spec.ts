import { expect, test, type Page, type Route } from '@playwright/test'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ee001'
const TASK_ASSIGNED = '019f84a0-bbbb-7f8c-9505-36fe5c0ee002'
const TASK_UNASSIGNED = '019f84a0-bbbb-7f8c-9505-36fe5c0ee012'
const TECH_PROFILE_ID = 'tech-profile-a'
const MEMBERSHIP_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ee005'
const APPOINTMENT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ee003'
const CONTACT_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ee004'
const WINDOW_START = '2026-07-18T01:00:00Z'
const WINDOW_END = '2026-07-18T04:00:00Z'

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

async function stubPortal(page: Page, options?: { denyTechnicians?: boolean }) {
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
          taskIds: [TASK_ASSIGNED, TASK_UNASSIGNED],
          businessType: 'INSTALLATION',
          technicianId: TECH_PROFILE_ID,
          effectiveFrom: '2026-07-17T10:00:00Z',
          asOf: '2026-07-17T12:00:00Z',
          tasks: [
            {
              taskId: TASK_ASSIGNED,
              workOrderId: WORK_ORDER_ID,
              projectId: null,
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'S1',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: TECH_PROFILE_ID,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
            {
              taskId: TASK_UNASSIGNED,
              workOrderId: WORK_ORDER_ID,
              projectId: null,
              taskType: 'SURVEY',
              taskKind: 'HUMAN',
              stageCode: 'S0',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: null,
              effectiveFrom: '2026-07-17T10:00:00Z',
            },
          ],
          // M227：预约 window 由 workspace.appointments 交付（不再依赖列表 fan-in）
          appointments: [
            {
              appointmentId: APPOINTMENT_ID,
              taskId: TASK_ASSIGNED,
              type: 'SURVEY',
              status: 'PROPOSED',
              assignedNetworkId: NETWORK_ID,
              technicianId: TECH_PROFILE_ID,
              currentRevisionNo: 1,
              windowStart: WINDOW_START,
              windowEnd: WINDOW_END,
              timezone: 'Asia/Shanghai',
              estimatedDurationMinutes: 180,
              aggregateVersion: 1,
              createdAt: '2026-07-17T10:00:00Z',
            },
          ],
          contactAttempts: [
            {
              contactAttemptId: CONTACT_ID,
              taskId: TASK_ASSIGNED,
              projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ee005',
              workOrderId: WORK_ORDER_ID,
              channel: 'PHONE',
              startedAt: '2026-07-17T11:00:00Z',
              endedAt: '2026-07-17T11:00:30Z',
              resultCode: 'NO_ANSWER',
              nextContactAt: null,
              createdAt: '2026-07-17T11:00:00Z',
            },
          ],
        }),
      })
    },
  )
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    if (options?.denyTechnicians) {
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
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            membershipId: MEMBERSHIP_ID,
            technicianProfileId: TECH_PROFILE_ID,
            principalId: 'principal-a',
            displayName: '张师傅',
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
  await page.route(`**/api/v1/network-portal/tasks/${TASK_ASSIGNED}/appointments**`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          appointmentId: APPOINTMENT_ID,
          taskId: TASK_ASSIGNED,
          type: 'SURVEY',
          status: 'PROPOSED',
          assignedNetworkId: NETWORK_ID,
          aggregateVersion: 1,
          currentRevisionNo: 1,
          revisions: [
            {
              revisionId: '019f84a0-ffff-7f8c-9505-36fe5c0ee006',
              revisionNo: 1,
              window: {
                start: WINDOW_START,
                end: WINDOW_END,
                timezone: 'Asia/Shanghai',
                estimatedDurationMinutes: 180,
              },
              addressRef: 'SHOULD-NOT-RENDER',
              addressVersion: 'v1',
              note: 'secret-note',
            },
          ],
        },
      ]),
    })
  })
  await page.route(
    `**/api/v1/network-portal/tasks/${TASK_UNASSIGNED}/appointments**`,
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    },
  )
  for (const taskId of [TASK_ASSIGNED, TASK_UNASSIGNED]) {
    await page.route(`**/api/v1/network-portal/tasks/${taskId}/contact-attempts**`, async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          taskId === TASK_ASSIGNED
            ? [
                {
                  contactAttemptId: CONTACT_ID,
                  taskId: TASK_ASSIGNED,
                  channel: 'PHONE',
                  contactedPartyRef: 'party-1',
                  resultCode: 'NO_ANSWER',
                  actorId: 'actor-1',
                  createdAt: '2026-07-17T11:00:00Z',
                },
              ]
            : [],
        ),
      })
    })
  }
}

test.describe('M216 Network Portal 工作区当前师傅 fan-in', () => {
  test('M216-01/02/03/04：展示师傅名、membership 深链、未指派深链与预约 window', async ({
    page,
  }) => {
    await loginWithLocalKeycloak(page)
    await stubPortal(page)
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-current-technicians')).toBeVisible()
    await expect(page.getByTestId('workspace-technician-display-name')).toHaveText('张师傅')
    await expect(page.getByTestId('workspace-technician-membership-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/technicians/memberships/${MEMBERSHIP_ID}`,
    )
    await expect(page.getByTestId('workspace-unassigned-task-deeplink')).toHaveAttribute(
      'href',
      `/network-portal/tasks?taskId=${TASK_UNASSIGNED}`,
    )
    await expect(page.getByTestId('workspace-appointment-window')).toContainText(WINDOW_START)
    await expect(page.getByTestId('workspace-appointment-window')).toContainText(WINDOW_END)
    await expect(page.getByTestId('workspace-appointment-window')).toContainText('Asia/Shanghai')
    await expect(page.locator('body')).not.toContainText('SHOULD-NOT-RENDER')
    await expect(page.locator('body')).not.toContainText('secret-note')
  })

  test('M216-05：缺 technician.readOwnNetwork 时省略当前师傅区块', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubPortal(page, { denyTechnicians: true })
    await page.goto(`/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-current-technicians')).toHaveCount(0)
    await expect(page.getByTestId('workspace-header-technician-id')).toContainText(TECH_PROFILE_ID)
    await expect(page.getByTestId('workspace-related-appointments')).toBeVisible()
  })
})
