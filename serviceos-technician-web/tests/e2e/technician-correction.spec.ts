import { navigateTechnician, expect, test, type Route } from './support/fixture'

const CONTEXT = 'TECHNICIAN|NETWORK|019f90a0-2222-7f8c-9505-36fe5c0e8802'
const CASE = '019f90a0-3000-7f8c-9505-36fe5c0e8803'
const SOURCE_TASK = '019f90a0-4000-7f8c-9505-36fe5c0e8804'
const CORRECTION_TASK = '019f90a0-5000-7f8c-9505-36fe5c0e8805'
const SLOT = '019f90a0-6000-7f8c-9505-36fe5c0e8806'
const SESSION = '019f90a0-7000-7f8c-9505-36fe5c0e8807'
const ITEM = '019f90a0-8000-7f8c-9505-36fe5c0e8808'
const REVISION = '019f90a0-9000-7f8c-9505-36fe5c0e8809'
const SNAPSHOT = '019f90a0-a000-7f8c-9505-36fe5c0e8810'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('button[type="submit"]').click()
  await expect(page).toHaveURL(/\/work-orders$/)
}

test('师傅领取启动整改、补传资料并以权威 Snapshot 多轮重提', async ({ page }) => {
  let taskStatus: 'READY' | 'CLAIMED' | 'RUNNING' = 'READY'
  let taskVersion = 1
  let revisionStatus = 'STORED'
  const commandBodies: unknown[] = []

  const correction = () => ({
    correctionCaseId: CASE,
    sourceTaskId: SOURCE_TASK,
    correctionTaskId: CORRECTION_TASK,
    caseStatus: 'IN_PROGRESS',
    reasonCodes: ['IMAGE.BLUR'],
    taskStatus,
    taskVersion,
    latestResubmissionSnapshotId: null,
    resubmissionCount: 0,
  })

  await page.route('**/api/v1/technician/me/corrections**', async (route: Route) => {
    expect(route.request().headers()['x-technician-context']).toBe(CONTEXT)
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    if (path.endsWith('/corrections') && method === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([correction()]) })
      return
    }
    if (path.endsWith(`${CASE}:claim`)) {
      expect(route.request().headers()['if-match']).toBe('"1"')
      taskStatus = 'CLAIMED'; taskVersion = 2
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(correction()) })
      return
    }
    if (path.endsWith(`${CASE}:start`)) {
      expect(route.request().headers()['if-match']).toBe('"2"')
      taskStatus = 'RUNNING'; taskVersion = 3
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(correction()) })
      return
    }
    if (path.endsWith('/evidence-slots') && method === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{
        slotId: SLOT, requirementCode: 'site.photo', occurrenceKey: 'default',
        requirementName: '整改现场照片', mediaType: 'PHOTO', required: true,
        minCount: 1, maxCount: 1, status: revisionStatus === 'VALIDATED' ? 'SATISFIED' : 'MISSING',
        active: true, transition: 'UNCHANGED_ACTIVE', requiredDisposition: 'NONE',
      }]) })
      return
    }
    if (path.endsWith('/evidence-items') && method === 'GET') {
      const body = revisionStatus === 'STORED' && commandBodies.length === 0 ? [] : [{
        evidenceItemId: ITEM, taskId: SOURCE_TASK, evidenceSlotId: SLOT, itemOrdinal: 1,
        status: 'ACTIVE', createdAt: '2026-07-18T12:00:00Z', revisions: [{
          evidenceRevisionId: REVISION, revisionNumber: 2, contentDigest: 'a'.repeat(64),
          mimeType: 'image/jpeg', sizeBytes: 12, status: revisionStatus,
          createdAt: '2026-07-18T12:00:00Z',
        }],
      }]
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
      return
    }
    if (path.endsWith('/upload-sessions') && method === 'POST') {
      const body = route.request().postDataJSON()
      commandBodies.push(body)
      expect(body).not.toHaveProperty('sourceTaskId')
      expect(body).not.toHaveProperty('correctionTaskId')
      expect(body).not.toHaveProperty('uploader')
      expect(body).not.toHaveProperty('offlineFlag')
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        uploadSessionId: SESSION, evidenceSlotId: SLOT, evidenceItemId: null, status: 'CREATED',
        uploadMethod: 'PUT', uploadUrl: 'https://upload.serviceos.test/once', requiredHeaders: {},
        uploadAuthorizationExpiresAt: '2026-07-18T12:01:00Z', sessionExpiresAt: '2026-07-18T12:10:00Z',
      }) })
      return
    }
    if (path.endsWith(':finalize') && method === 'POST') {
      const body = route.request().postDataJSON()
      commandBodies.push(body)
      expect(body).toEqual({ actualSha256: expect.stringMatching(/^[0-9a-f]{64}$/), finalizeCommandId: expect.any(String) })
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        evidenceItemId: ITEM, taskId: SOURCE_TASK, evidenceSlotId: SLOT, itemOrdinal: 1,
        status: 'ACTIVE', createdAt: '2026-07-18T12:00:00Z', revisions: [{
          evidenceRevisionId: REVISION, revisionNumber: 2, contentDigest: 'a'.repeat(64),
          mimeType: 'image/jpeg', sizeBytes: 12, status: 'STORED', createdAt: '2026-07-18T12:00:00Z',
        }],
      }) })
      return
    }
    if (path.endsWith('/evidence-set-snapshots') && method === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ memberRevisionIds: [REVISION] })
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        evidenceSetSnapshotId: SNAPSHOT, taskId: SOURCE_TASK, purpose: 'TASK_SUBMISSION',
        memberCount: 1, contentDigest: 'b'.repeat(64), createdAt: '2026-07-18T12:00:00Z',
        members: [{ evidenceSlotId: SLOT, evidenceItemId: ITEM, evidenceRevisionId: REVISION,
          revisionNumber: 2, revisionStatus: 'VALIDATED', contentDigest: 'a'.repeat(64), memberOrdinal: 1 }],
      }) })
      return
    }
    if (path.endsWith(`${CASE}:resubmit`) && method === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ evidenceSetSnapshotId: SNAPSHOT })
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        ...correction(), caseStatus: 'RESUBMITTED', latestResubmissionSnapshotId: SNAPSHOT, resubmissionCount: 1,
      }) })
      return
    }
    await route.fallback()
  })
  await page.route('https://upload.serviceos.test/once', async (route) => {
    expect(route.request().headers()['authorization']).toBeUndefined()
    expect(route.request().headers()['x-technician-context']).toBeUndefined()
    await route.fulfill({ status: 200, body: '' })
  })

  await login(page)
  await navigateTechnician(page, `/technician-portal/corrections/${CASE}`)
  await expect(page.getByTestId('technician-correction-detail')).toBeVisible()
  await expect(page.getByTestId('technician-correction-reasons')).toContainText('IMAGE.BLUR')
  await page.getByTestId('technician-correction-lifecycle').click()
  await expect(page.getByTestId('technician-correction-task-status')).toHaveText(/已认领|CLAIMED/)
  await page.getByTestId('technician-correction-lifecycle').click()
  await expect(page.getByTestId('technician-correction-task-status')).toHaveText(/处理中|计时中|RUNNING/)

  await page.getByTestId(`technician-correction-file-${SLOT}`).setInputFiles({
    name: 'fix.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('correction-image'),
  })
  await page.getByTestId(`technician-correction-upload-${SLOT}`).click()
  await expect(page.getByTestId('technician-correction-message')).toContainText('等待服务器扫描')

  revisionStatus = 'VALIDATED'
  await page.getByTestId('technician-correction-refresh').click()
  await expect(page.getByTestId(`technician-correction-slot-${SLOT}`)).toContainText(/已校验|VALIDATED/)
  await page.getByTestId('technician-correction-resubmit').click()
  await expect(page.getByTestId('technician-correction-message')).toContainText('第 1 次重提')
  await expect(page.getByTestId('technician-correction-task-status')).toHaveText(/处理中|计时中|RUNNING/)

  // 第二轮不能突破 Slot maxCount 创建新 Item，必须在既有整改 Item 上追加 Revision。
  revisionStatus = 'STORED'
  await page.getByTestId(`technician-correction-file-${SLOT}`).setInputFiles({
    name: 'fix-round-2.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('correction-image-round-2'),
  })
  await page.getByTestId(`technician-correction-upload-${SLOT}`).click()
  await expect.poll(() => commandBodies.length).toBe(4)
  expect(commandBodies[2]).toMatchObject({ evidenceItemId: ITEM })
  await expect(page.getByTestId('technician-correction-task-status')).toHaveText(/处理中|计时中|RUNNING/)
})
