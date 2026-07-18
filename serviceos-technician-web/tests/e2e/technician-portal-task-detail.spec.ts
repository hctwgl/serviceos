import { navigateTechnician, expect, test, type Page, type Route } from './support/fixture'

const NETWORK_ID = '019f84b0-3333-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `TECHNICIAN|NETWORK|${NETWORK_ID}`
const TASK_ID = '019f84b0-bbbb-7f8c-9505-36fe5c0ee002'
const WORK_ORDER_ID = '019f84b0-aaaa-7f8c-9505-36fe5c0ee001'
const APPOINTMENT_ID = '019f84b0-cccc-7f8c-9505-36fe5c0ee003'
const EVIDENCE_SLOT_ID = '019f84b0-dddc-7f8c-9505-36fe5c0ee011'
const EVIDENCE_ITEM_ID = '019f84b0-dddb-7f8c-9505-36fe5c0ee012'
const EVIDENCE_REVISION_ID = '019f84b0-ddda-7f8c-9505-36fe5c0ee013'
const EVIDENCE_SNAPSHOT_ID = '019f84b0-ddd8-7f8c-9505-36fe5c0ee015'

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

async function stubTechnicianContext(
  page: Page,
  options: { visits?: Array<Record<string, unknown>>; taskStatus?: string; validatedEvidence?: boolean } = {},
) {
  let taskStatus = options.taskStatus ?? 'READY'
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
  let formSubmissions = [{
    submissionId: '019f84b0-fffd-7f8c-9505-36fe5c0ee008',
    formVersionId: '019f84b0-fffc-7f8c-9505-36fe5c0ee009',
    formKey: 'INSTALL_REPORT',
    submissionVersion: 2,
    validationStatus: 'VALIDATED',
    errorCount: 0,
    warningCount: 1,
    submittedAt: '2026-07-19T02:00:00Z',
  }]
  let evidenceItems: Array<Record<string, unknown>> = options.validatedEvidence ? [{
    evidenceItemId: EVIDENCE_ITEM_ID, taskId: TASK_ID, evidenceSlotId: EVIDENCE_SLOT_ID,
    itemOrdinal: 1, status: 'ACTIVE', createdAt: '2026-07-19T03:00:00Z', revisions: [{
      evidenceRevisionId: EVIDENCE_REVISION_ID, revisionNumber: 1,
      contentDigest: 'c'.repeat(64), mimeType: 'image/jpeg', sizeBytes: 10,
      status: 'VALIDATED', createdAt: '2026-07-19T03:00:00Z',
    }],
  }] : []
  let expectedEvidenceDigest = ''
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
    const pathname = new URL(route.request().url()).pathname
    if (pathname.endsWith('/forms')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{
          taskId: TASK_ID,
          formVersionId: '019f84b0-fffc-7f8c-9505-36fe5c0ee009',
          formKey: 'INSTALL_REPORT',
          semanticVersion: '1.0.0',
          schemaVersion: 'FORM_V1',
          contentDigest: 'a'.repeat(64),
          definition: {
            formKey: 'INSTALL_REPORT', version: '1.0.0', title: '安装结果',
            sections: [{ sectionKey: 'result', title: '现场结果', fields: [
              { fieldKey: 'survey.conclusion', label: '勘测结论', dataType: 'STRING',
                binding: 'task.input.survey.conclusion', required: true },
              { fieldKey: 'installation.count', label: '安装数量', dataType: 'INTEGER',
                binding: 'task.input.installation.count' },
              { fieldKey: 'site.safe', label: '现场安全', dataType: 'BOOLEAN',
                binding: 'task.input.site.safe' },
            ] }],
          },
        }]),
      })
      return
    }
    if (pathname.endsWith('/form-submissions') && route.request().method() === 'POST') {
      const request = route.request()
      const body = request.postDataJSON()
      expect(request.headers()['idempotency-key']).toBeTruthy()
      expect(body).not.toHaveProperty('prefillVersion')
      expect(body).not.toHaveProperty('submittedBy')
      expect(body.values).toEqual({
        'survey.conclusion': 'PASS',
        'installation.count': 2,
        'site.safe': true,
      })
      formSubmissions = [...formSubmissions, {
        submissionId: '019f84b0-fff0-7f8c-9505-36fe5c0ee010',
        formVersionId: body.formVersionId,
        formKey: 'INSTALL_REPORT', submissionVersion: 3, validationStatus: 'VALIDATED',
        errorCount: 0, warningCount: 0, submittedAt: '2026-07-19T02:10:00Z',
      }]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        submissionId: formSubmissions.at(-1)?.submissionId, taskId: TASK_ID,
        projectId: '019f84b0-dddd-7f8c-9505-36fe5c0ee004', formVersionId: body.formVersionId,
        formKey: 'INSTALL_REPORT', submissionVersion: 3, values: body.values,
        contentDigest: 'b'.repeat(64), validationStatus: 'VALIDATED', errors: [], warnings: [],
        submittedAt: '2026-07-19T02:10:00Z',
      }) })
      return
    }
    if (pathname.endsWith('/evidence-slots') && route.request().method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{
        slotId: EVIDENCE_SLOT_ID, requirementCode: 'SITE_PHOTO', occurrenceKey: '1',
        requirementName: '现场照片', mediaType: 'PHOTO', required: true, minCount: 1, maxCount: 1,
        status: evidenceItems.length ? 'SATISFIED' : 'MISSING', active: true,
        transition: 'ACTIVATED', requiredDisposition: 'NONE',
      }]) })
      return
    }
    if (pathname.endsWith('/evidence-items') && route.request().method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(evidenceItems) })
      return
    }
    if (pathname.endsWith('/evidence-set-snapshots') && route.request().method() === 'POST') {
      const request = route.request()
      const body = request.postDataJSON()
      expect(request.headers()['idempotency-key']).toBeTruthy()
      expect(body).toEqual({ memberRevisionIds: [EVIDENCE_REVISION_ID] })
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        evidenceSetSnapshotId: EVIDENCE_SNAPSHOT_ID, taskId: TASK_ID, purpose: 'TASK_SUBMISSION',
        memberCount: 1, contentDigest: 'd'.repeat(64), createdAt: '2026-07-19T03:10:00Z',
        members: [{ evidenceRevisionId: EVIDENCE_REVISION_ID, evidenceItemId: EVIDENCE_ITEM_ID,
          evidenceSlotId: EVIDENCE_SLOT_ID, revisionNumber: 1, revisionStatus: 'VALIDATED',
          contentDigest: 'c'.repeat(64), memberOrdinal: 1 }],
      }) })
      return
    }
    if (pathname.endsWith(`${TASK_ID}:complete`) && route.request().method() === 'POST') {
      const request = route.request()
      const body = request.postDataJSON()
      expect(request.headers()['idempotency-key']).toBeTruthy()
      expect(request.headers()['if-match']).toBe('"3"')
      expect(body).toEqual({
        evidenceSetSnapshotId: EVIDENCE_SNAPSHOT_ID,
        formSubmissionId: formSubmissions.at(-1)?.submissionId,
      })
      expect(body).not.toHaveProperty('resultRef')
      expect(body).not.toHaveProperty('inputVersionRefs')
      taskStatus = 'COMPLETED'
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        taskId: TASK_ID, status: 'COMPLETED', resourceVersion: 4,
        occurredAt: '2026-07-19T03:11:00Z',
      }) })
      return
    }
    if (pathname.endsWith(':finalize') && pathname.includes('/evidence-slots/')) {
      const body = route.request().postDataJSON()
      expect(body.actualSha256).toBe(expectedEvidenceDigest)
      expect(body.finalizeCommandId).toBeTruthy()
      evidenceItems = [{
        evidenceItemId: EVIDENCE_ITEM_ID, taskId: TASK_ID, evidenceSlotId: EVIDENCE_SLOT_ID,
        itemOrdinal: 1, status: 'ACTIVE', createdAt: '2026-07-19T03:00:00Z', revisions: [{
          evidenceRevisionId: EVIDENCE_REVISION_ID, revisionNumber: 1,
          contentDigest: expectedEvidenceDigest, mimeType: 'image/jpeg', sizeBytes: 10,
          status: 'STORED', createdAt: '2026-07-19T03:00:00Z',
        }],
      }]
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(evidenceItems[0]) })
      return
    }
    if (pathname.endsWith('/upload-sessions') && pathname.includes('/evidence-slots/')) {
      const request = route.request()
      const body = request.postDataJSON()
      expect(request.headers()['idempotency-key']).toBeTruthy()
      expect(body.captureSource).toBe('FILE')
      expect(body.expectedSize).toBe(10)
      expect(body).not.toHaveProperty('offline')
      expect(body).not.toHaveProperty('uploadedBy')
      expect(body).not.toHaveProperty('locationVerified')
      expect(body).not.toHaveProperty('onBehalfOf')
      expectedEvidenceDigest = body.expectedSha256
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        uploadSessionId: '019f84b0-ddd9-7f8c-9505-36fe5c0ee014',
        evidenceSlotId: EVIDENCE_SLOT_ID, evidenceItemId: null, status: 'CREATED', uploadMethod: 'PUT',
        uploadUrl: '/api/v1/file-transfers/test-token', requiredHeaders: { 'Content-Type': 'image/jpeg' },
        uploadAuthorizationExpiresAt: '2026-07-19T03:05:00Z', sessionExpiresAt: '2026-07-19T03:10:00Z',
      }) })
      return
    }
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
        taskStatus,
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
        formSubmissions,
        asOf: '2026-07-18T03:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/file-transfers/test-token', async (route: Route) => {
    const request = route.request()
    expect(request.method()).toBe('PUT')
    expect(request.headers()['authorization']).toBeUndefined()
    expect(request.headers()['x-technician-context']).toBeUndefined()
    expect(request.postDataBuffer()?.byteLength).toBe(10)
    await route.fulfill({ status: 204, body: '' })
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
    await expect(page.getByTestId('technician-task-detail-boundary')).toContainText('提交人')
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

  test('M263-01：固定冻结表单在线提交保持类型且不伪造草稿/prefill', async ({ page }) => {
    await stubTechnicianContext(page, { taskStatus: 'RUNNING' })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await page.getByTestId('technician-form-field-survey.conclusion').fill('PASS')
    await page.getByTestId('technician-form-field-installation.count').fill('2')
    await page.getByTestId('technician-form-field-site.safe').check()
    await page.getByTestId('technician-online-form-submit').click()

    await expect(page.getByTestId('technician-online-form-message')).toContainText('版本 3')
    await expect(page.getByTestId('technician-task-detail-form-submissions')).toContainText('VALIDATED')
    await expect(page.getByTestId('technician-online-form')).toContainText('不会伪装成已保存草稿')
  })

  test('M264-01：浏览器资料走受限 PUT/Finalize 且不伪造可信元数据', async ({ page }) => {
    await stubTechnicianContext(page, { taskStatus: 'RUNNING' })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await expect(page.getByTestId(`technician-evidence-slot-${EVIDENCE_SLOT_ID}`)).toContainText('MISSING')
    await page.getByTestId(`technician-evidence-file-${EVIDENCE_SLOT_ID}`).setInputFiles({
      name: 'site.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('0123456789'),
    })
    await page.getByTestId(`technician-evidence-upload-${EVIDENCE_SLOT_ID}`).click()

    await expect(page.getByTestId('technician-evidence-message')).toContainText('等待扫描与机器校验')
    await expect(page.getByTestId(`technician-evidence-slot-${EVIDENCE_SLOT_ID}`)).toContainText('Revision 1 · STORED')
    await expect(page.getByTestId('technician-online-evidence')).toContainText('不后台重试')
  })

  test('M265-01：只提交已校验版本 ID，由服务器冻结引用与摘要后完成任务', async ({ page }) => {
    await stubTechnicianContext(page, { taskStatus: 'RUNNING', validatedEvidence: true })
    await loginWithLocalKeycloak(page)
    await navigateTechnician(page, `/technician-portal/tasks/${TASK_ID}`)

    await page.getByTestId('technician-task-complete').click()

    await expect(page.getByTestId('technician-task-submission-message')).toContainText('输入版本引用均已冻结')
    await expect(page.getByTestId('technician-task-detail-status')).toHaveText('COMPLETED')
    await expect(page.getByTestId('technician-task-submission')).toContainText('服务器重新读取并冻结')
  })
})
