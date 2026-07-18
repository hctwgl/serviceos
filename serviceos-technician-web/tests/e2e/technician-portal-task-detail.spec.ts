import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84b0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84b0-bbbb-7f8c-9505-36fe5c0ee002'
const WORK_ORDER_ID = '019f84b0-aaaa-7f8c-9505-36fe5c0ee001'
const APPOINTMENT_ID = '019f84b0-cccc-7f8c-9505-36fe5c0ee003'

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

async function stubTechnicianContext(page: Page, options: { visits?: Array<Record<string, unknown>> } = {}) {
  let visits = options.visits ?? [{
    visitId: '019f84b0-fffe-7f8c-9505-36fe5c0ee007',
    taskId: TASK_ID,
    appointmentId: APPOINTMENT_ID,
    visitSequence: 1,
    status: 'IN_PROGRESS',
    checkInCapturedAt: '2026-07-19T01:05:00Z',
    checkInReceivedAt: '2026-07-19T01:05:05Z',
    geofenceResult: 'WITHIN_GEOFENCE',
    policyDecision: 'ACCEPTED',
    checkOutCapturedAt: null,
    checkOutReceivedAt: null,
    resultCode: null,
    exceptionCode: null,
    aggregateVersion: 1,
  }]
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextVersion: 'ctx-v1',
        asOf: '2026-07-18T03:00:00Z',
        contexts: [{
          contextId: CONTEXT_ID,
          portal: 'TECHNICIAN',
          personaType: 'TECHNICIAN',
          scopeType: 'NETWORK',
          scopeRef: NETWORK_ID,
          scopeSummary: { organizationIds: [], networkIds: [NETWORK_ID], projectIds: [] },
          version: '1',
        }],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextId: CONTEXT_ID,
        portal: 'TECHNICIAN',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-18T03:00:00Z',
        items: [{
          pageId: 'TECHNICIAN.TASK.LIST',
          routeKey: 'task-feed',
          title: '任务 Feed',
          order: 1,
          section: '任务',
          requiredCapabilities: ['task.readAssigned'],
        }],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/task-feed**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        asOf: '2026-07-18T03:00:00Z',
        nextCursor: null,
        items: [{
          itemType: 'ASSIGNMENT',
          taskId: TASK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
          serviceAssignmentId: '019f84b0-eeee-7f8c-9505-36fe5c0ee005',
          taskAssignmentId: null,
          taskType: 'INSTALLATION',
          taskKind: 'HUMAN',
          stageCode: 'INSTALL',
          taskStatus: 'READY',
          businessType: 'INSTALLATION',
          effectiveFrom: '2026-07-18T02:00:00Z',
          cursor: 'cursor-1',
          invalidationReason: null,
        }],
      }),
    })
  })
  await page.route('**/api/v1/technician/me/tasks/**', async (route: Route) => {
    expect(route.request().headers()['x-technician-context']).toBe(CONTEXT_ID)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        networkId: NETWORK_ID,
        taskId: TASK_ID,
        workOrderId: WORK_ORDER_ID,
        projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
        serviceAssignmentId: '019f84b0-eeee-7f8c-9505-36fe5c0ee005',
        taskAssignmentId: null,
        taskType: 'INSTALLATION',
        taskKind: 'HUMAN',
        stageCode: 'INSTALL',
        taskStatus: 'READY',
        businessType: 'INSTALLATION',
        effectiveFrom: '2026-07-18T02:00:00Z',
        executionGuarded: false,
        resourceVersion: 3,
        appointments: [{
          appointmentId: APPOINTMENT_ID,
          taskId: TASK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004',
          type: 'INSTALLATION',
          status: 'CONFIRMED',
          windowStart: '2026-07-19T01:00:00Z',
          windowEnd: '2026-07-19T03:00:00Z',
          timezone: 'Asia/Shanghai',
        }],
        contactAttempts: [{
          contactAttemptId: '019f84b0-ffff-7f8c-9505-36fe5c0ee006',
          taskId: TASK_ID,
          channel: 'PHONE',
          startedAt: '2026-07-18T02:30:00Z',
          endedAt: '2026-07-18T02:32:00Z',
          resultCode: 'NO_ANSWER',
          nextContactAt: '2026-07-18T05:00:00Z',
          createdAt: '2026-07-18T02:33:00Z',
        }],
        visits,
        formSubmissions: [{
          submissionId: '019f84b0-fffd-7f8c-9505-36fe5c0ee008',
          formVersionId: '019f84b0-fffc-7f8c-9505-36fe5c0ee009',
          formKey: 'INSTALL_REPORT',
          submissionVersion: 2,
          validationStatus: 'VALIDATED',
          errorCount: 0,
          warningCount: 1,
          submittedAt: '2026-07-19T02:00:00Z',
        }],
        asOf: '2026-07-18T03:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/technician/me/appointments/*/visits:check-in', async (route: Route) => {
    const request = route.request()
    expect(request.headers()['x-technician-context']).toBe(CONTEXT_ID)
    const body = request.postDataJSON()
    expect(request.headers()['idempotency-key']).toBe(body.deviceCommandId)
    expect(body).not.toHaveProperty('offline')
    expect(body.location).toEqual({ latitude: 31.2304, longitude: 121.4737, accuracyMeters: 8 })
    visits = [{
      visitId: '019f84b0-fffe-7f8c-9505-36fe5c0ee007', taskId: TASK_ID,
      appointmentId: APPOINTMENT_ID, visitSequence: 1, status: 'IN_PROGRESS',
      checkInCapturedAt: body.capturedAt, checkInReceivedAt: '2026-07-19T01:05:05Z',
      geofenceResult: 'WITHIN_GEOFENCE', policyDecision: 'ACCEPTED',
      checkOutCapturedAt: null, checkOutReceivedAt: null, resultCode: null,
      exceptionCode: null, aggregateVersion: 1,
    }]
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
      visitId: visits[0].visitId, status: 'IN_PROGRESS', aggregateVersion: 1,
      geofenceResult: 'WITHIN_GEOFENCE', policyDecision: 'ACCEPTED', occurredAt: '2026-07-19T01:05:05Z',
    }) })
  })
  await page.route('**/api/v1/technician/me/visits/*:interrupt', async (route: Route) => {
    const request = route.request()
    expect(request.headers()['x-technician-context']).toBe(CONTEXT_ID)
    expect(request.headers()['if-match']).toBe('"1"')
    const body = request.postDataJSON()
    expect(body.exceptionCode).toBe('SITE_UNSAFE')
    expect(body.evidenceRefs).toEqual([])
    visits = visits.map((visit) => ({ ...visit, status: 'INTERRUPTED', exceptionCode: body.exceptionCode,
      aggregateVersion: 2 }))
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      visitId: visits[0].visitId, status: 'INTERRUPTED', aggregateVersion: 2,
      geofenceResult: 'WITHIN_GEOFENCE', policyDecision: 'ACCEPTED', occurredAt: '2026-07-19T02:00:00Z',
    }) })
  })
}

test.describe('M246 Technician Portal 表单提交安全摘要', () => {
  test('M246-01/02：详情展示表单校验摘要，不出现 values 和提交人', async ({ page }) => {
    await stubTechnicianContext(page)
    await loginWithLocalKeycloak(page)

    await navigateTechnician(page, '/technician-portal/task-feed')
    await expect(page.getByTestId('technician-feed-task-detail-deeplink')).toHaveAttribute(
      'href',
      `/technician-portal/tasks/${TASK_ID}`,
    )
    await page.getByTestId('technician-feed-task-detail-deeplink').click()

    await expect(page).toHaveURL(new RegExp(`/technician-portal/tasks/${TASK_ID}$`))
    await expect(page.getByTestId('technician-portal-task-detail')).toBeVisible()
    await expect(page.getByTestId('technician-task-detail-task-id')).toHaveText(TASK_ID)
    await expect(page.getByTestId('technician-task-detail-status')).toHaveText('READY')
    await expect(page.getByTestId('technician-task-detail-appointments')).toContainText('CONFIRMED')
    await expect(page.getByTestId('technician-task-detail-contact-attempts')).toContainText('NO_ANSWER')
    await expect(page.getByTestId('technician-task-detail-contact-attempts')).toContainText('PHONE')
    await expect(page.getByTestId('technician-task-detail-visits')).toContainText('IN_PROGRESS')
    await expect(page.getByTestId('technician-task-detail-visits')).toContainText('WITHIN_GEOFENCE')
    await expect(page.getByTestId('technician-task-detail-form-submissions')).toContainText('INSTALL_REPORT')
    await expect(page.getByTestId('technician-task-detail-form-submissions')).toContainText('VALIDATED')
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('不返回地址')
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('联系对象引用')
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('GPS')
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('表单值')
    await expect(page.getByTestId('technician-task-detail-schedule-link')).toHaveAttribute(
      'href',
      `/technician-portal/schedule?taskId=${TASK_ID}`,
    )
  })

  test('M262-01：用户主动定位后在线签到，不发送 offline 或伪造 receivedAt', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'geolocation', { configurable: true, value: {
        getCurrentPosition: (success: PositionCallback) => success({
          coords: { latitude: 31.2304, longitude: 121.4737, accuracy: 8,
            altitude: null, altitudeAccuracy: null, heading: null, speed: null },
          timestamp: Date.parse('2026-07-19T01:05:00Z'),
        } as GeolocationPosition),
      } })
    })
    await stubTechnicianContext(page, { visits: [] })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await page.getByTestId('technician-visit-check-in').click()
    await expect(page.getByTestId('technician-visit-action-message')).toHaveText('到场已由服务器确认')
    await expect(page.getByTestId('technician-task-detail-visits')).toContainText('IN_PROGRESS')
  })

  test('M262-02：无法施工携带 If-Match 并明确不伪造 Evidence', async ({ page }) => {
    await stubTechnicianContext(page)
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await page.getByTestId('technician-visit-interrupt-note').fill('现场存在安全风险')
    await page.getByTestId('technician-visit-interrupt').click()
    await expect(page.getByTestId('technician-visit-action-message')).toContainText('未伪造任何资料上传')
    await expect(page.getByTestId('technician-task-detail-visits')).toContainText('INTERRUPTED')
    await expect(page.getByTestId('technician-visit-checkout-boundary')).not.toBeVisible()
  })
})
