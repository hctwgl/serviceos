import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2222-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const TASK_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const TECH_ID = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
const WO_ID = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'

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

async function stubWorkbenchProduct(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            scopeRef: NETWORK_ID,
            version: 'ctx-v1',
          },
        ],
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
            pageId: 'NETWORK.WORKBENCH',
            routeKey: 'workbench',
            title: '本网点工作台',
            order: 10,
            section: '核心',
            requiredCapabilities: [],
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/network-portal/workbench**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        activeWorkOrderCount: 3,
        activeTaskCount: 4,
        activeTechnicianCount: 2,
        unassignedTechnicianTaskCount: 1,
        openCorrectionCaseCount: 0,
        openOperationalExceptionCount: 0,
        slaSummary: { openCount: 1, breachedCount: 0 },
        todayAppointmentCount: 1,
        todayAppointments: [
          {
            appointmentId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            taskId: TASK_ID,
            workOrderId: WO_ID,
            type: 'INSTALLATION',
            status: 'CONFIRMED',
            windowStart: '2026-07-21T02:00:00Z',
            windowEnd: '2026-07-21T03:00:00Z',
            timezone: 'Asia/Shanghai',
            technicianId: TECH_ID,
            technicianDisplayName: '张师傅',
          },
        ],
        todayTimeline: [
          { bucketCode: 'UNASSIGNED', label: '待分配', count: 1, summary: '待指派师傅任务 1 个' },
          { bucketCode: 'AM_APPOINTMENTS', label: '上午预约', count: 1, summary: '上午预约 1 个' },
          { bucketCode: 'PM_APPOINTMENTS', label: '下午预约', count: 0, summary: '下午无预约窗口' },
          { bucketCode: 'EVENING_APPOINTMENTS', label: '晚间预约', count: 0, summary: '晚间无预约窗口' },
          { bucketCode: 'OPEN_CORRECTIONS', label: '资料整改', count: 0, summary: '无开放整改' },
          { bucketCode: 'SLA_AT_RISK', label: 'SLA 风险', count: 1, summary: 'SLA 风险 1 项（已超时 0）' },
        ],
        capacity: [
          {
            capacityCounterId: 'cap-1',
            businessType: 'INSTALLATION',
            maxUnits: 10,
            occupiedUnits: 3,
            availableUnits: 7,
            version: 1,
            updatedAt: '2026-07-20T04:00:00Z',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/network-portal/tasks**', async (route: Route) => {
    if (route.request().url().includes('/assign-candidates')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          taskId: TASK_ID,
          businessType: 'INSTALLATION',
          workOrderRegionSummary: '青岛市',
          rankingExplanation:
            '排序：可分配优先 → 推荐档位 → 行政区亲和 → 开放任务少 → 姓名；依据可见运营事实，不含内部评分公式。',
          emptyReason: null,
          items: [
            {
              technicianProfileId: TECH_ID,
              displayName: '张师傅',
              membershipStatus: 'ACTIVE',
              profileStatus: 'ACTIVE',
              openTaskCount: 1,
              approvedQualificationCount: 2,
              pendingQualificationCount: 0,
              qualificationSummary: '已通过资质 2 项',
              upcomingAppointmentCount: 1,
              scheduleConflictSummary: '另有 1 个未完成预约',
              scheduleOverlap: false,
              distanceTier: 'SAME_CITY',
              distanceSummary: '同城 · 青岛市',
              coverageMatched: true,
              capacityAvailableUnits: 7,
              capacityMaxUnits: 10,
              warnings: [],
              assignable: true,
              recommendationTier: 'RECOMMENDED',
              recommendationSummary: '建议优先：同城覆盖 + 已通过资质 2 项 + 网点产能可用',
              recommendationReasons: ['同城覆盖', '已通过资质 2 项', '网点产能可用'],
            },
          ],
          asOf: '2026-07-20T04:00:00Z',
        }),
      })
      return
    }
    if (route.request().url().includes(':assign-technician')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          taskId: TASK_ID,
          workOrderId: WO_ID,
          networkServiceAssignmentId: 'nsa-1',
          technicianServiceAssignmentId: 'tsa-12345678-xxxx',
          networkAssigneeId: NETWORK_ID,
          technicianAssigneeId: TECH_ID,
          occurredAt: '2026-07-20T04:05:00Z',
        }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            taskId: TASK_ID,
            workOrderId: WO_ID,
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
        nextCursor: null,
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
}

test.describe('M390/M407/M408/M410/M411/M412 网点工作台产品化 + 分配候选 + 今日预约', () => {
  test('展示 SummaryStrip、今日时间轴/预约、待分配表并完成分配', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 1024 })
    await stubWorkbenchProduct(page)
    await loginWithLocalKeycloak(page)
    await navigateNetwork(page, '/network-portal/workbench')

    await expect(page.getByTestId('network-portal-workbench')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('network-summary-strip')).toBeVisible()
    await expect(page.getByTestId('workbench-unassigned-count')).toContainText('1')
    await expect(page.getByTestId('workbench-today-appointment-count')).toContainText('1')
    await expect(page.getByTestId('workbench-today-timeline')).toBeVisible()
    await expect(page.getByTestId('timeline-bucket-UNASSIGNED')).toContainText('待分配')
    await expect(page.getByTestId('timeline-bucket-AM_APPOINTMENTS')).toContainText('上午预约')
    await expect(page.getByTestId('workbench-today-appointments')).toContainText('张师傅')
    await expect(page.getByTestId('workbench-unassigned-table')).toBeVisible()
    await expect(page.getByTestId(`assign-open-${TASK_ID}`)).toBeVisible()

    await page.getByTestId('workbench-unassigned-table').scrollIntoViewIfNeeded()
    await page.getByTestId(`assign-open-${TASK_ID}`).click()
    await expect(page.getByTestId('assign-technician-drawer')).toBeVisible()
    await expect(page.getByTestId(`assign-candidate-${TECH_ID}`)).toBeVisible()
    await expect(page.getByTestId('assign-candidate-recommendation')).toContainText('建议优先')
    await expect(page.getByTestId('assign-candidate-open-tasks')).toContainText('开放任务 1')
    await expect(page.getByTestId('assign-candidate-qualification')).toContainText('已通过资质')
    await expect(page.getByTestId('assign-candidate-schedule')).toContainText('未完成预约')
    await expect(page.getByTestId('assign-candidate-distance')).toContainText('同城')
    await page.getByTestId(`assign-candidate-${TECH_ID}`).click()
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('不含内部评分公式')
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('建议优先')
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('网点产能可用')
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('已通过资质')
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('未完成预约')
    await expect(page.getByTestId('assign-drawer-impact')).toContainText('同城')
    await expect(page.getByTestId('assign-drawer-impact')).not.toContainText('距离读模型尚未交付')
    await expect(page.getByTestId('assign-drawer-impact')).not.toContainText('推荐解释读模型未就绪')
    await page.getByTestId('assign-drawer-submit').click()
    await expect(page.getByTestId('assign-drawer-message')).toContainText('指派已生效')

    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-workbench-product-1440.png',
      fullPage: true,
    })
  })
})
