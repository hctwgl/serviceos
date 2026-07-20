import { createHash, randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

/** 与 Backend BydCpimSignatureVerifier 一致：AppSecret&Nonce&Cur_Time&sortedParams */
function signBydCpimPayload(
  appSecret: string,
  nonce: string,
  currentDate: string,
  businessParameters: Record<string, string>,
) {
  const params = Object.keys(businessParameters)
    .sort()
    .map((key) => `${key}=${businessParameters[key]}`)
    .join('&')
  const source = `${appSecret}&${nonce}&${currentDate}&${params}`
  return createHash('sha256').update(source, 'utf8').digest('hex')
}

function asiaShanghaiDateTimeNow() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(new Date())
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')} ${value('hour')}:${value('minute')}:${value('second')}`
}

function asiaShanghaiDateToday() {
  return asiaShanghaiDateTimeNow().slice(0, 10)
}

/**
 * 权威区「打开任务：taskType / taskId」链接。
 * M152 起 TASKS 区块另有「打开区块任务：taskType / kind / status / taskId」；
 * M157 起 SLA 关联任务为「SLA / slaRef / taskId」。
 * 不能再用裸 taskId 正则，否则 strict mode 会命中多条同目标链接。
 */
function authorityTaskDetailLinkName(taskId: string) {
  return new RegExp(`^[^/]+\\s*/\\s*${taskId}$`)
}

/** M157：SLA → Task 深链三段标签。 */
function slaRelatedTaskLinkName(slaRef: string, taskId: string) {
  return new RegExp(`^SLA\\s*/\\s*${slaRef}\\s*/\\s*${taskId}$`)
}

/** M148：整改队列按 IN_PROGRESS + sourceReviewCaseId 收窄，避免历史页遮挡本轮 Case。 */
async function openInProgressCorrectionFromFilteredQueue(
  hostPage: import('@playwright/test').Page,
  sourceReviewCaseId: string,
  missingMessage: string,
) {
  const correctionPage = await hostPage.context().newPage()
  await correctionPage.goto(new URL('/corrections', hostPage.url()).toString())
  await expect(correctionPage.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()
  await correctionPage.getByLabel('correction status filter').selectOption('IN_PROGRESS')
  await correctionPage
    .getByLabel('correction sourceReviewCaseId filter')
    .fill(sourceReviewCaseId)
  const correctionQueuePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/correction-cases') &&
      new URL(response.url()).searchParams.get('status') === 'IN_PROGRESS' &&
      new URL(response.url()).searchParams.get('sourceReviewCaseId') ===
        sourceReviewCaseId,
  )
  await correctionPage.getByRole('button', { name: '查询' }).click()
  const correctionQueueResponse = await correctionQueuePromise
  expect(correctionQueueResponse.status()).toBe(200)
  const correctionQueue = (await correctionQueueResponse.json()) as {
    items: Array<{
      correctionCaseId: string
      projectId: string
      taskId: string
      sourceReviewCaseId: string
      correctionTaskId: string | null
      status: string
    }>
  }
  const correction = correctionQueue.items.find(
    (item) => item.sourceReviewCaseId === sourceReviewCaseId,
  )
  expect(correction, missingMessage).toBeTruthy()
  expect(correction).toMatchObject({ status: 'IN_PROGRESS' })
  expect(correction?.correctionTaskId, '驳回未自动创建整改 Task').toBeTruthy()

  // M176：整改队列关联资源深链（源审核 / 整改 Task），再进入整改详情。
  const queueSourceReviewPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/review-cases/${sourceReviewCaseId}`,
  )
  await correctionPage
    .locator('.correction-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开源审核\\s+${sourceReviewCaseId}`),
    })
    .click()
  expect((await queueSourceReviewPromise).status()).toBe(200)
  await expect(correctionPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await correctionPage.goBack()
  await expect(correctionPage.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()

  const queueCorrectionTaskPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/tasks/${correction!.correctionTaskId}`,
  )
  await correctionPage
    .locator('.correction-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开整改任务\\s+${correction!.correctionTaskId}`),
    })
    .click()
  expect((await queueCorrectionTaskPromise).status()).toBe(200)
  await expect(correctionPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await correctionPage.goBack()
  await expect(correctionPage.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()

  // M180：整改队列剩余 Accepted 关联字段（项目 / 来源任务）。
  const queueProjectPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${correction!.projectId}`,
  )
  await correctionPage
    .locator('.correction-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开项目\\s+${correction!.projectId}`),
    })
    .first()
    .click()
  expect((await queueProjectPromise).status()).toBe(200)
  await expect(correctionPage.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  await correctionPage.goBack()
  await expect(correctionPage.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()

  const queueSourceTaskPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${correction!.taskId}`,
  )
  await correctionPage
    .locator('.correction-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开来源任务\\s+${correction!.taskId}`),
    })
    .click()
  expect((await queueSourceTaskPromise).status()).toBe(200)
  await expect(correctionPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await correctionPage.goBack()
  await expect(correctionPage.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()

  await correctionPage
    .getByRole('link', { name: `打开整改案例 ${correction!.correctionCaseId}` })
    .click()
  await expect(correctionPage.getByRole('heading', { name: '整改案例' })).toBeVisible()
  return { correctionPage, correction: correction! }
}

async function loginWithLocalKeycloak(page: import('@playwright/test').Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()

  // Keycloak DOM 文案会随语言包变化，使用稳定表单字段名完成本地开发账号登录。
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('input[type="submit"], button[type="submit"]').click()

  // 兼容首次使用旧本地 realm 容器时 Keycloak 触发的资料补全；新导入 realm 已预置 email。
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill('developer@serviceos.local')
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }

  await expect(page).toHaveURL(/\/work-orders$/)
}


async function fillWorkOrderProjectFilter(page: import('@playwright/test').Page, projectId: string) {
  const input = page.getByLabel('所属项目筛选')
  await input.click()
  await input.fill('')
  // Ant Design Vue Input：逐字输入确保 v-model 同步，避免 fill() 未触发 update:value。
  await input.pressSequentially(projectId, { delay: 15 })
  await expect(input).toHaveValue(projectId)
}

async function selectWorkOrderStatus(page: import('@playwright/test').Page, statusLabel: string) {
  await page.getByLabel('工单状态筛选').click()
  await page.getByRole('option', { name: statusLabel, exact: true }).click()
}

async function expectWorkOrderStatusSelected(
  page: import('@playwright/test').Page,
  statusLabel: string,
) {
  await expect(page.getByLabel('工单状态筛选')).toContainText(statusLabel)
}

async function clickWorkOrderQuery(page: import('@playwright/test').Page) {
  await page.getByTestId('query-panel').getByRole('button', { name: '查询' }).click()
}

async function locateTaskCommandCard(page: import('@playwright/test').Page) {
  return page.locator('.ant-card').filter({ hasText: '当前任务命令' })
}

async function clickTaskCommandButton(
  page: import('@playwright/test').Page,
  name: string | RegExp,
) {
  await (await locateTaskCommandCard(page)).getByRole('button', { name }).click()
}

async function expectTaskCommandButton(
  page: import('@playwright/test').Page,
  name: string | RegExp,
) {
  await expect((await locateTaskCommandCard(page)).getByRole('button', { name })).toBeVisible()
}


async function prepareOpenReviewCase(
  page: import('@playwright/test').Page,
  workOrderCode: string,
  taskId: string,
  sourceId: string,
  fileName: string,
) {
  await loginWithLocalKeycloak(page)
  await page.getByRole('link', { name: workOrderCode }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill(sourceId)
  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  expect((await assignmentResponsePromise).status()).toBe(200)

  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await clickTaskCommandButton(page, '启动任务')
  expect((await startResponsePromise).status()).toBe(200)

  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('cell', { name: /completion\.photo|完工/ })).toBeVisible({
    timeout: 30_000,
  })

  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  await page.getByLabel('文件').setInputFiles({
    name: fileName,
    mimeType: 'image/png',
    buffer: png,
  })
  const finalizeResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const finalizeResponse = await finalizeResponsePromise
  expect(finalizeResponse.status()).toBe(201)
  const evidenceItem = (await finalizeResponse.json()) as {
    revisions: Array<{ evidenceRevisionId: string }>
  }
  const evidenceRevisionId = evidenceItem.revisions.at(-1)?.evidenceRevisionId
  expect(evidenceRevisionId, 'Finalize 未返回审核验证 EvidenceRevision').toBeTruthy()

  const orchestrationHeader = page
    .getByRole('heading', { name: '表单 / 资料编排' })
    .locator('..')
  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: evidenceRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  const snapshotResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  expect((await snapshotResponsePromise).status()).toBe(201)

  const reviewCreateResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const reviewCreateResponse = await reviewCreateResponsePromise
  expect(reviewCreateResponse.status()).toBe(201)
  const reviewCase = (await reviewCreateResponse.json()) as {
    reviewCaseId: string
    status: string
    evidenceSetSnapshotId: string
  }
  expect(reviewCase.status).toBe('OPEN')
  expect(reviewCase.evidenceSetSnapshotId).toBeTruthy()

  const reviewHref = await page
    .getByRole('link', { name: new RegExp(`打开审核案例 ${reviewCase.reviewCaseId}`) })
    .getAttribute('href')
  expect(reviewHref, '审核案例深链缺失').toBeTruthy()
  const reviewPage = await page.context().newPage()
  await reviewPage.goto(new URL(reviewHref!, page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(reviewPage.getByText(/^(OPEN|待处理)$/)).toBeVisible()

  return { reviewCase, reviewPage }
}

test('真实 OIDC 登录后可读取核心投影并完成 Task 分配领取释放写链路', async ({ page }) => {
  test.setTimeout(120_000)
  await loginWithLocalKeycloak(page)
  await expect(page.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  await expect(page.getByText('加载中…')).toHaveCount(0)

  // M150：运营异常队列 Accepted OpenAPI 筛选（默认 OPEN；切换 ACKNOWLEDGED 仍 200）。
  await page.getByRole('link', { name: '运营异常' }).click()
  await expect(page.getByRole('heading', { name: '运营异常', level: 1 })).toBeVisible()
  await expect(page.getByLabel('exception status filter')).toHaveValue('OPEN')
  await page.getByLabel('exception status filter').selectOption('ACKNOWLEDGED')
  await page.getByLabel('exception severity filter').selectOption('P1')
  const exceptionFilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/operational-exceptions') &&
      new URL(response.url()).searchParams.get('status') === 'ACKNOWLEDGED' &&
      new URL(response.url()).searchParams.get('severity') === 'P1',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await exceptionFilterPromise).status()).toBe(200)

  // M158：入站 Envelope 授权队列（默认 RECEIVED；COMPLETED + projectId 深链详情）。
  const pilotProjectId = '10000000-0000-4000-8000-000000000001'
  await page.getByRole('link', { name: '入站队列' }).click()
  await expect(page.getByRole('heading', { name: '入站 Envelope 队列' })).toBeVisible()
  await expect(page.getByLabel('inbound processingStatus filter')).toHaveValue('RECEIVED')
  const inboundDefaultPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/inbound-envelopes' &&
      new URL(response.url()).searchParams.get('processingStatus') === 'RECEIVED',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await inboundDefaultPromise).status()).toBe(200)

  await page.getByLabel('inbound processingStatus filter').selectOption('COMPLETED')
  await page.getByLabel('inbound projectId filter').fill(pilotProjectId)
  await page.getByLabel('inbound messageType filter').selectOption('CREATE_WORK_ORDER')
  const inboundCompletedPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/inbound-envelopes' &&
      new URL(response.url()).searchParams.get('processingStatus') === 'COMPLETED' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('messageType') === 'CREATE_WORK_ORDER',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await inboundCompletedPromise).status()).toBe(200)
  await expect(page.getByText('打开入站：')).toBeVisible()

  // M177：入站队列关联资源 → 项目详情。
  const inboundQueueProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${pilotProjectId}`,
  )
  await page
    .locator('.inbound-queue-cross-links')
    .getByRole('link', { name: new RegExp(`打开项目\\s+${pilotProjectId}`) })
    .first()
    .click()
  expect((await inboundQueueProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  // 表单筛选不写入 route.query；返回后需重放 COMPLETED 筛选再打开入站详情。
  await page.goto(new URL('/integration/inbound', page.url()).toString())
  await expect(page.getByRole('heading', { name: '入站 Envelope 队列' })).toBeVisible()
  await page.getByLabel('inbound processingStatus filter').selectOption('COMPLETED')
  await page.getByLabel('inbound projectId filter').fill(pilotProjectId)
  await page.getByLabel('inbound messageType filter').selectOption('CREATE_WORK_ORDER')
  const inboundRefilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/inbound-envelopes' &&
      new URL(response.url()).searchParams.get('processingStatus') === 'COMPLETED' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('messageType') === 'CREATE_WORK_ORDER',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await inboundRefilterPromise).status()).toBe(200)

  const inboundDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/v1\/inbound-envelopes\/[0-9a-f-]+$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('link', { name: /^CREATE_WORK_ORDER\s*\// }).first().click()
  expect((await inboundDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '入站 Envelope' })).toBeVisible()

  // M151：目录/SLA Accepted OpenAPI 筛选补齐（projectId / activeOn / SUCCEEDED）。
  // 工单目录产品化后以 route.query 水合 + 查询面板为主；用深链证明 projectId 查询参数。
  const workOrderFilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/work-orders' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId,
  )
  await page.goto(new URL(`/work-orders?projectId=${pilotProjectId}`, page.url()).toString())
  await expect(page.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  await expect(page.getByLabel('所属项目筛选')).toHaveValue(pilotProjectId)
  expect((await workOrderFilterPromise).status()).toBe(200)

  // M178：工单目录关联资源 → 项目详情（表格所属项目列）。
  const workOrderDirectoryProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${pilotProjectId}`,
  )
  await page
    .getByRole('main')
    .getByRole('table')
    .getByRole('link', { name: new RegExp(`打开项目\\s+${pilotProjectId}`) })
    .first()
    .click()
  expect((await workOrderDirectoryProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  await page.getByRole('link', { name: '任务目录' }).click()
  await expect(page.getByRole('heading', { name: /任务中心|任务目录/, level: 1 })).toBeVisible()
  await page.getByLabel('task projectId filter').fill(pilotProjectId)
  await page.getByLabel('task status filter').selectOption('SUCCEEDED')
  const taskFilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/tasks' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'SUCCEEDED',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await taskFilterPromise).status()).toBe(200)

  // M178：任务目录关联资源 → 项目详情（用 READY 保证试点 HUMAN Task 可见）。
  await page.getByLabel('task status filter').selectOption('READY')
  const taskReadyPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/tasks' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'READY',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await taskReadyPromise).status()).toBe(200)
  const taskDirectoryProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${pilotProjectId}`,
  )
  await page
    .locator('.task-directory-cross-links')
    .getByRole('link', { name: new RegExp(`打开项目\\s+${pilotProjectId}`) })
    .first()
    .click()
  expect((await taskDirectoryProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()

  await page.getByRole('link', { name: 'SLA 工作台' }).click()
  await expect(page.getByRole('heading', { name: 'SLA 工作台', level: 1 })).toBeVisible()
  await page.getByLabel('sla projectId filter').fill(pilotProjectId)
  const slaFilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/sla-instances' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'BREACHED',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await slaFilterPromise).status()).toBe(200)

  // M157：SLA 工作台关联任务深链（Pilot SLA 为 RUNNING，与默认 BREACHED 筛选区分）。
  const pilotTaskId = '70000000-0000-4000-8000-000000000001'
  await page.getByLabel('sla status filter').selectOption('RUNNING')
  const slaRunningPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/sla-instances' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'RUNNING',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await slaRunningPromise).status()).toBe(200)
  await expect(page.getByText('打开关联任务：')).toBeVisible()
  const slaQueueTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${pilotTaskId}`,
  )
  await page
    .getByRole('link', {
      name: slaRelatedTaskLinkName('PILOT_RESPONSE', pilotTaskId),
    })
    .click()
  expect((await slaQueueTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await page.getByRole('link', { name: 'SLA 工作台' }).click()
  await expect(page.getByRole('heading', { name: 'SLA 工作台', level: 1 })).toBeVisible()

  await page.getByRole('link', { name: '项目目录' }).click()
  await expect(page.getByRole('heading', { name: /项目列表|项目中心|授权项目目录|项目目录/, level: 1 })).toBeVisible()
  const activeOn = new Date().toISOString().slice(0, 10)
  await page.getByLabel('project activeOn filter').fill(activeOn)
  const projectFilterPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/projects' &&
      new URL(response.url()).searchParams.get('activeOn') === activeOn &&
      new URL(response.url()).searchParams.get('status') === 'ACTIVE',
  )
  await page.getByRole('button', { name: '查询' }).click()
  expect((await projectFilterPromise).status()).toBe(200)

  await page.getByRole('link', { name: '工单目录' }).click()
  await expect(page.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  // 先按固定 Pilot projectId 收窄，避免本地冒烟累积动态工单把基线单顶出 20 页窗口。
  const pilotDirectoryPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/work-orders' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId,
  )
  await page.goto(new URL(`/work-orders?projectId=${pilotProjectId}`, page.url()).toString())
  await expect(page.getByLabel('所属项目筛选')).toHaveValue(pilotProjectId)
  expect((await pilotDirectoryPromise).status()).toBe(200)

  const pilotLink = page.getByRole('main').getByRole('table').getByRole('link', { name: 'ADMIN-PILOT-001', exact: true })
  // 同项目内仍可能分页；通过真实游标查找，保持目录查询证明。
  for (let pageNo = 0; pageNo < 20 && (await pilotLink.count()) === 0; pageNo += 1) {
    const nextButton = page.getByRole('button', { name: '下一页' })
    expect(await nextButton.isEnabled(), '20 页内未找到固定 Admin 试点工单').toBe(true)
    const nextResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/work-orders') &&
        response.url().includes('cursor='),
    )
    await nextButton.click()
    expect((await nextResponsePromise).status()).toBe(200)
  }
  await expect(pilotLink).toBeVisible()
  await pilotLink.click()

  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  // 产品化工作区：概览配置来源；SLA/活动/诊断分属独立页签（Card title 非 heading）。
  await expect(page.getByText('配置来源', { exact: true }).first()).toBeVisible()
  await page.getByRole('tab', { name: 'SLA 与异常' }).click()
  await expect(page.getByText('工单 SLA 实例', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PILOT_RESPONSE', exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '履约任务' }).click()
  await expect(page.getByRole('cell', { name: '试点勘测', exact: true }).first()).toBeVisible()
  await page.getByRole('tab', { name: '活动记录' }).click()
  await expect(page.getByText('核心时间线', { exact: true }).first()).toBeVisible()
  await page.getByRole('tab', { name: '技术诊断' }).click()
  await expect(page.getByRole('heading', { name: /工单权威事实/ })).toBeVisible()
  await page.getByRole('tab', { name: '概览' }).click()

  // M165：工作区异常摘要 → 运营异常队列 query 水合（workOrderId + status=OPEN）。
  const pilotWorkOrderId = '40000000-0000-4000-8000-000000000001'
  await expect(page.getByRole('link', { name: '打开异常队列' })).toBeVisible()
  const exceptionHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/operational-exceptions' &&
      new URL(response.url()).searchParams.get('workOrderId') === pilotWorkOrderId &&
      new URL(response.url()).searchParams.get('status') === 'OPEN',
  )
  await page.getByRole('link', { name: '打开异常队列' }).click()
  expect((await exceptionHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '运营异常', level: 1 })).toBeVisible()
  await expect(page.getByLabel('exception workOrderId filter')).toHaveValue(pilotWorkOrderId)
  await expect(page.getByLabel('exception status filter')).toHaveValue('OPEN')
  await expect(page).toHaveURL(
    new RegExp(`/exceptions\\?.*workOrderId=${pilotWorkOrderId}`),
  )

  // M177：异常队列关联资源 → 人工接管任务；M175：异常详情 handlingTaskId 深链。
  const pilotExceptionId = 'a1000000-0000-4000-8000-000000000001'
  const pilotHandlingTaskId = '71000000-0000-4000-8000-000000000001'
  await expect(
    page.getByRole('link', { name: `打开异常 ${pilotExceptionId}` }),
  ).toBeVisible({ timeout: 30_000 })
  const queueHandlingTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${pilotHandlingTaskId}`,
  )
  await page
    .locator('.exception-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开人工接管任务\\s+${pilotHandlingTaskId}`),
    })
    .click()
  expect((await queueHandlingTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { name: '运营异常', level: 1 })).toBeVisible()

  const exceptionDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/operational-exceptions/${pilotExceptionId}`,
  )
  await page.getByRole('link', { name: `打开异常 ${pilotExceptionId}` }).click()
  expect((await exceptionDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '运营异常详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/exceptions/${pilotExceptionId}$`))

  const handlingTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${pilotHandlingTaskId}`,
  )
  await page
    .locator('.exception-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开人工接管任务\\s+${pilotHandlingTaskId}`),
    })
    .click()
  expect((await handlingTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/tasks/${pilotHandlingTaskId}$`))

  // M169：专项队列 route.query 水合（对齐 ExceptionQueue；侧栏直达默认值不变）。
  const pilotTaskIdForQueue = '70000000-0000-4000-8000-000000000001'
  const reviewHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/review-cases' &&
      new URL(response.url()).searchParams.get('status') === 'OPEN' &&
      new URL(response.url()).searchParams.get('taskId') === pilotTaskIdForQueue,
  )
  await page.goto(`/reviews?status=OPEN&taskId=${pilotTaskIdForQueue}`)
  expect((await reviewHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /审核中心|审核队列/, level: 1 })).toBeVisible()
  await expect(page.getByLabel('review status filter')).toHaveValue('OPEN')
  await expect(page.getByLabel('review taskId filter')).toHaveValue(pilotTaskIdForQueue)

  const correctionHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/correction-cases' &&
      new URL(response.url()).searchParams.get('status') === 'IN_PROGRESS' &&
      new URL(response.url()).searchParams.get('taskId') === pilotTaskIdForQueue,
  )
  await page.goto(`/corrections?status=IN_PROGRESS&taskId=${pilotTaskIdForQueue}`)
  expect((await correctionHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /整改中心|整改跟踪/, level: 1 })).toBeVisible()
  await expect(page.getByLabel('correction status filter')).toHaveValue('IN_PROGRESS')
  await expect(page.getByLabel('correction taskId filter')).toHaveValue(pilotTaskIdForQueue)

  const inboundHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/inbound-envelopes' &&
      new URL(response.url()).searchParams.get('processingStatus') === 'COMPLETED' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('messageType') === 'CREATE_WORK_ORDER',
  )
  await page.goto(
    `/integration/inbound?processingStatus=COMPLETED&projectId=${pilotProjectId}&messageType=CREATE_WORK_ORDER`,
  )
  expect((await inboundHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '入站 Envelope 队列' })).toBeVisible()
  await expect(page.getByLabel('inbound processingStatus filter')).toHaveValue('COMPLETED')
  await expect(page.getByLabel('inbound projectId filter')).toHaveValue(pilotProjectId)
  await expect(page.getByLabel('inbound messageType filter')).toHaveValue('CREATE_WORK_ORDER')

  const outboundHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/outbound-deliveries' &&
      new URL(response.url()).searchParams.get('status') === 'UNKNOWN' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId,
  )
  await page.goto(`/integration/outbound?status=UNKNOWN&projectId=${pilotProjectId}`)
  expect((await outboundHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '外发交付队列' })).toBeVisible()
  await expect(page.getByLabel('outbound status filter')).toHaveValue('UNKNOWN')
  await expect(page.getByLabel('outbound projectId filter')).toHaveValue(pilotProjectId)

  // M170：目录页 route.query 水合（工单/任务/SLA/项目）。
  const workOrderHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/work-orders' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'ACTIVE',
  )
  await page.goto(`/work-orders?projectId=${pilotProjectId}&status=ACTIVE`)
  expect((await workOrderHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  await expect(page.getByLabel('所属项目筛选')).toHaveValue(pilotProjectId)
  await expectWorkOrderStatusSelected(page, '处理中')

  const taskHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/tasks' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'SUCCEEDED',
  )
  await page.goto(`/tasks?projectId=${pilotProjectId}&status=SUCCEEDED`)
  expect((await taskHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /任务中心|任务目录/, level: 1 })).toBeVisible()
  await expect(page.getByLabel('task projectId filter')).toHaveValue(pilotProjectId)
  await expect(page.getByLabel('task status filter')).toHaveValue('SUCCEEDED')

  const slaHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/sla-instances' &&
      new URL(response.url()).searchParams.get('projectId') === pilotProjectId &&
      new URL(response.url()).searchParams.get('status') === 'RUNNING',
  )
  await page.goto(`/sla?projectId=${pilotProjectId}&status=RUNNING`)
  expect((await slaHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'SLA 工作台', level: 1 })).toBeVisible()
  await expect(page.getByLabel('sla projectId filter')).toHaveValue(pilotProjectId)
  await expect(page.getByLabel('sla status filter')).toHaveValue('RUNNING')

  const projectHydrateActiveOn = new Date().toISOString().slice(0, 10)
  const projectHydratePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/projects' &&
      new URL(response.url()).searchParams.get('activeOn') === projectHydrateActiveOn &&
      new URL(response.url()).searchParams.get('status') === 'ACTIVE',
  )
  await page.goto(`/projects?status=ACTIVE&activeOn=${projectHydrateActiveOn}`)
  expect((await projectHydratePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /项目列表|项目中心|授权项目目录|项目目录/, level: 1 })).toBeVisible()
  await expect(page.getByLabel('project activeOn filter')).toHaveValue(projectHydrateActiveOn)
  await expect(page.getByLabel('project status filter')).toHaveValue('ACTIVE')

  await page.goto(`/work-orders/${pilotWorkOrderId}`)
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // 产品化工作区：用履约任务页签深链替代旧「打开*资源」旁路链接。
  const taskId = '70000000-0000-4000-8000-000000000001'
  await page.getByRole('tab', { name: '履约任务' }).click()
  const workspaceTaskDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${taskId}`,
  )
  await page.getByRole('link', { name: /试点勘测|PILOT_SURVEY/ }).first().click()
  expect((await workspaceTaskDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}$`))
  await page.goto('/work-orders/40000000-0000-4000-8000-000000000001')
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // M157：工作区摘要项目深链 → 项目详情。
  const workspaceProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${pilotProjectId}`,
  )
  await page
    .getByRole('link', { name: new RegExp(`打开项目\\s+${pilotProjectId}`) })
    .first()
    .click()
  expect((await workspaceProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/projects/${pilotProjectId}$`))
  await page.goto('/work-orders/40000000-0000-4000-8000-000000000001')
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // 夹具不预置 ACTIVE 候选；先通过 Admin 页面调用 MANUAL assign-candidates，
  // 再由服务端刷新 allowed-actions，使后续 claim 真正依赖本轮候选快照。
  // 概览「当前任务命令」与页头主操作可能同时渲染领取；以命令面板为准。
  const taskCommandCard = page.locator('.ant-card').filter({ hasText: '当前任务命令' })
  await expect(taskCommandCard.getByRole('button', { name: '领取任务' })).toHaveCount(0)

  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  const allowedActionsRefreshPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/allowed-actions`),
  )
  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill('admin-pilot-e2e')
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  const assignmentResponse = await assignmentResponsePromise
  expect(assignmentResponse.status()).toBe(200)
  expect(await assignmentResponse.json()).toMatchObject({
    taskId,
    candidateCount: 1,
  })
  await allowedActionsRefreshPromise
  await expect(taskCommandCard.getByRole('button', { name: '领取任务' })).toBeVisible()

  // 继续通过页面真实调用后端命令，证明 allowed-actions、数据库 RoleGrant、候选责任、
  // If-Match 版本与 Idempotency-Key 共同生效；release 后回到 READY，夹具可重复执行。
  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  const claimResponse = await claimResponsePromise
  expect(claimResponse.status()).toBe(200)
  await expect(taskCommandCard.getByRole('button', { name: '释放任务' })).toBeVisible()

  const releaseResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:release`),
  )
  await taskCommandCard.getByRole('button', { name: '释放任务' }).click()
  const releaseResponse = await releaseResponsePromise
  expect(releaseResponse.status()).toBe(200)
  await expect(taskCommandCard.getByRole('button', { name: '领取任务' })).toBeVisible()
})

test('真实 OIDC 登录后可完成 Task 并可靠推进 Workflow 与 WorkOrder', async ({ page }) => {
  test.setTimeout(90_000)
  const workOrderCode = process.env.ADMIN_PILOT_COMPLETION_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_COMPLETION_TASK_ID
  expect(workOrderCode, '缺少动态终态验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态终态验证 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  // 完工任务在履约任务页签表格中，不在概览 SLA 单元格。
  await page.getByRole('tab', { name: '履约任务' }).click()
  await expect(page.getByRole('cell', { name: /PILOT_COMPLETION|试点完工/ }).first()).toBeVisible()
  await page.getByRole('tab', { name: '概览' }).click()
  const taskCommandCard = page.locator('.ant-card').filter({ hasText: '当前任务命令' })

  // 每轮使用全新 Task，按服务端 allowed-actions 依次推进，避免通过 SQL 重置终态事实。
  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill('admin-pilot-completion-e2e')
  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  expect((await assignmentResponsePromise).status()).toBe(200)

  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await clickTaskCommandButton(page, '启动任务')
  expect((await startResponsePromise).status()).toBe(200)

  // M116 页面提交精确锁定的 FormVersion；VALIDATED submission 会把不可变引用和摘要
  // 回填给 complete 面板，浏览器不自行拼接或猜测完成结果。
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'admin.pilot-completion-form' })).toBeVisible()
  await page.getByLabel('values JSON').fill('{"completion.note":"ADMIN_PILOT_E2E"}')
  const formResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/form-submissions`),
  )
  await page.getByRole('button', { name: 'submitTaskForm' }).click()
  const formResponse = await formResponsePromise
  expect(formResponse.status()).toBe(201)
  const submission = (await formResponse.json()) as {
    submissionId: string
    contentDigest: string
    validationStatus: string
  }
  expect(submission.validationStatus).toBe('VALIDATED')
  // prepared-complete 会刷新 allowed-actions；等待 complete 面板出现再断言回填。
  await expect(page.getByLabel('resultRef')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByLabel('resultRef')).toHaveValue(
    `form-submission://${submission.submissionId}`,
    { timeout: 30_000 },
  )
  await expect(page.getByLabel('resultDigest')).toHaveValue(submission.contentDigest)

  // M167：Task 面板 → 表单提交详情（新页签，保留双输入面板状态）。
  const submissionLink = page.locator('.task-forms-submission-links').getByRole('link', {
    name: new RegExp(
      `task\\s*/\\s*FormSubmission\\s*/\\s*(VALIDATED|已校验)\\s*/\\s*${submission.submissionId}`,
    ),
  })
  const submissionHref = await submissionLink.getAttribute('href')
  expect(submissionHref, '表单提交深链缺失').toBeTruthy()
  const submissionPage = await page.context().newPage()
  const taskSubmissionDetailPromise = submissionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/form-submissions/${submission.submissionId}`,
  )
  await submissionPage.goto(new URL(submissionHref!, page.url()).toString())
  expect((await taskSubmissionDetailPromise).status()).toBe(200)
  await expect(submissionPage.getByRole('heading', { name: '表单提交详情' })).toBeVisible()
  await submissionPage.close()

  // M38/M39/M40 继续通过真实页面执行 Begin→本地私有 PUT→Finalize，
  // 等待扫描与机器校验 worker 将不可变 Revision 推进至 VALIDATED 后再冻结 Snapshot。
  await expect(page.getByRole('cell', { name: /completion\.photo|完工/ })).toBeVisible({
    timeout: 30_000,
  })
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  await page.getByLabel('文件').setInputFiles({
    name: 'admin-pilot-completion.png',
    mimeType: 'image/png',
    buffer: png,
  })
  const finalizeResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const finalizeResponse = await finalizeResponsePromise
  expect(finalizeResponse.status()).toBe(201)
  const evidenceItem = (await finalizeResponse.json()) as {
    evidenceItemId: string
    revisions: Array<{ evidenceRevisionId: string }>
  }
  const evidenceRevisionId = evidenceItem.revisions.at(-1)?.evidenceRevisionId
  expect(evidenceRevisionId, 'Finalize 未返回 EvidenceRevision').toBeTruthy()

  const orchestrationHeader = page
    .getByRole('heading', { name: '表单 / 资料编排' })
    .locator('..')
  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: evidenceRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  // M167：Task 面板 → 资料项详情（新页签，保留双输入面板状态）。
  const evidenceItemLink = page.locator('.task-forms-item-links').getByRole('link', {
    name: new RegExp(
      `task\\s*/\\s*EvidenceItem\\s*/\\s*\\S+\\s*/\\s*${evidenceItem.evidenceItemId}`,
    ),
  })
  const evidenceItemHref = await evidenceItemLink.getAttribute('href')
  expect(evidenceItemHref, '资料项深链缺失').toBeTruthy()
  const evidenceItemPage = await page.context().newPage()
  const taskEvidenceItemDetailPromise = evidenceItemPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-items/${evidenceItem.evidenceItemId}`,
  )
  await evidenceItemPage.goto(new URL(evidenceItemHref!, page.url()).toString())
  expect((await taskEvidenceItemDetailPromise).status()).toBe(200)
  await expect(evidenceItemPage.getByRole('heading', { name: '资料项详情' })).toBeVisible()
  await evidenceItemPage.close()

  const snapshotResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  const snapshotResponse = await snapshotResponsePromise
  expect(snapshotResponse.status()).toBe(201)
  const snapshot = (await snapshotResponse.json()) as {
    evidenceSetSnapshotId: string
    contentDigest: string
  }

  // M156：Task 面板深链打开资料快照详情（新页签，保留双输入面板状态）。
  const snapshotLink = page.getByRole('link', {
    name: new RegExp(`打开资料快照 ${snapshot.evidenceSetSnapshotId}`),
  })
  const snapshotHref = await snapshotLink.getAttribute('href')
  expect(snapshotHref, '资料快照深链缺失').toBeTruthy()
  const snapshotPage = await page.context().newPage()
  const snapshotDetailPromise = snapshotPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${snapshot.evidenceSetSnapshotId}`,
  )
  await snapshotPage.goto(new URL(snapshotHref!, page.url()).toString())
  expect((await snapshotDetailPromise).status()).toBe(200)
  await expect(snapshotPage.getByRole('heading', { name: '资料快照详情' })).toBeVisible()
  await expect(snapshotPage).toHaveURL(
    new RegExp(`/evidence-set-snapshots/${snapshot.evidenceSetSnapshotId}$`),
  )
  await snapshotPage.close()

  // M44/M112/M121 通过真实 Admin 页面创建 INTERNAL ReviewCase，并在独立页面完成普通 APPROVED
  // 裁决。使用同一浏览器上下文的新页签，既保留 Task 页的双输入不可变引用，也证明 OIDC 会话、
  // evidence.review 授权、幂等命令与审核详情读取能够跨页面协作。
  const reviewCreateResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const reviewCreateResponse = await reviewCreateResponsePromise
  expect(reviewCreateResponse.status()).toBe(201)
  const reviewCase = (await reviewCreateResponse.json()) as {
    reviewCaseId: string
    status: string
  }
  expect(reviewCase.status).toBe('OPEN')

  const reviewLink = page.getByRole('link', {
    name: new RegExp(`打开审核案例 ${reviewCase.reviewCaseId}`),
  })
  const reviewHref = await reviewLink.getAttribute('href')
  expect(reviewHref, '审核案例深链缺失').toBeTruthy()
  const reviewPage = await page.context().newPage()
  await reviewPage.goto(new URL(reviewHref!, page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(reviewPage.getByText(/^(OPEN|待处理)$/)).toBeVisible()

  const reviewDecisionResponsePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${reviewCase.reviewCaseId}:decide`),
  )
  await reviewPage.getByLabel('note').fill('Admin pilot evidence approved')
  await reviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  const reviewDecisionResponse = await reviewDecisionResponsePromise
  expect(reviewDecisionResponse.status()).toBe(200)
  expect(await reviewDecisionResponse.json()).toMatchObject({
    reviewCaseId: reviewCase.reviewCaseId,
    status: 'APPROVED',
  })
  await expect(reviewPage.getByText(/已裁决为\s*(APPROVED|已通过)/)).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: /APPROVED|已通过/ })).toBeVisible()
  await reviewPage.close()

  // 双输入 Task 的主结果仍是 FormSubmission；页面自动组合两份精确版本引用，
  // 不要求运营人员手工复制 UUID 或摘要，也不能让 Snapshot 覆盖表单主引用。
  await expect(page.getByLabel('resultRef')).toHaveValue(
    `form-submission://${submission.submissionId}`,
  )
  await expect(page.getByLabel('resultDigest')).toHaveValue(submission.contentDigest)
  const inputVersionRefs = JSON.parse(
    await page.getByLabel('inputVersionRefs JSON（双引用可选）').inputValue(),
  )
  expect(inputVersionRefs).toEqual([
    {
      kind: 'FORM_SUBMISSION',
      ref: `form-submission://${submission.submissionId}`,
      digest: submission.contentDigest,
    },
    {
      kind: 'EVIDENCE_SET_SNAPSHOT',
      ref: `evidence-set-snapshot://${snapshot.evidenceSetSnapshotId}`,
      digest: snapshot.contentDigest,
    },
  ])

  const completeResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:complete`),
  )
  await clickTaskCommandButton(page, '完成任务')
  const completeResponse = await completeResponsePromise
  const completeBody = await completeResponse.json()
  expect(completeResponse.status(), JSON.stringify(completeBody)).toBe(200)
  expect(completeResponse.request().postDataJSON()).toMatchObject({
    resultRef: `form-submission://${submission.submissionId}`,
    resultDigest: submission.contentDigest,
    inputVersionRefs,
  })
  expect(completeBody).toMatchObject({
    taskId,
    status: 'COMPLETED',
  })

  // M155 / M154：完结后证明表单提交详情与 Task 旁路（不得打断上方双输入 complete 面板状态）。
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // M161：产品化后核心时间线侧链移除；用受权路由直达证明 FormSubmission 可读。
  const coreTimelineFormPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/form-submissions/${submission.submissionId}`,
  )
  await page.goto(`/form-submissions/${submission.submissionId}`)
  expect((await coreTimelineFormPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '表单提交详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/form-submissions/${submission.submissionId}$`),
  )
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // M161：EvidenceSetSnapshot 详情（路由直达）。
  const coreTimelineSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${snapshot.evidenceSetSnapshotId}`,
  )
  await page.goto(`/evidence-set-snapshots/${snapshot.evidenceSetSnapshotId}`)
  expect((await coreTimelineSnapshotPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '资料快照详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/evidence-set-snapshots/${snapshot.evidenceSetSnapshotId}$`),
  )

  // 产品化资料页签不再展示「打开表单提交详情」旁路；路由直达即可。
  const workspaceFormSubmissionPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/form-submissions/${submission.submissionId}`,
  )
  await page.goto(`/form-submissions/${submission.submissionId}`)
  expect((await workspaceFormSubmissionPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '表单提交详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/form-submissions/${submission.submissionId}$`),
  )


  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  // 产品化资料页签侧链移除；任务详情路由直达。
  const workspaceFeTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${taskId}`,
  )
  await page.goto(`/tasks/${taskId}`)
  expect((await workspaceFeTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}$`))

  // M156：工作区 FORMS_EVIDENCE → 资料项详情。
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  // 产品化资料项侧链移除；资料项详情路由直达。
  const workspaceEvidenceItemPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-items/${evidenceItem.evidenceItemId}`,
  )
  await page.goto(`/evidence-items/${evidenceItem.evidenceItemId}`)
  expect((await workspaceEvidenceItemPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '资料项详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/evidence-items/${evidenceItem.evidenceItemId}$`),
  )
})

test('真实 OIDC 登录后审核驳回可进入整改队列并授权豁免整改 Task', async ({ page }) => {
  test.setTimeout(120_000)
  const workOrderCode = process.env.ADMIN_PILOT_CORRECTION_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_CORRECTION_TASK_ID
  expect(workOrderCode, '缺少动态整改验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态整改验证 Task ID').toBeTruthy()

  const { reviewCase, reviewPage } = await prepareOpenReviewCase(
    page,
    workOrderCode!,
    taskId!,
    'admin-pilot-correction-e2e',
    'admin-pilot-correction.png',
  )

  // M180：审核队列 → 资料快照（Accepted ReviewCaseQueueItem.evidenceSetSnapshotId）。
  const reviewQueuePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === '/api/v1/review-cases' &&
      new URL(response.url()).searchParams.get('status') === 'OPEN' &&
      new URL(response.url()).searchParams.get('taskId') === taskId,
  )
  await page.goto(`/reviews?status=OPEN&taskId=${taskId}`)
  expect((await reviewQueuePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /审核中心|审核队列/, level: 1 })).toBeVisible()

  // M181：QueueTable 行内 projectId 单元格深链（opt-in linkColumns）。
  const pilotProjectIdForQueueCell = '10000000-0000-4000-8000-000000000001'
  const queueCellProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/projects/${pilotProjectIdForQueueCell}`,
  )
  await page
    .locator('table')
    .locator('a.queue-cell-link', { hasText: new RegExp(`^${pilotProjectIdForQueueCell}$`) })
    .first()
    .click()
  expect((await queueCellProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  await page.goto(`/reviews?status=OPEN&taskId=${taskId}`)
  await expect(page.getByRole('heading', { name: /审核中心|审核队列/, level: 1 })).toBeVisible()

  const reviewQueueSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${reviewCase.evidenceSetSnapshotId}`,
  )
  await page
    .locator('.review-queue-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开资料快照\\s+${reviewCase.evidenceSetSnapshotId}`),
    })
    .click()
  expect((await reviewQueueSnapshotPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '资料快照详情' })).toBeVisible()

  await reviewPage.getByLabel('decision').selectOption('REJECTED')
  await reviewPage.getByLabel('reasonCodes（逗号分隔）').fill('IMAGE.BLUR')
  await reviewPage.getByLabel('note').fill('Admin pilot correction required')
  const rejectResponsePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${reviewCase.reviewCaseId}:decide`),
  )
  await reviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  const rejectResponse = await rejectResponsePromise
  expect(rejectResponse.status()).toBe(200)
  expect(await rejectResponse.json()).toMatchObject({
    reviewCaseId: reviewCase.reviewCaseId,
    status: 'REJECTED',
  })
  await expect(reviewPage.getByText(/已裁决为\s*(REJECTED|已驳回)/)).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: /REJECTED|已驳回/ })).toBeVisible()

  // M149：工作区 REVIEWS_CORRECTIONS → 审核/整改详情深链（复用已有详情页）。
  await page.getByRole('link', { name: '工单目录' }).click()
  await expect(page.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('tab', { name: '审核整改' }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)

  // M166：工作区区块 → 审核资料快照深链（Accepted 投影字段，不经详情页）。
  // 产品化审核整改侧链移除；快照详情路由直达。
  const workspaceSectionSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${reviewCase.evidenceSetSnapshotId}`,
  )
  await page.goto(`/evidence-set-snapshots/${reviewCase.evidenceSetSnapshotId}`)
  expect((await workspaceSectionSnapshotPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '资料快照详情' })).toBeVisible()
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('tab', { name: '审核整改' }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)

  const workspaceReviewDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/review-cases/${reviewCase.reviewCaseId}`,
  )
  await page.goto(`/reviews/${reviewCase.reviewCaseId}`)
  expect((await workspaceReviewDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/reviews/${reviewCase.reviewCaseId}$`))

  // M172：审核详情明文 projectId → 项目详情深链（试点项目固定 UUID）。
  const pilotProjectId = '10000000-0000-4000-8000-000000000001'
  const reviewProjectPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/projects/${pilotProjectId}`,
  )
  await page
    .locator('.review-cross-links')
    .getByRole('link', { name: new RegExp(`打开项目\\s+${pilotProjectId}`) })
    .click()
  expect((await reviewProjectPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '项目详情', level: 1 })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/projects/${pilotProjectId}$`))

  await page.goto(new URL(`/reviews/${reviewCase.reviewCaseId}`, page.url()).toString())
  await expect(page.getByRole('heading', { name: '审核案例' })).toBeVisible()

  // M164：审核详情 → 资料快照深链。
  const reviewSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${reviewCase.evidenceSetSnapshotId}`,
  )
  await page
    .locator('.review-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开资料快照\\s+${reviewCase.evidenceSetSnapshotId}`),
    })
    .click()
  expect((await reviewSnapshotPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '资料快照详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/evidence-set-snapshots/${reviewCase.evidenceSetSnapshotId}$`),
  )

  // 产品化工作区不再暴露 sections JSON 预览与 rc 旁路链；整改 Case 通过授权队列定位（下方 M148）。

  // REJECTED 会同事务创建并激活整改 Case/Task。通过真实授权队列（M148 筛选）定位来源审核，
  // 再在详情页执行 CRITICAL 豁免，证明 Case 终态与整改 Task 取消同时成立。
  const { correctionPage, correction } = await openInProgressCorrectionFromFilteredQueue(
    page,
    reviewCase.reviewCaseId,
    '整改队列未返回本轮驳回生成的 CorrectionCase',
  )
  await expect(correctionPage.getByText('处理中', { exact: true })).toBeVisible()
  await expect(correctionPage.getByText(correction.correctionTaskId!, { exact: true })).toBeVisible()

  await correctionPage.getByLabel('waive reason').fill('Pilot authorized correction waiver')
  await correctionPage.getByLabel('waive approvalRef').fill('ADMIN-PILOT-WAIVE-001')
  const waiveResponsePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/correction-cases/${correction!.correctionCaseId}:waive`,
      ),
  )
  await correctionPage.getByRole('button', { name: 'waive', exact: true }).click()
  const waiveResponse = await waiveResponsePromise
  expect(waiveResponse.status()).toBe(200)
  expect(await waiveResponse.json()).toMatchObject({
    correctionCaseId: correction!.correctionCaseId,
    status: 'WAIVED',
  })
  await expect(correctionPage.getByText(/已豁免，\s*(WAIVED|已豁免)/)).toBeVisible()
  await expect(correctionPage.getByText(/^(WAIVED|已豁免)$/)).toBeVisible()
  await expect(correctionPage.getByText(correction!.correctionTaskId!, { exact: true })).toBeVisible()

  await correctionPage.close()
  await reviewPage.close()
})

test('真实 OIDC 登录后可强制通过并导航到重开的后继审核案例', async ({ page }) => {
  test.setTimeout(90_000)
  const workOrderCode = process.env.ADMIN_PILOT_REOPEN_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_REOPEN_TASK_ID
  expect(workOrderCode, '缺少动态审核重开验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态审核重开验证 Task ID').toBeTruthy()

  const { reviewCase, reviewPage } = await prepareOpenReviewCase(
    page,
    workOrderCode!,
    taskId!,
    'admin-pilot-review-reopen-e2e',
    'admin-pilot-review-reopen.png',
  )

  // M148：审核队列按 OPEN + taskId 收窄可见本轮 OPEN Case。
  await reviewPage.goto(new URL('/reviews', page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: /审核中心|审核队列/, level: 1 })).toBeVisible()
  await reviewPage.getByLabel('review status filter').selectOption('OPEN')
  await reviewPage.getByLabel('review taskId filter').fill(taskId!)
  const reviewQueuePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/review-cases') &&
      new URL(response.url()).searchParams.get('status') === 'OPEN' &&
      new URL(response.url()).searchParams.get('taskId') === taskId,
  )
  await reviewPage.getByRole('button', { name: '查询' }).click()
  expect((await reviewQueuePromise).status()).toBe(200)
  await reviewPage
    .getByRole('link', { name: `打开审核案例 ${reviewCase.reviewCaseId}` })
    .click()
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(reviewPage).toHaveURL(new RegExp(`/reviews/${reviewCase.reviewCaseId}$`))

  // 强制通过保持独立 FORCE_APPROVED 决定，不伪装成普通 APPROVED，也不创建整改 Case。
  await reviewPage.getByLabel('reasonCodes（逗号分隔）').fill('UNMET_OCR')
  await reviewPage.getByLabel('approvalRef（强制通过）').fill('ADMIN-PILOT-FORCE-001')
  await reviewPage.getByLabel('note').fill('Pilot authorized force approval')
  const forceResponsePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${reviewCase.reviewCaseId}:force-approve`),
  )
  await reviewPage.getByRole('button', { name: 'force-approve', exact: true }).click()
  const forceResponse = await forceResponsePromise
  expect(forceResponse.status()).toBe(200)
  expect(await forceResponse.json()).toMatchObject({
    reviewCaseId: reviewCase.reviewCaseId,
    status: 'FORCE_APPROVED',
  })
  await expect(reviewPage.getByText(/已强制通过：\s*(FORCE_APPROVED|强制通过)/)).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: /FORCE_APPROVED|强制通过/ })).toBeVisible()

  await reviewPage.getByLabel('reason', { exact: true }).fill('OEM requested another review round')
  await reviewPage.getByLabel('triggerRef').fill('OEM_REJECTION:ADMIN-PILOT-001')
  const reopenResponsePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${reviewCase.reviewCaseId}:reopen`),
  )
  await reviewPage.getByRole('button', { name: 'reopen', exact: true }).click()
  const reopenResponse = await reopenResponsePromise
  expect(reopenResponse.status()).toBe(201)
  const reopened = (await reopenResponse.json()) as {
    reviewCaseId: string
    status: string
    reopenedFromReviewCaseId: string | null
    reopenTriggerRef: string | null
  }
  expect(reopened).toMatchObject({
    status: 'OPEN',
    reopenedFromReviewCaseId: reviewCase.reviewCaseId,
    reopenTriggerRef: 'OEM_REJECTION:ADMIN-PILOT-001',
  })
  expect(reopened.reviewCaseId).not.toBe(reviewCase.reviewCaseId)

  // 页面必须把路由身份切到后继 Case；否则刷新会回到已 REOPENED 的旧案例。
  await expect(reviewPage).toHaveURL(new RegExp(`/reviews/${reopened.reviewCaseId}$`))
  await expect(
    reviewPage.getByText(new RegExp(`重开结果：\\s*(OPEN|待处理)\\s*/\\s*${reopened.reviewCaseId}`)),
  ).toBeVisible()
  await expect(reviewPage.getByText(/^(OPEN|待处理)$/)).toBeVisible()
  await expect(
    reviewPage.getByText(reviewCase.reviewCaseId, { exact: true }),
  ).toBeVisible()
  await expect(
    reviewPage.getByText('OEM_REJECTION:ADMIN-PILOT-001', { exact: true }),
  ).toBeVisible()

  await reviewPage.reload()
  await expect(reviewPage.getByText(/^(OPEN|待处理)$/)).toBeVisible()
  await expect(
    reviewPage.getByText(reviewCase.reviewCaseId, { exact: true }),
  ).toBeVisible()

  // M164：后继审核案例 → 源审核案例深链。
  const sourceReviewPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/review-cases/${reviewCase.reviewCaseId}`,
  )
  await reviewPage
    .locator('.review-cross-links')
    .getByRole('link', {
      name: new RegExp(`打开源审核案例\\s+${reviewCase.reviewCaseId}`),
    })
    .click()
  expect((await sourceReviewPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()

  // M166：产品化工作区侧链移除；源审核案例路由直达即可。
  const workspaceSourceReviewPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/review-cases/${reviewCase.reviewCaseId}`,
  )
  await page.goto(`/reviews/${reviewCase.reviewCaseId}`)
  expect((await workspaceSourceReviewPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/reviews/${reviewCase.reviewCaseId}$`))

  await reviewPage.close()
})

test('真实 OIDC 登录后审核驳回可经补传关闭并复审通过后完结工单', async ({ page }) => {
  test.setTimeout(180_000)
  const workOrderCode = process.env.ADMIN_PILOT_RESUBMIT_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_RESUBMIT_TASK_ID
  expect(workOrderCode, '缺少动态补传验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态补传验证 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill('admin-pilot-resubmit-e2e')
  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  expect((await assignmentResponsePromise).status()).toBe(200)

  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await clickTaskCommandButton(page, '启动任务')
  expect((await startResponsePromise).status()).toBe(200)

  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()

  await page.getByLabel('values JSON').fill('{"completion.note":"ADMIN_PILOT_RESUBMIT_E2E"}')
  const formResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/form-submissions`),
  )
  await page.getByRole('button', { name: 'submitTaskForm' }).click()
  const formResponse = await formResponsePromise
  expect(formResponse.status()).toBe(201)
  const submission = (await formResponse.json()) as {
    submissionId: string
    contentDigest: string
    validationStatus: string
  }
  expect(submission.validationStatus).toBe('VALIDATED')

  await expect(page.getByRole('cell', { name: /completion\.photo|完工/ })).toBeVisible({
    timeout: 30_000,
  })
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  await page.getByLabel('文件').setInputFiles({
    name: 'admin-pilot-resubmit-v1.png',
    mimeType: 'image/png',
    buffer: png,
  })
  const firstFinalizePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const firstFinalize = await firstFinalizePromise
  expect(firstFinalize.status()).toBe(201)
  const firstEvidence = (await firstFinalize.json()) as {
    evidenceItemId: string
    revisions: Array<{ evidenceRevisionId: string }>
  }
  const firstRevisionId = firstEvidence.revisions.at(-1)?.evidenceRevisionId
  expect(firstRevisionId, '首轮 Finalize 未返回 EvidenceRevision').toBeTruthy()

  const orchestrationHeader = page
    .getByRole('heading', { name: '表单 / 资料编排' })
    .locator('..')
  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: firstRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  const firstSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  expect((await firstSnapshotPromise).status()).toBe(201)

  const firstReviewCreatePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const firstReviewCreate = await firstReviewCreatePromise
  expect(firstReviewCreate.status()).toBe(201)
  const rejectedReview = (await firstReviewCreate.json()) as {
    reviewCaseId: string
    status: string
  }
  expect(rejectedReview.status).toBe('OPEN')

  const reviewHref = await page
    .getByRole('link', { name: new RegExp(`打开审核案例 ${rejectedReview.reviewCaseId}`) })
    .getAttribute('href')
  expect(reviewHref, '首轮审核案例深链缺失').toBeTruthy()
  const reviewPage = await page.context().newPage()
  await reviewPage.goto(new URL(reviewHref!, page.url()).toString())
  await reviewPage.getByLabel('decision').selectOption('REJECTED')
  await reviewPage.getByLabel('reasonCodes（逗号分隔）').fill('IMAGE.BLUR')
  await reviewPage.getByLabel('note').fill('Admin pilot resubmit required')
  const rejectPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${rejectedReview.reviewCaseId}:decide`),
  )
  await reviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  expect((await rejectPromise).status()).toBe(200)
  await expect(reviewPage.getByText(/已裁决为\s*(REJECTED|已驳回)/)).toBeVisible()

  const { correctionPage, correction } = await openInProgressCorrectionFromFilteredQueue(
    page,
    rejectedReview.reviewCaseId,
    '整改队列未返回本轮驳回生成的 CorrectionCase',
  )

  // 保持源 Task 页签：在同一 Item 上追加补传 Revision，并冻结新的 Snapshot。
  await page.getByLabel('文件').setInputFiles({
    name: 'admin-pilot-resubmit-v2.png',
    mimeType: 'image/png',
    buffer: png,
  })
  const secondFinalizePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const secondFinalize = await secondFinalizePromise
  expect(secondFinalize.status()).toBe(201)
  const secondEvidence = (await secondFinalize.json()) as {
    evidenceItemId: string
    revisions: Array<{ evidenceRevisionId: string }>
  }
  expect(secondEvidence.evidenceItemId).toBe(firstEvidence.evidenceItemId)
  const secondRevisionId = secondEvidence.revisions.at(-1)?.evidenceRevisionId
  expect(secondRevisionId, '补传 Finalize 未返回新 EvidenceRevision').toBeTruthy()
  expect(secondRevisionId).not.toBe(firstRevisionId)

  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: secondRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  const secondSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  const secondSnapshotResponse = await secondSnapshotPromise
  expect(secondSnapshotResponse.status()).toBe(201)
  const secondSnapshot = (await secondSnapshotResponse.json()) as {
    evidenceSetSnapshotId: string
    contentDigest: string
  }

  await correctionPage
    .getByLabel('resubmit snapshotId')
    .fill(secondSnapshot.evidenceSetSnapshotId)
  const resubmitPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/correction-cases/${correction!.correctionCaseId}:resubmit`,
      ),
  )
  await correctionPage.getByRole('button', { name: 'resubmit', exact: true }).click()
  const resubmitResponse = await resubmitPromise
  expect(resubmitResponse.status()).toBe(200)
  expect(await resubmitResponse.json()).toMatchObject({
    correctionCaseId: correction!.correctionCaseId,
    status: 'RESUBMITTED',
    latestResubmissionSnapshotId: secondSnapshot.evidenceSetSnapshotId,
  })
  await expect(correctionPage.getByText(/已补传，\s*(RESUBMITTED|已重新提交)/)).toBeVisible()
  await expect(correctionPage.getByText(/^(RESUBMITTED|已重新提交)$/)).toBeVisible()
  await expect(
    correctionPage.getByRole('cell', { name: secondSnapshot.evidenceSetSnapshotId, exact: true }),
  ).toBeVisible()

  await correctionPage.getByLabel('close note').fill('verified resubmission close')
  const closePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/correction-cases/${correction!.correctionCaseId}:close`,
      ),
  )
  await correctionPage.getByRole('button', { name: 'close', exact: true }).click()
  const closeResponse = await closePromise
  expect(closeResponse.status()).toBe(200)
  expect(await closeResponse.json()).toMatchObject({
    correctionCaseId: correction!.correctionCaseId,
    status: 'CLOSED',
  })
  await expect(correctionPage.getByText(/已关闭，\s*(CLOSED|已关闭)/)).toBeVisible()
  await expect(correctionPage.getByText(/^(CLOSED|已关闭)$/)).toBeVisible()

  // close 不等于审核通过：对补传 Snapshot 新建 INTERNAL ReviewCase 并普通 APPROVED。
  // 若服务端因快照已有 OPEN Case 返回 409，则从授权审核队列解析补传快照对应案例。
  const secondReviewCreatePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const secondReviewCreate = await secondReviewCreatePromise
  let approvedReview: {
    reviewCaseId: string
    status: string
    evidenceSetSnapshotId: string
  }
  if (secondReviewCreate.status() === 201) {
    approvedReview = (await secondReviewCreate.json()) as typeof approvedReview
  } else {
    expect(
      secondReviewCreate.status(),
      `复审创建期望 201 或可恢复的 409，实际 ${secondReviewCreate.status()}`,
    ).toBe(409)
    const reopenQueuePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/v1/review-cases' &&
        new URL(response.url()).searchParams.get('status') === 'OPEN' &&
        new URL(response.url()).searchParams.get('taskId') === taskId,
    )
    await page.goto(`/reviews?status=OPEN&taskId=${taskId}`)
    const reopenQueue = await reopenQueuePromise
    expect(reopenQueue.status()).toBe(200)
    const queue = (await reopenQueue.json()) as {
      items: Array<{
        reviewCaseId: string
        status: string
        evidenceSetSnapshotId: string
      }>
    }
    const found = queue.items.find(
      (item) => item.evidenceSetSnapshotId === secondSnapshot.evidenceSetSnapshotId,
    )
    expect(found, '409 后未在 OPEN 队列找到补传快照对应审核案例').toBeTruthy()
    approvedReview = found!
  }
  expect(approvedReview).toMatchObject({
    status: 'OPEN',
    evidenceSetSnapshotId: secondSnapshot.evidenceSetSnapshotId,
  })
  expect(approvedReview.reviewCaseId).not.toBe(rejectedReview.reviewCaseId)

  // 409 恢复路径可能已离开 Task 页；完成前回到任务详情并等待双引用回填。
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByLabel('resultRef')).toBeVisible({ timeout: 30_000 })

  const reReviewPage = await page.context().newPage()
  await reReviewPage.goto(new URL(`/reviews/${approvedReview.reviewCaseId}`, page.url()).toString())
  await reReviewPage.getByLabel('note').fill('Admin pilot resubmission approved')
  const approvePromise = reReviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${approvedReview.reviewCaseId}:decide`),
  )
  await reReviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  const approveResponse = await approvePromise
  expect(approveResponse.status()).toBe(200)
  expect(await approveResponse.json()).toMatchObject({
    reviewCaseId: approvedReview.reviewCaseId,
    status: 'APPROVED',
  })
  await expect(reReviewPage.getByText(/已裁决为\s*(APPROVED|已通过)/)).toBeVisible()

  await expect(page.getByLabel('resultRef')).toHaveValue(
    `form-submission://${submission.submissionId}`,
  )
  await expect(page.getByLabel('resultDigest')).toHaveValue(submission.contentDigest)
  const inputVersionRefs = JSON.parse(
    await page.getByLabel('inputVersionRefs JSON（双引用可选）').inputValue(),
  )
  expect(inputVersionRefs).toEqual([
    {
      kind: 'FORM_SUBMISSION',
      ref: `form-submission://${submission.submissionId}`,
      digest: submission.contentDigest,
    },
    {
      kind: 'EVIDENCE_SET_SNAPSHOT',
      ref: `evidence-set-snapshot://${secondSnapshot.evidenceSetSnapshotId}`,
      digest: secondSnapshot.contentDigest,
    },
  ])

  const completePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:complete`),
  )
  await clickTaskCommandButton(page, '完成任务')
  const completeResponse = await completePromise
  const completeBody = await completeResponse.json()
  expect(completeResponse.status(), JSON.stringify(completeBody)).toBe(200)
  expect(completeBody).toMatchObject({
    taskId,
    status: 'COMPLETED',
  })

  await reReviewPage.close()
  await correctionPage.close()
  await reviewPage.close()
})

test('真实 OIDC 登录后可完成预约提议确认与上门签到签退', async ({ page }) => {
  test.setTimeout(120_000)
  const workOrderCode = process.env.ADMIN_PILOT_FIELD_OPS_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_FIELD_OPS_TASK_ID
  expect(workOrderCode, '缺少动态现场履约验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态现场履约验证 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // M144：Admin HTTP 人工初派 Visit 所需 NETWORK/TECHNICIAN（高级双责任初派）。
  await page.locator('summary', { hasText: '高级：一次初派网点+师傅' }).click()
  await page.getByLabel('assign-network networkAssigneeId').fill('admin-pilot-network-1')
  await page.getByLabel('manual-assign technicianAssigneeId').fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await expect(page.getByRole('button', { name: '确认双责任初派' })).toBeVisible()
  const manualAssignPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/service-assignments:manual-assign`),
  )
  await page.getByRole('button', { name: '确认双责任初派' }).click()
  const manualAssignResponse = await manualAssignPromise
  expect(manualAssignResponse.status(), await manualAssignResponse.text()).toBe(200)
  // 成功文案落在 <details> 内；折叠时 Playwright 视为 hidden。
  await page.locator('details.assign-advanced').evaluate((el) => {
    ;(el as HTMLDetailsElement).open = true
  })
  await expect(page.locator('details.assign-advanced').getByText(/已初派/)).toBeVisible({
    timeout: 15_000,
  })
  await expect(
    page.locator('dt', { hasText: /^服务责任$/ }).locator('xpath=../dd'),
  ).toContainText('admin-pilot-network-1')

  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill('admin-pilot-field-ops-e2e')
  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  expect((await assignmentResponsePromise).status()).toBe(200)

  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await clickTaskCommandButton(page, '启动任务')
  expect((await startResponsePromise).status()).toBe(200)

  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  // M160：先记录联系，供工作区 AV → 联系详情深链证明。
  const recordContactPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/contact-attempts`),
  )
  await page.getByRole('button', { name: 'recordContactAttempt', exact: true }).click()
  const recordContactResponse = await recordContactPromise
  expect(recordContactResponse.status()).toBe(201)
  const recordedContact = (await recordContactResponse.json()) as {
    contactAttemptId: string
    channel: string
    resultCode: string
  }
  expect(recordedContact.contactAttemptId).toBeTruthy()
  await expect(
    page.getByText(`已记录联系 ${recordedContact.contactAttemptId}`),
  ).toBeVisible()

  // UI 在加载时预填未来窗口；此处显式覆盖，避免时钟漂移导致窗口非法。
  const windowStart = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()
  const windowEnd = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString()
  await page.getByLabel('window.start').fill(windowStart)
  await page.getByLabel('window.end').fill(windowEnd)
  await page.getByLabel('type').selectOption('INSTALLATION')

  const proposePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/appointments`),
  )
  await page.getByRole('button', { name: 'proposeAppointment', exact: true }).click()
  const proposeResponse = await proposePromise
  expect(proposeResponse.status()).toBe(201)
  const proposed = (await proposeResponse.json()) as {
    appointmentId: string
    status: string
    aggregateVersion: number
  }
  expect(proposed.status).toBe('PROPOSED')
  await expect(page.getByText(`已提议预约 ${proposed.appointmentId}`)).toBeVisible()

  const confirmPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/appointments/${proposed.appointmentId}:confirm`),
  )
  await page.getByRole('button', { name: 'confirm', exact: true }).click()
  const confirmResponse = await confirmPromise
  expect(confirmResponse.status()).toBe(200)
  expect(await confirmResponse.json()).toMatchObject({
    appointmentId: proposed.appointmentId,
    status: 'CONFIRMED',
  })
  await expect(page.getByText(`已确认预约 ${proposed.appointmentId}`)).toBeVisible()

  // M167：Task 面板 → 联系 / 预约详情（复用已有详情页）。
  const taskContactDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/contact-attempts/${recordedContact.contactAttemptId}`,
  )
  await page
    .locator('.task-fieldops-contact-links')
    .getByRole('link', {
      name: new RegExp(
        `task\\s*/\\s*ContactAttempt\\s*/\\s*${recordedContact.channel}\\s*/\\s*${recordedContact.resultCode}\\s*/\\s*${recordedContact.contactAttemptId}`,
      ),
    })
    .click()
  expect((await taskContactDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '联系详情' })).toBeVisible()
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  const taskAppointmentDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/appointments/${proposed.appointmentId}`,
  )
  await page
    .locator('.task-fieldops-appointment-links')
    .getByRole('link', {
      name: new RegExp(
        `task\\s*/\\s*Appointment\\s*/\\s*(INSTALLATION|安装)\\s*/\\s*(CONFIRMED|已确认)\\s*/\\s*${proposed.appointmentId}`,
      ),
    })
    .click()
  expect((await taskAppointmentDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '预约详情' })).toBeVisible()
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  // M155：工作区 APPOINTMENTS_VISITS → 预约详情（包装 GET /appointments/{id}）。
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('button', { name: /APPOINTMENTS_VISITS|预约到场/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  await expect(page.getByText('打开预约详情：')).toBeVisible()
  const workspaceAppointmentDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/appointments/${proposed.appointmentId}`,
  )
  await page
    .getByRole('link', {
      name: new RegExp(
        `(INSTALLATION|安装)\\s*/\\s*(CONFIRMED|已确认)\\s*/\\s*${proposed.appointmentId}`,
      ),
    })
    .click()
  expect((await workspaceAppointmentDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '预约详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/appointments/${proposed.appointmentId}$`))

  // M174：预约事实格明文 taskId → 任务详情。
  const appointmentInlineTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${taskId}`,
  )
  await page
    .locator('dt', { hasText: /^taskId$/ })
    .locator('xpath=../dd')
    .getByRole('link', { name: taskId!, exact: true })
    .click()
  expect((await appointmentInlineTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await page.goto(new URL(`/appointments/${proposed.appointmentId}`, page.url()).toString())
  await expect(page.getByRole('heading', { name: '预约详情' })).toBeVisible()

  // M154：同区块 Task 旁路仍可用（现场操作入口）。
  await page.getByRole('link', { name: '工单工作区' }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('button', { name: /APPOINTMENTS_VISITS|预约到场/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  await expect(page.getByText('打开预约上门关联任务：')).toBeVisible()
  const workspaceAvTaskPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${taskId}`,
  )
  await page
    .getByRole('link', {
      name: new RegExp(
        `appointment\\s*/\\s*(INSTALLATION|安装)\\s*/\\s*(CONFIRMED|已确认)\\s*/\\s*${taskId}`,
      ),
    })
    .click()
  expect((await workspaceAvTaskPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}$`))
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  const checkInPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/appointments/${proposed.appointmentId}/visits:check-in`,
      ),
  )
  await page.getByRole('button', { name: 'check-in', exact: true }).click()
  const checkInResponse = await checkInPromise
  expect(checkInResponse.status()).toBe(201)
  const checkedIn = (await checkInResponse.json()) as {
    visitId: string
    status: string
  }
  expect(checkedIn.status).toBe('IN_PROGRESS')
  await expect(page.getByText(`签到 Visit ${checkedIn.visitId}`)).toBeVisible()

  const checkOutPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/visits/${checkedIn.visitId}:check-out`),
  )
  await page.getByRole('button', { name: 'check-out', exact: true }).click()
  const checkOutResponse = await checkOutPromise
  expect(checkOutResponse.status()).toBe(200)
  expect(await checkOutResponse.json()).toMatchObject({
    visitId: checkedIn.visitId,
    status: 'COMPLETED',
  })
  await expect(page.getByText(`签退 Visit ${checkedIn.visitId}`)).toBeVisible()

  // M167：Task 面板 → 上门详情。
  const taskVisitDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/visits/${checkedIn.visitId}`,
  )
  await page
    .locator('.task-fieldops-visit-links')
    .getByRole('link', {
      name: new RegExp(
        `task\\s*/\\s*Visit\\s*/\\s*(COMPLETED|已完成)\\s*/\\s*seq=\\d+\\s*/\\s*${checkedIn.visitId}`,
      ),
    })
    .click()
  expect((await taskVisitDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '上门详情' })).toBeVisible()
  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  // M159：工作区 APPOINTMENTS_VISITS → 上门详情（GET /visits/{id}）。
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('button', { name: /APPOINTMENTS_VISITS|预约到场/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  await expect(page.getByText('打开上门详情：')).toBeVisible()
  const workspaceVisitDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/visits/${checkedIn.visitId}`,
  )
  await page
    .getByRole('link', {
      name: new RegExp(`(COMPLETED|已完成)\\s*/\\s*seq=\\d+\\s*/\\s*${checkedIn.visitId}`),
    })
    .click()
  expect((await workspaceVisitDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '上门详情' })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/visits/${checkedIn.visitId}$`))

  // M160：工作区 APPOINTMENTS_VISITS → 联系详情（GET /contact-attempts/{id}）。
  await page.getByRole('link', { name: '工单目录' }).click()
  await page.getByRole('main').getByRole('table').getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await page.getByRole('button', { name: /APPOINTMENTS_VISITS|预约到场/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  await expect(page.getByText('打开联系详情：')).toBeVisible()
  const workspaceContactDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/contact-attempts/${recordedContact.contactAttemptId}`,
  )
  await page
    .getByRole('link', {
      name: new RegExp(
        `${recordedContact.channel}\\s*/\\s*${recordedContact.resultCode}\\s*/\\s*${recordedContact.contactAttemptId}`,
      ),
    })
    .click()
  expect((await workspaceContactDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '联系详情' })).toBeVisible()
  await expect(page).toHaveURL(
    new RegExp(`/contact-attempts/${recordedContact.contactAttemptId}$`),
  )
})

test('真实 OIDC 登录后可通过审核外发并经厂端回调关闭 CLIENT Case', async ({ page, request }) => {
  test.setTimeout(240_000)
  const workOrderCode = process.env.ADMIN_PILOT_OUTBOUND_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_OUTBOUND_TASK_ID
  expect(workOrderCode, '缺少动态外发验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态外发验证 Task ID').toBeTruthy()

  const { reviewCase, reviewPage } = await prepareOpenReviewCase(
    page,
    workOrderCode!,
    taskId!,
    'admin-pilot-outbound-e2e',
    'admin-pilot-outbound.png',
  )

  const approvePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${reviewCase.reviewCaseId}:decide`),
  )
  await reviewPage.getByLabel('note').fill('Admin pilot outbound approved')
  await reviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  expect((await approvePromise).status()).toBe(200)
  await expect(reviewPage.getByText(/已裁决为\s*(APPROVED|已通过)/)).toBeVisible()

  const submitPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/internal/integration/byd/review-submissions'),
  )
  await reviewPage.getByRole('button', { name: 'create BYD review submission', exact: true }).click()
  const submitResponse = await submitPromise
  expect(submitResponse.status()).toBe(201)
  const delivery = (await submitResponse.json()) as {
    deliveryId: string
    status: string
    externalOrderCode?: string
  }
  expect(delivery.deliveryId).toBeTruthy()
  await expect(
    reviewPage.getByText(new RegExp(`已创建外发交付 ${delivery.deliveryId}`)),
  ).toBeVisible()

  // Task worker 在事务外发送 stub HTTP，再落 ACK/CLIENT Case；轮询权威详情直至 ACKNOWLEDGED。
  await reviewPage.goto(
    new URL(`/integration/outbound/${delivery.deliveryId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()
  const outboundRefresh = reviewPage
    .locator('header')
    .filter({ hasText: '外发交付' })
    .getByRole('button', { name: '刷新' })
  await expect
    .poll(
      async () => {
        await outboundRefresh.click()
        // 刷新期间模板切到“加载中…”，必须等详情卡恢复后再读取 status。
        await expect(reviewPage.getByText('加载中…')).toHaveCount(0)
        return reviewPage.locator('dd', { hasText: /^(ACKNOWLEDGED|已确认)$/ }).count()
      },
      { timeout: 60_000 },
    )
    .toBeGreaterThan(0)

  const clientReviewHref = await reviewPage
    .getByRole('link', { name: /CLIENT\s*审核案例/ })
    .getAttribute('href')
  expect(clientReviewHref, 'CLIENT 审核案例深链缺失').toBeTruthy()
  const clientReviewCaseId = clientReviewHref!.split('/').filter(Boolean).at(-1)!
  const externalOrderCode = (
    await reviewPage
      .locator('dt', { hasText: /^externalOrderCode$/ })
      .locator('xpath=../dd')
      .innerText()
  ).trim()
  const sourceWorkOrderId = (
    await reviewPage
      .locator('dt', { hasText: /^sourceWorkOrderId$/ })
      .locator('xpath=../dd')
      .innerText()
  ).trim()
  const sourceTaskHref = await reviewPage
    .getByRole('link', { name: /打开源任务/ })
    .getAttribute('href')
  expect(sourceTaskHref, '源任务深链缺失').toBeTruthy()
  const sourceTaskId = sourceTaskHref!.split('/').filter(Boolean).at(-1)!
  const sourceSnapshotHref = await reviewPage
    .getByRole('link', { name: /打开源资料快照/ })
    .getAttribute('href')
  expect(sourceSnapshotHref, '源资料快照深链缺失').toBeTruthy()
  const sourceSnapshotId = sourceSnapshotHref!.split('/').filter(Boolean).at(-1)!
  expect(clientReviewCaseId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )
  expect(externalOrderCode).toBeTruthy()
  expect(sourceWorkOrderId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )
  expect(sourceTaskId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )
  expect(sourceSnapshotId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )

  // M173：外发详情「打开源任务」深链 → 任务详情。
  const outboundInlineTaskPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${sourceTaskId}`,
  )
  await reviewPage.getByRole('link', { name: /打开源任务/ }).click()
  expect((await outboundInlineTaskPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await reviewPage.goto(
    new URL(`/integration/outbound/${delivery.deliveryId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()

  // M171：外发详情 → 源任务 / 源资料快照交叉深链。
  const outboundSourceTaskPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${sourceTaskId}`,
  )
  await reviewPage
    .getByRole('link', { name: new RegExp(`打开源任务\\s+${sourceTaskId}`) })
    .click()
  expect((await outboundSourceTaskPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await reviewPage.goto(
    new URL(`/integration/outbound/${delivery.deliveryId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()
  // M182：外发详情 → 执行任务（OpenAPI executionTaskId）。
  const executionTaskHref = await reviewPage
    .getByRole('link', { name: /打开执行任务/ })
    .getAttribute('href')
  expect(executionTaskHref, '执行任务深链缺失').toBeTruthy()
  const executionTaskId = executionTaskHref!.split('/').filter(Boolean).at(-1)!
  expect(executionTaskId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )
  const outboundExecutionTaskPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${executionTaskId}`,
  )
  await reviewPage.getByRole('link', { name: /打开执行任务/ }).click()
  expect((await outboundExecutionTaskPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await reviewPage.goto(
    new URL(`/integration/outbound/${delivery.deliveryId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()

  const outboundSourceSnapshotPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-set-snapshots/${sourceSnapshotId}`,
  )
  await reviewPage
    .getByRole('link', { name: new RegExp(`打开源资料快照\\s+${sourceSnapshotId}`) })
    .click()
  const outboundSourceSnapshotResponse = await outboundSourceSnapshotPromise
  expect(outboundSourceSnapshotResponse.status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '资料快照详情' })).toBeVisible()
  const snapshotDetail = (await outboundSourceSnapshotResponse.json()) as {
    members?: Array<{ evidenceItemId: string }>
  }
  const memberEvidenceItemId = snapshotDetail.members?.[0]?.evidenceItemId
  expect(memberEvidenceItemId, '源资料快照缺少成员 evidenceItemId').toBeTruthy()

  // M182：资料快照成员 → 资料项详情。
  const snapshotMemberItemPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/evidence-items/${memberEvidenceItemId}`,
  )
  await reviewPage
    .locator('.evidence-snapshot-member-links')
    .getByRole('link', {
      name: new RegExp(`打开资料项\\s+${memberEvidenceItemId}`),
    })
    .click()
  expect((await snapshotMemberItemPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '资料项详情' })).toBeVisible()

  // M147：工作区 INTEGRATION → 外发交付详情深链（复用已有 OutboundDeliveryDetailPage）。
  await reviewPage.goto(
    new URL(`/work-orders/${sourceWorkOrderId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await reviewPage.getByRole('button', { name: /INTEGRATION|集成/ }).click()
  await expect(reviewPage.getByText('区块加载中…')).toHaveCount(0)

  // M171：工作区外发关联资源 → 源任务。
  await expect(reviewPage.getByText('打开外发关联资源：')).toBeVisible()
  const workspaceOutboundTaskPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${sourceTaskId}`,
  )
  await reviewPage
    .locator('.outbound-cross-links')
    .getByRole('link', { name: new RegExp(`ob\\s*/\\s*源任务\\s*/\\s*${sourceTaskId}`) })
    .click()
  expect((await workspaceOutboundTaskPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await reviewPage.goto(
    new URL(`/work-orders/${sourceWorkOrderId}`, page.url()).toString(),
  )
  await expect(reviewPage.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await reviewPage.getByRole('button', { name: /INTEGRATION|集成/ }).click()
  await expect(reviewPage.getByText('区块加载中…')).toHaveCount(0)

  const workspaceOutboundDetailPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/outbound-deliveries/${delivery.deliveryId}`,
  )
  await reviewPage
    .locator('.outbound-links')
    .getByRole('link', {
      name: new RegExp(
        `SUBMIT_CLIENT_REVIEW\\s*/\\s*(ACKNOWLEDGED|已确认)\\s*/\\s*${externalOrderCode}`,
      ),
    })
    .click()
  expect((await workspaceOutboundDetailPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()
  await expect(reviewPage).toHaveURL(
    new RegExp(`/integration/outbound/${delivery.deliveryId}$`),
  )
  await expect(reviewPage.getByText(externalOrderCode, { exact: true }).first()).toBeVisible()

  // M146：外发队列按 ACKNOWLEDGED + sourceWorkOrderId 收窄，避免历史 ACK 页把本交付挤出首页。
  await reviewPage.goto(new URL('/integration/outbound', page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: '外发交付队列' })).toBeVisible()
  await reviewPage.getByLabel('outbound status filter').selectOption('ACKNOWLEDGED')
  await reviewPage.getByLabel('outbound sourceWorkOrderId filter').fill(sourceWorkOrderId)
  const queueFilterPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/outbound-deliveries') &&
      new URL(response.url()).searchParams.get('status') === 'ACKNOWLEDGED' &&
      new URL(response.url()).searchParams.get('sourceWorkOrderId') === sourceWorkOrderId,
  )
  await reviewPage.getByRole('button', { name: '查询' }).click()
  expect((await queueFilterPromise).status()).toBe(200)
  const deliveryQueueLink = reviewPage
    .getByRole('link', { name: externalOrderCode, exact: true })
    .first()
  await expect(deliveryQueueLink).toBeVisible()

  // M177：外发队列关联资源 → 源任务。
  const queueOutboundSourceTaskPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/v1/tasks/${sourceTaskId}`,
  )
  await reviewPage
    .locator('.outbound-queue-cross-links')
    .getByRole('link', { name: new RegExp(`打开源任务\\s+${sourceTaskId}`) })
    .click()
  expect((await queueOutboundSourceTaskPromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await reviewPage.goto(new URL('/integration/outbound', page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: '外发交付队列' })).toBeVisible()
  await reviewPage.getByLabel('outbound status filter').selectOption('ACKNOWLEDGED')
  await reviewPage.getByLabel('outbound sourceWorkOrderId filter').fill(sourceWorkOrderId)
  const queueRefilterPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/outbound-deliveries') &&
      new URL(response.url()).searchParams.get('status') === 'ACKNOWLEDGED' &&
      new URL(response.url()).searchParams.get('sourceWorkOrderId') === sourceWorkOrderId,
  )
  await reviewPage.getByRole('button', { name: '查询' }).click()
  expect((await queueRefilterPromise).status()).toBe(200)

  // 从筛选结果深链回交付详情，再继续厂端回调与 CLIENT Case 断言。
  await reviewPage
    .getByRole('link', { name: externalOrderCode, exact: true })
    .first()
    .click()
  await expect(reviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()
  await expect(reviewPage).toHaveURL(
    new RegExp(`/integration/outbound/${delivery.deliveryId}$`),
  )

  // 厂端回调是 CPIM 签名入站，不走 Admin JWT；在同一浏览器链路后以协议方身份联调。
  const appKey = process.env.SERVICEOS_BYD_CPIM_APP_KEY ?? 'local-byd-app-key'
  const appSecret =
    process.env.SERVICEOS_BYD_CPIM_APP_SECRET ?? 'local-byd-app-secret-change-me'
  const payload = {
    orderCode: externalOrderCode,
    result: '1',
    remark: 'Admin pilot OEM approved',
    examinePerson: 'BYD-PILOT-REVIEWER',
    examineDate: asiaShanghaiDateTimeNow(),
  }
  const nonce = randomUUID()
  const currentDate = asiaShanghaiDateToday()
  const signature = signBydCpimPayload(appSecret, nonce, currentDate, payload)
  const callbackResponse = await request.post(
    'http://127.0.0.1:8080/api/v1/integrations/byd/cpim/v7.3.1/review-results',
    {
      headers: {
        'Content-Type': 'application/json',
        APP_KEY: appKey,
        Nonce: nonce,
        Cur_Time: currentDate,
        Sign: signature,
        'X-Correlation-Id': `admin-pilot-callback-${delivery.deliveryId}`,
      },
      data: payload,
    },
  )
  expect(callbackResponse.status(), await callbackResponse.text()).toBe(200)
  expect(await callbackResponse.json()).toMatchObject({ message: 'success', data: [] })

  await reviewPage.getByRole('link', { name: 'CLIENT 审核案例' }).click()
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(reviewPage).toHaveURL(new RegExp(`/reviews/${clientReviewCaseId}$`))
  const clientRefresh = reviewPage
    .locator('header')
    .filter({ hasText: '审核案例' })
    .getByRole('button', { name: '刷新' })
  await expect
    .poll(
      async () => {
        await clientRefresh.click()
        await expect(reviewPage.getByText('加载中…')).toHaveCount(0)
        const status = await reviewPage
          .locator('dt', { hasText: /^状态$/ })
          .locator('xpath=../dd')
          .innerText()
        const origin = await reviewPage
          .locator('dt', { hasText: /^来源$/ })
          .locator('xpath=../dd')
          .innerText()
        return `${origin.trim()}:${status.trim()}`
      },
      { timeout: 30_000 },
    )
    .toMatch(/^(CLIENT|客户):(APPROVED|已通过)$/)

  // M163：厂端回调后核心时间线 → ExternalReviewReceipt 详情（GET /internal/...）。
  // Inbox 投影可能晚于 CLIENT:APPROVED；轮询刷新工作区直至回执链接出现。
  await reviewPage.getByRole('link', { name: '工单目录' }).click()
  await expect(reviewPage.getByRole('heading', { name: '工单中心', level: 1 })).toBeVisible()
  await reviewPage.getByRole('link', { name: workOrderCode!, exact: true }).click()
  await expect(reviewPage.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  const workspaceRefresh = reviewPage
    .locator('header')
    .filter({ hasText: '工单工作区' })
    .getByRole('button', { name: '刷新' })
  const receiptLink = reviewPage.locator('.core-timeline-resource-links').getByRole('link', {
    name: /core\s*\/\s*evidence\.external-review-receipt-recorded\s*\/\s*ExternalReviewReceipt/,
  })
  await expect
    .poll(
      async () => {
        await workspaceRefresh.click()
        await expect(reviewPage.getByText('加载中…')).toHaveCount(0)
        return receiptLink.count()
      },
      { timeout: 60_000 },
    )
    .toBeGreaterThan(0)
  const receiptDetailPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname.startsWith(
        '/api/v1/internal/external-review-receipts/',
      ),
  )
  await receiptLink.click()
  const receiptDetailResponse = await receiptDetailPromise
  expect(receiptDetailResponse.status()).toBe(200)
  const receiptBody = (await receiptDetailResponse.json()) as {
    receiptId: string
    result: string
    inboundEnvelopeId: string
  }
  expect(receiptBody.result).toBe('APPROVED')
  expect(receiptBody.inboundEnvelopeId).toBeTruthy()
  await expect(reviewPage.getByRole('heading', { name: '外部审核回执' })).toBeVisible()
  await expect(reviewPage).toHaveURL(
    new RegExp(`/external-review-receipts/${receiptBody.receiptId}$`),
  )

  // M171：外部审核回执 → 入站 Envelope 交叉深链。
  const receiptEnvelopePromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname ===
        `/api/v1/inbound-envelopes/${receiptBody.inboundEnvelopeId}`,
  )
  await reviewPage
    .getByRole('link', {
      name: new RegExp(`打开入站 Envelope\\s+${receiptBody.inboundEnvelopeId}`),
    })
    .click()
  expect((await receiptEnvelopePromise).status()).toBe(200)
  await expect(reviewPage.getByRole('heading', { name: '入站 Envelope' })).toBeVisible()
  await expect(reviewPage).toHaveURL(
    new RegExp(`/integration/inbound/${receiptBody.inboundEnvelopeId}$`),
  )

  await reviewPage.close()
})

test('真实 OIDC 登录后可在入站工单上完成领取、预约上门、整改补传复审与外发', async ({
  page,
  request,
}) => {
  test.setTimeout(300_000)
  // 冒烟脚本已完成 CPIM 入站与 Outbox 激活；此处经 Admin HTTP 人工初派后证明同单写路径
  // 直至驳回整改/补传复审/BYD 外发 ACK/厂端回调与双输入完结（接单→派单→…→完结 = ADMIN-PILOT-09）。
  const orderCode = process.env.ADMIN_PILOT_INBOUND_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_INBOUND_TASK_ID
  expect(orderCode, '缺少动态入站接单 orderCode').toBeTruthy()
  expect(taskId, '缺少入站激活后的 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.goto('/work-orders')
  await selectWorkOrderStatus(page, '处理中')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByRole('link', { name: orderCode! })).toBeVisible({
    timeout: 30_000,
  })
  await page.getByRole('main').getByRole('table').getByRole('link', { name: orderCode!, exact: true }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()
  await expect(page.locator('dt', { hasText: /^状态$/ }).first().locator('xpath=../dd')).toHaveText(/ACTIVE|处理中/)
  await expect(
    page.locator('dt', { hasText: /^外部单号$/ }).locator('xpath=../dd'),
  ).toHaveText(orderCode!)

  await page.getByRole('button', { name: /INTEGRATION|集成/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  const integrationPreview = page.locator('article.sections pre')
  await expect(integrationPreview).toContainText('CREATE_WORK_ORDER')
  await expect(integrationPreview).toContainText('COMPLETED')
  await expect(integrationPreview).toContainText('WORK_ORDER')
  await expect(integrationPreview).toContainText('ACCEPTED')

  // M145：从工作区 INTEGRATION 深链打开入站 Envelope + Canonical 详情（非专用队列页）。
  // 限定 .inbound-links，避免与 M168 Canonical 链接（同前缀 CREATE_WORK_ORDER / COMPLETED）冲突。
  const envelopeDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/v1\/inbound-envelopes\/[0-9a-f-]+$/.test(new URL(response.url()).pathname),
  )
  const canonicalDetailPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/v1\/canonical-messages\/[0-9a-f-]+$/.test(new URL(response.url()).pathname),
  )
  await page
    .locator('.inbound-links')
    .getByRole('link', { name: /CREATE_WORK_ORDER\s*\/\s*(COMPLETED|已完成)/ })
    .click()
  expect((await envelopeDetailPromise).status()).toBe(200)
  expect((await canonicalDetailPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: '入站 Envelope' })).toBeVisible()
  await expect(page.getByText('CREATE_WORK_ORDER').first()).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Canonical Message' })).toBeVisible()
  await expect(page.getByText(/BYD:INSTALL:/)).toBeVisible()

  // M168：入站 Envelope → Canonical 独立详情页（复用已 Implemented GET）。
  const standaloneCanonicalPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/v1\/canonical-messages\/[0-9a-f-]+$/.test(new URL(response.url()).pathname),
  )
  await page.getByRole('link', { name: /^打开 Canonical\s+/ }).click()
  expect((await standaloneCanonicalPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Canonical Message' })).toBeVisible()
  await expect(page).toHaveURL(/\/integration\/canonical\/[0-9a-f-]+$/)
  await expect(page.getByText(/BYD:INSTALL:/)).toBeVisible()
  await page.getByRole('link', { name: '工单工作区' }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  // M168：工作区 INTEGRATION → Canonical 独立详情深链。
  await page.getByRole('button', { name: /INTEGRATION|集成/ }).click()
  await expect(page.getByText('区块加载中…')).toHaveCount(0)
  await expect(page.getByText('打开 Canonical Message：')).toBeVisible()
  const workspaceCanonicalPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      /\/api\/v1\/canonical-messages\/[0-9a-f-]+$/.test(new URL(response.url()).pathname),
  )
  await page
    .locator('.canonical-links')
    .getByRole('link', { name: /CREATE_WORK_ORDER\s*\/\s*(COMPLETED|已完成)\s*\// })
    .click()
  expect((await workspaceCanonicalPromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: 'Canonical Message' })).toBeVisible()
  await expect(page).toHaveURL(/\/integration\/canonical\/[0-9a-f-]+$/)
  await page.getByRole('link', { name: '工单工作区' }).click()
  await expect(page.getByRole('heading', { name: /工单工作区|工单详情/, level: 1 })).toBeVisible()

  const inboundManualAssignPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/service-assignments:manual-assign`),
  )
  await page.locator('summary', { hasText: '高级：一次初派网点+师傅' }).click()
  await page.getByLabel('assign-network networkAssigneeId').fill('admin-pilot-network-1')
  await page.getByLabel('manual-assign technicianAssigneeId').fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByRole('button', { name: '确认双责任初派' }).click()
  expect((await inboundManualAssignPromise).status()).toBe(200)
  await page.locator('details.assign-advanced').evaluate((el) => {
    ;(el as HTMLDetailsElement).open = true
  })
  await expect(page.locator('details.assign-advanced').getByText(/已初派/)).toBeVisible({
    timeout: 15_000,
  })

  await page
    .getByLabel('assign-candidates principalIds')
    .fill('06b612f3-a901-4b0e-bd90-86b4259cc087')
  await page.getByLabel('sourceId').fill('admin-pilot-inbound-e2e')
  const assignmentResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:assign-candidates`),
  )
  await page.getByRole('button', { name: 'assign-candidates', exact: true }).click()
  expect((await assignmentResponsePromise).status()).toBe(200)

  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await clickTaskCommandButton(page, '领取任务')
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await clickTaskCommandButton(page, '启动任务')
  expect((await startResponsePromise).status()).toBe(200)

  await page.goto(`/tasks/${taskId}`)
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

  const windowStart = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()
  const windowEnd = new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString()
  await page.getByLabel('window.start').fill(windowStart)
  await page.getByLabel('window.end').fill(windowEnd)
  await page.getByLabel('type').selectOption('INSTALLATION')

  const proposePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/appointments`),
  )
  await page.getByRole('button', { name: 'proposeAppointment', exact: true }).click()
  const proposeResponse = await proposePromise
  expect(proposeResponse.status()).toBe(201)
  const proposed = (await proposeResponse.json()) as {
    appointmentId: string
    status: string
  }
  expect(proposed.status).toBe('PROPOSED')
  await expect(page.getByText(`已提议预约 ${proposed.appointmentId}`)).toBeVisible()

  const confirmPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/appointments/${proposed.appointmentId}:confirm`),
  )
  await page.getByRole('button', { name: 'confirm', exact: true }).click()
  const confirmResponse = await confirmPromise
  expect(confirmResponse.status()).toBe(200)
  expect(await confirmResponse.json()).toMatchObject({
    appointmentId: proposed.appointmentId,
    status: 'CONFIRMED',
  })

  const checkInPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/appointments/${proposed.appointmentId}/visits:check-in`,
      ),
  )
  await page.getByRole('button', { name: 'check-in', exact: true }).click()
  const checkInResponse = await checkInPromise
  expect(checkInResponse.status()).toBe(201)
  const checkedIn = (await checkInResponse.json()) as {
    visitId: string
    status: string
  }
  expect(checkedIn.status).toBe('IN_PROGRESS')

  const checkOutPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/visits/${checkedIn.visitId}:check-out`),
  )
  await page.getByRole('button', { name: 'check-out', exact: true }).click()
  const checkOutResponse = await checkOutPromise
  expect(checkOutResponse.status()).toBe(200)
  expect(await checkOutResponse.json()).toMatchObject({
    visitId: checkedIn.visitId,
    status: 'COMPLETED',
  })

  // M142：同一入站 Task 继续表单 → 首轮 Snapshot → REJECTED → 补传/关闭 → 复审 APPROVED
  // → BYD 外发 → 厂端回调 → complete（承接 M141 外发完结，补上同单整改分支）。
  await expect(page.getByRole('cell', { name: 'admin.pilot-inbound-form' })).toBeVisible()
  await page.getByLabel('values JSON').fill('{"survey.note":"ADMIN_PILOT_INBOUND_E2E"}')
  const formResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/form-submissions`),
  )
  await page.getByRole('button', { name: 'submitTaskForm' }).click()
  const formResponse = await formResponsePromise
  expect(formResponse.status()).toBe(201)
  const submission = (await formResponse.json()) as {
    submissionId: string
    contentDigest: string
    validationStatus: string
  }
  expect(submission.validationStatus).toBe('VALIDATED')

  await expect(page.getByRole('cell', { name: 'survey.photo', exact: true })).toBeVisible({
    timeout: 30_000,
  })
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  )
  await page.getByLabel('文件').setInputFiles({
    name: 'admin-pilot-inbound-v1.png',
    mimeType: 'image/png',
    buffer: png,
  })
  const firstFinalizePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const firstFinalize = await firstFinalizePromise
  expect(firstFinalize.status()).toBe(201)
  const firstEvidence = (await firstFinalize.json()) as {
    evidenceItemId: string
    revisions: Array<{ evidenceRevisionId: string }>
  }
  const firstRevisionId = firstEvidence.revisions.at(-1)?.evidenceRevisionId
  expect(firstRevisionId, '首轮 Finalize 未返回 EvidenceRevision').toBeTruthy()

  const orchestrationHeader = page
    .getByRole('heading', { name: '表单 / 资料编排' })
    .locator('..')
  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: firstRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  const firstSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  expect((await firstSnapshotPromise).status()).toBe(201)

  const firstReviewCreatePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const firstReviewCreate = await firstReviewCreatePromise
  expect(firstReviewCreate.status()).toBe(201)
  const rejectedReview = (await firstReviewCreate.json()) as {
    reviewCaseId: string
    status: string
  }
  expect(rejectedReview.status).toBe('OPEN')

  const reviewHref = await page
    .getByRole('link', { name: new RegExp(`打开审核案例 ${rejectedReview.reviewCaseId}`) })
    .getAttribute('href')
  expect(reviewHref, '首轮审核案例深链缺失').toBeTruthy()
  const reviewPage = await page.context().newPage()
  await reviewPage.goto(new URL(reviewHref!, page.url()).toString())
  await reviewPage.getByLabel('decision').selectOption('REJECTED')
  await reviewPage.getByLabel('reasonCodes（逗号分隔）').fill('IMAGE.BLUR')
  await reviewPage.getByLabel('note').fill('Admin pilot inbound resubmit required')
  const rejectPromise = reviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${rejectedReview.reviewCaseId}:decide`),
  )
  await reviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  expect((await rejectPromise).status()).toBe(200)
  await expect(reviewPage.getByText(/已裁决为\s*(REJECTED|已驳回)/)).toBeVisible()

  const { correctionPage, correction } = await openInProgressCorrectionFromFilteredQueue(
    page,
    rejectedReview.reviewCaseId,
    '整改队列未返回本轮入站驳回生成的 CorrectionCase',
  )

  await page.getByLabel('文件').setInputFiles({
    name: 'admin-pilot-inbound-v2.png',
    mimeType: 'image/png',
    buffer: png,
  })
  const secondFinalizePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().includes(`/api/v1/tasks/${taskId}/evidence-slots/`) &&
      response.url().endsWith(':finalize'),
  )
  await page.getByRole('button', { name: 'upload + finalize' }).click()
  const secondFinalize = await secondFinalizePromise
  expect(secondFinalize.status()).toBe(201)
  const secondEvidence = (await secondFinalize.json()) as {
    evidenceItemId: string
    revisions: Array<{ evidenceRevisionId: string }>
  }
  expect(secondEvidence.evidenceItemId).toBe(firstEvidence.evidenceItemId)
  const secondRevisionId = secondEvidence.revisions.at(-1)?.evidenceRevisionId
  expect(secondRevisionId, '补传 Finalize 未返回新 EvidenceRevision').toBeTruthy()
  expect(secondRevisionId).not.toBe(firstRevisionId)

  await expect
    .poll(
      async () => {
        await orchestrationHeader.getByRole('button', { name: '刷新' }).click()
        return page
          .getByRole('row')
          .filter({ hasText: secondRevisionId! })
          .filter({ hasText: /VALIDATED|已校验/ })
          .count()
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(0)

  const secondSnapshotPromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}/evidence-set-snapshots`),
  )
  await page.getByRole('button', { name: 'createEvidenceSetSnapshot' }).click()
  const secondSnapshotResponse = await secondSnapshotPromise
  expect(secondSnapshotResponse.status()).toBe(201)
  const secondSnapshot = (await secondSnapshotResponse.json()) as {
    evidenceSetSnapshotId: string
    contentDigest: string
  }

  await correctionPage
    .getByLabel('resubmit snapshotId')
    .fill(secondSnapshot.evidenceSetSnapshotId)
  const resubmitPromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/correction-cases/${correction!.correctionCaseId}:resubmit`,
      ),
  )
  await correctionPage.getByRole('button', { name: 'resubmit', exact: true }).click()
  expect((await resubmitPromise).status()).toBe(200)
  await expect(correctionPage.getByText(/已补传，\s*(RESUBMITTED|已重新提交)/)).toBeVisible()

  await correctionPage.getByLabel('close note').fill('verified inbound resubmission close')
  const closePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(
        `/api/v1/correction-cases/${correction!.correctionCaseId}:close`,
      ),
  )
  await correctionPage.getByRole('button', { name: 'close', exact: true }).click()
  expect((await closePromise).status()).toBe(200)
  await expect(correctionPage.getByText(/已关闭，\s*(CLOSED|已关闭)/)).toBeVisible()

  const secondReviewCreatePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/review-cases'),
  )
  await page.getByRole('button', { name: 'createReviewCase' }).click()
  const secondReviewCreate = await secondReviewCreatePromise
  expect(secondReviewCreate.status()).toBe(201)
  const approvedReview = (await secondReviewCreate.json()) as {
    reviewCaseId: string
    status: string
    evidenceSetSnapshotId: string
  }
  expect(approvedReview).toMatchObject({
    status: 'OPEN',
    evidenceSetSnapshotId: secondSnapshot.evidenceSetSnapshotId,
  })
  expect(approvedReview.reviewCaseId).not.toBe(rejectedReview.reviewCaseId)

  const reReviewHref = await page
    .getByRole('link', { name: new RegExp(`打开审核案例 ${approvedReview.reviewCaseId}`) })
    .getAttribute('href')
  expect(reReviewHref, '复审案例深链缺失').toBeTruthy()
  const reReviewPage = await page.context().newPage()
  await reReviewPage.goto(new URL(reReviewHref!, page.url()).toString())
  await reReviewPage.getByLabel('note').fill('Admin pilot inbound resubmission approved')
  const approvePromise = reReviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/review-cases/${approvedReview.reviewCaseId}:decide`),
  )
  await reReviewPage.getByRole('button', { name: 'decide', exact: true }).click()
  expect((await approvePromise).status()).toBe(200)
  await expect(reReviewPage.getByText(/已裁决为\s*(APPROVED|已通过)/)).toBeVisible()

  const submitPromise = reReviewPage.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith('/api/v1/internal/integration/byd/review-submissions'),
  )
  await reReviewPage.getByRole('button', { name: 'create BYD review submission', exact: true }).click()
  const submitResponse = await submitPromise
  expect(submitResponse.status()).toBe(201)
  const delivery = (await submitResponse.json()) as {
    deliveryId: string
  }
  expect(delivery.deliveryId).toBeTruthy()

  await reReviewPage.goto(
    new URL(`/integration/outbound/${delivery.deliveryId}`, page.url()).toString(),
  )
  await expect(reReviewPage.getByRole('heading', { name: '外发交付' })).toBeVisible()
  const outboundRefresh = reReviewPage
    .locator('header')
    .filter({ hasText: '外发交付' })
    .getByRole('button', { name: '刷新' })
  await expect
    .poll(
      async () => {
        await outboundRefresh.click()
        await expect(reReviewPage.getByText('加载中…')).toHaveCount(0)
        return reReviewPage.locator('dd', { hasText: /^(ACKNOWLEDGED|已确认)$/ }).count()
      },
      { timeout: 60_000 },
    )
    .toBeGreaterThan(0)

  const clientReviewHref = await reReviewPage
    .getByRole('link', { name: /CLIENT\s*审核案例/ })
    .getAttribute('href')
  expect(clientReviewHref, 'CLIENT 审核案例深链缺失').toBeTruthy()
  const clientReviewCaseId = clientReviewHref!.split('/').filter(Boolean).at(-1)!
  const externalOrderCode = (
    await reReviewPage
      .locator('dt', { hasText: /^externalOrderCode$/ })
      .locator('xpath=../dd')
      .innerText()
  ).trim()
  expect(clientReviewCaseId).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  )
  expect(externalOrderCode).toBe(orderCode)

  const appKey = process.env.SERVICEOS_BYD_CPIM_APP_KEY ?? 'local-byd-app-key'
  const appSecret =
    process.env.SERVICEOS_BYD_CPIM_APP_SECRET ?? 'local-byd-app-secret-change-me'
  const payload = {
    orderCode: externalOrderCode,
    result: '1',
    remark: 'Admin pilot inbound OEM approved after correction',
    examinePerson: 'BYD-PILOT-REVIEWER',
    examineDate: asiaShanghaiDateTimeNow(),
  }
  const nonce = randomUUID()
  const currentDate = asiaShanghaiDateToday()
  const signature = signBydCpimPayload(appSecret, nonce, currentDate, payload)
  const callbackResponse = await request.post(
    'http://127.0.0.1:8080/api/v1/integrations/byd/cpim/v7.3.1/review-results',
    {
      headers: {
        'Content-Type': 'application/json',
        APP_KEY: appKey,
        Nonce: nonce,
        Cur_Time: currentDate,
        Sign: signature,
        'X-Correlation-Id': `admin-pilot-inbound-callback-${delivery.deliveryId}`,
      },
      data: payload,
    },
  )
  expect(callbackResponse.status(), await callbackResponse.text()).toBe(200)

  await reReviewPage.getByRole('link', { name: 'CLIENT 审核案例' }).click()
  await expect(reReviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  const clientRefresh = reReviewPage
    .locator('header')
    .filter({ hasText: '审核案例' })
    .getByRole('button', { name: '刷新' })
  await expect
    .poll(
      async () => {
        await clientRefresh.click()
        await expect(reReviewPage.getByText('加载中…')).toHaveCount(0)
        const status = await reReviewPage
          .locator('dt', { hasText: /^状态$/ })
          .locator('xpath=../dd')
          .innerText()
        const origin = await reReviewPage
          .locator('dt', { hasText: /^来源$/ })
          .locator('xpath=../dd')
          .innerText()
        return `${origin.trim()}:${status.trim()}`
      },
      { timeout: 30_000 },
    )
    .toMatch(/^(CLIENT|客户):(APPROVED|已通过)$/)

  await expect(page.getByLabel('resultRef')).toHaveValue(
    `form-submission://${submission.submissionId}`,
  )
  const inputVersionRefs = JSON.parse(
    await page.getByLabel('inputVersionRefs JSON（双引用可选）').inputValue(),
  )
  expect(inputVersionRefs).toEqual([
    {
      kind: 'FORM_SUBMISSION',
      ref: `form-submission://${submission.submissionId}`,
      digest: submission.contentDigest,
    },
    {
      kind: 'EVIDENCE_SET_SNAPSHOT',
      ref: `evidence-set-snapshot://${secondSnapshot.evidenceSetSnapshotId}`,
      digest: secondSnapshot.contentDigest,
    },
  ])

  const completeResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:complete`),
  )
  await clickTaskCommandButton(page, '完成任务')
  const completeResponse = await completeResponsePromise
  const completeBody = await completeResponse.json()
  expect(completeResponse.status(), JSON.stringify(completeBody)).toBe(200)
  expect(completeBody).toMatchObject({
    taskId,
    status: 'COMPLETED',
  })

  await reReviewPage.close()
  await correctionPage.close()
  await reviewPage.close()
})
