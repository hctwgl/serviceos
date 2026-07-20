import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ec001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ec002'
const TECH_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'

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

async function stubWorkspaceProduct(page: Page) {
  // 覆盖 fixture 的 network-portal 503 兜底，避免写命令落到失败关闭。
  await page.unroute('**/api/v1/network-portal/**').catch(() => undefined)
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [{ contextId: CONTEXT_ID, portal: 'NETWORK', scopeRef: NETWORK_ID, version: 'ctx-v1' }],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        capabilityCodes: [
          'networkTask.read',
          'technician.readOwnNetwork',
          'networkPortal.assignTechnician',
          'networkPortal.manageAppointment',
          'sla.read',
          'evidence.read',
          'operations.exception.read',
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        items: [
          {
            pageId: 'NETWORK.WORKORDER.WORKSPACE',
            routeKey: 'work-order-workspace',
            title: '工单工作区',
            order: 16,
            section: '工单任务',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        principalId: 'principal-network-1',
        tenantId: 'tenant-1',
        displayName: '网点负责人',
        contextVersion: 'ctx-v1',
        asOf: '2026-07-20T04:00:00Z',
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/work-orders/${WORK_ORDER_ID}/workspace**`,
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
          technicianId: null,
          effectiveFrom: '2026-07-20T03:00:00Z',
          asOf: '2026-07-20T04:00:00Z',
          slaSummary: { openCount: 1, breachedCount: 0 },
          appointments: [],
          contactAttempts: [],
          corrections: [],
          exceptions: [],
          technicians: [],
          tasks: [
            {
              taskId: TASK_ID,
              workOrderId: WORK_ORDER_ID,
              projectId: null,
              taskType: 'INSTALL',
              taskKind: 'HUMAN',
              stageCode: 'INSTALLATION',
              status: 'READY',
              businessType: 'INSTALLATION',
              technicianId: null,
              effectiveFrom: '2026-07-20T03:00:00Z',
              serviceProductCode: 'INSTALLATION',
            },
          ],
        }),
      })
    },
  )
  await page.route('**/api/v1/network-portal/tasks/**/assign-candidates**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        taskId: TASK_ID,
        businessType: 'INSTALLATION',
        items: [
          {
            technicianProfileId: TECH_ID,
            displayName: '张师傅',
            membershipStatus: 'ACTIVE',
            profileStatus: 'ACTIVE',
            openTaskCount: 0,
            approvedQualificationCount: 1,
            pendingQualificationCount: 0,
            qualificationSummary: '已通过资质 1 项',
            upcomingAppointmentCount: 0,
            scheduleConflictSummary: '无近期预约',
            scheduleOverlap: false,
            capacityAvailableUnits: 5,
            capacityMaxUnits: 8,
            warnings: [],
            assignable: true,
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/network-portal/technicians**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            membershipId: 'mem-1',
            technicianProfileId: TECH_ID,
            principalId: 'prin-1',
            displayName: '张师傅',
            profileStatus: 'ACTIVE',
            membershipStatus: 'ACTIVE',
            validFrom: '2026-01-01T00:00:00Z',
            validTo: null,
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks/**/appointments**', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          appointmentId: 'appt-1',
          revisionId: 'rev-1',
          status: 'PROPOSED',
          revisionNo: 1,
          aggregateVersion: 1,
          occurredAt: '2026-07-20T04:10:00Z',
        }),
      })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
  await page.route('**/api/v1/network-portal/tasks/**/contact-attempts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contactAttemptId: 'ca-1',
        taskId: TASK_ID,
        channel: 'PHONE',
        resultCode: 'REACHED',
        startedAt: '2026-07-20T04:12:00Z',
        endedAt: '2026-07-20T04:15:00Z',
      }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks/**:assign-technician**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        taskId: TASK_ID,
        workOrderId: WORK_ORDER_ID,
        networkServiceAssignmentId: 'nsa-1',
        technicianServiceAssignmentId: 'tsa-12345678-xxxx',
        networkAssigneeId: NETWORK_ID,
        technicianAssigneeId: TECH_ID,
        occurredAt: '2026-07-20T04:05:00Z',
      }),
    })
  })
}

test.describe('M391 网点工单工作区产品化 + 预约协同', () => {
  test('展示产品外壳、分配与预约协同', async ({ page }) => {
    await stubWorkspaceProduct(page)
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)

    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-product-shell')).toBeVisible()
    await expect(page.getByTestId('workspace-business-progress')).toContainText('当前')
    await expect(page.getByTestId('workspace-current-task-card')).toBeVisible()
    await expect(page.getByTestId('workspace-appointment-collaboration')).toBeVisible()
    await expect(page.getByTestId('workspace-header-fields')).toBeVisible()

    await page.getByTestId('workspace-action-assign').click()
    await expect(page.getByTestId('assign-technician-drawer')).toBeVisible()
    await page.getByTestId(`assign-candidate-${TECH_ID}`).click()
    await page.getByTestId('assign-drawer-submit').click()
    await expect(page.getByTestId('assign-technician-drawer')).toBeHidden({ timeout: 10_000 })

    await page.getByTestId('workspace-appointment-task').selectOption(TASK_ID)
    await page.getByTestId('workspace-appointment-window-start').fill('2026-07-21T09:00')
    await page.getByTestId('workspace-appointment-window-end').fill('2026-07-21T11:00')
    const proposeResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/appointments') && response.request().method() === 'POST',
      { timeout: 15_000 },
    )
    await page.getByTestId('workspace-appointment-propose').click()
    expect((await proposeResponsePromise).ok()).toBeTruthy()
    await expect(page.getByTestId('workspace-appointment-message')).toContainText('预约草案', {
      timeout: 10_000,
    })

    const contactResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/contact-attempts') && response.request().method() === 'POST',
      { timeout: 15_000 },
    )
    await page.getByTestId('workspace-contact-submit').click()
    expect((await contactResponsePromise).ok()).toBeTruthy()
    await expect(page.getByTestId('workspace-appointment-message')).toContainText('联系记录')

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-workspace-product-1440.png',
      fullPage: true,
    })
  })
})
