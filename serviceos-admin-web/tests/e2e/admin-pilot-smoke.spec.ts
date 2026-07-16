import { expect, test } from '@playwright/test'

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

async function prepareOpenReviewCase(
  page: import('@playwright/test').Page,
  workOrderCode: string,
  taskId: string,
  sourceId: string,
  fileName: string,
) {
  await loginWithLocalKeycloak(page)
  await page.getByRole('link', { name: workOrderCode }).click()
  await expect(page.getByRole('heading', { name: '工单工作区' })).toBeVisible()

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
  await page.getByRole('button', { name: '领取任务' }).click()
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await page.getByRole('button', { name: '启动任务' }).click()
  expect((await startResponsePromise).status()).toBe(200)

  await page.getByRole('link', { name: new RegExp(taskId) }).click()
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'completion.photo', exact: true })).toBeVisible({
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
          .filter({ hasText: 'VALIDATED' })
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
  }
  expect(reviewCase.status).toBe('OPEN')

  const reviewHref = await page
    .getByRole('link', { name: new RegExp(`打开审核案例 ${reviewCase.reviewCaseId}`) })
    .getAttribute('href')
  expect(reviewHref, '审核案例深链缺失').toBeTruthy()
  const reviewPage = await page.context().newPage()
  await reviewPage.goto(new URL(reviewHref!, page.url()).toString())
  await expect(reviewPage.getByRole('heading', { name: '审核案例' })).toBeVisible()
  await expect(reviewPage.getByText('OPEN', { exact: true })).toBeVisible()

  return { reviewCase, reviewPage }
}

test('真实 OIDC 登录后可读取核心投影并完成 Task 分配领取释放写链路', async ({ page }) => {
  await loginWithLocalKeycloak(page)
  await expect(page.getByRole('heading', { name: '授权工单目录' })).toBeVisible()
  await expect(page.getByText('加载中…')).toHaveCount(0)
  const pilotLink = page.getByRole('link', { name: 'ADMIN-PILOT-001' })
  // 本地冒烟会按“每轮新建、绝不回退历史事实”累积动态工单；固定基线工单可能不在首屏。
  // 通过真实游标分页查找，既保持目录查询证明，也避免依赖数据库清理或排序巧合。
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

  await expect(page.getByRole('heading', { name: '工单工作区' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '工单权威事实' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Workflow / Stage' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Stage 投影' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '工单 Task 摘要' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '核心时间线' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '工单 SLA 实例' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PILOT_RESPONSE', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PILOT_SURVEY', exact: true }).first()).toBeVisible()

  // 夹具不预置 ACTIVE 候选；先通过 Admin 页面调用 MANUAL assign-candidates，
  // 再由服务端刷新 allowed-actions，使后续 claim 真正依赖本轮候选快照。
  const taskId = '70000000-0000-4000-8000-000000000001'
  await expect(page.getByRole('button', { name: '领取任务' })).toHaveCount(0)

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
  await expect(page.getByRole('button', { name: '领取任务' })).toBeVisible()

  // 继续通过页面真实调用后端命令，证明 allowed-actions、数据库 RoleGrant、候选责任、
  // If-Match 版本与 Idempotency-Key 共同生效；release 后回到 READY，夹具可重复执行。
  const claimResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:claim`),
  )
  await page.getByRole('button', { name: '领取任务' }).click()
  const claimResponse = await claimResponsePromise
  expect(claimResponse.status()).toBe(200)
  await expect(page.getByRole('button', { name: '释放任务' })).toBeVisible()

  const releaseResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:release`),
  )
  await page.getByRole('button', { name: '释放任务' }).click()
  const releaseResponse = await releaseResponsePromise
  expect(releaseResponse.status()).toBe(200)
  await expect(page.getByRole('button', { name: '领取任务' })).toBeVisible()
})

test('真实 OIDC 登录后可完成 Task 并可靠推进 Workflow 与 WorkOrder', async ({ page }) => {
  test.setTimeout(90_000)
  const workOrderCode = process.env.ADMIN_PILOT_COMPLETION_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_COMPLETION_TASK_ID
  expect(workOrderCode, '缺少动态终态验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态终态验证 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.getByRole('link', { name: workOrderCode! }).click()
  await expect(page.getByRole('heading', { name: '工单工作区' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'PILOT_COMPLETION', exact: true }).first()).toBeVisible()

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
  await page.getByRole('button', { name: '领取任务' }).click()
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await page.getByRole('button', { name: '启动任务' }).click()
  expect((await startResponsePromise).status()).toBe(200)

  // M116 页面提交精确锁定的 FormVersion；VALIDATED submission 会把不可变引用和摘要
  // 回填给 complete 面板，浏览器不自行拼接或猜测完成结果。
  await page.getByRole('link', { name: new RegExp(taskId!) }).click()
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
  await expect(page.getByLabel('resultRef')).toHaveValue(
    `form-submission://${submission.submissionId}`,
  )
  await expect(page.getByLabel('resultDigest')).toHaveValue(submission.contentDigest)

  // M38/M39/M40 继续通过真实页面执行 Begin→本地私有 PUT→Finalize，
  // 等待扫描与机器校验 worker 将不可变 Revision 推进至 VALIDATED 后再冻结 Snapshot。
  await expect(page.getByRole('cell', { name: 'completion.photo', exact: true })).toBeVisible({
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
          .filter({ hasText: 'VALIDATED' })
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
  const snapshotResponse = await snapshotResponsePromise
  expect(snapshotResponse.status()).toBe(201)
  const snapshot = (await snapshotResponse.json()) as {
    evidenceSetSnapshotId: string
    contentDigest: string
  }

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
  await expect(reviewPage.getByText('OPEN', { exact: true })).toBeVisible()

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
  await expect(reviewPage.getByText('已裁决为 APPROVED')).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: 'APPROVED', exact: true })).toBeVisible()
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
  await page.getByRole('button', { name: '完成任务' }).click()
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
})

test('真实 OIDC 登录后审核驳回可进入整改队列并授权豁免整改 Task', async ({ page }) => {
  test.setTimeout(90_000)
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
  await expect(reviewPage.getByText('已裁决为 REJECTED')).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: 'REJECTED', exact: true })).toBeVisible()

  // REJECTED 会同事务创建并激活整改 Case/Task。通过真实授权队列定位来源审核，
  // 再在详情页执行 CRITICAL 豁免，证明 Case 终态与整改 Task 取消同时成立。
  const correctionPage = await page.context().newPage()
  const correctionQueueResponsePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/correction-cases') &&
      response.url().includes('status=IN_PROGRESS'),
  )
  await correctionPage.goto(new URL('/corrections', page.url()).toString())
  const correctionQueueResponse = await correctionQueueResponsePromise
  expect(correctionQueueResponse.status()).toBe(200)
  const correctionQueue = (await correctionQueueResponse.json()) as {
    items: Array<{
      correctionCaseId: string
      sourceReviewCaseId: string
      correctionTaskId: string | null
      status: string
    }>
  }
  const correction = correctionQueue.items.find(
    (item) => item.sourceReviewCaseId === reviewCase.reviewCaseId,
  )
  expect(correction, '整改队列未返回本轮驳回生成的 CorrectionCase').toBeTruthy()
  expect(correction).toMatchObject({ status: 'IN_PROGRESS' })
  expect(correction?.correctionTaskId, '驳回未自动创建整改 Task').toBeTruthy()

  await correctionPage
    .getByRole('link', { name: `打开整改案例 ${correction!.correctionCaseId}` })
    .click()
  await expect(correctionPage.getByRole('heading', { name: '整改案例' })).toBeVisible()
  await expect(correctionPage.getByText('IN_PROGRESS', { exact: true })).toBeVisible()
  await expect(correctionPage.getByText(correction!.correctionTaskId!, { exact: true })).toBeVisible()

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
  await expect(correctionPage.getByText('已豁免，status=WAIVED')).toBeVisible()
  await expect(correctionPage.getByText('WAIVED', { exact: true })).toBeVisible()
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
  await expect(reviewPage.getByText('已强制通过：FORCE_APPROVED')).toBeVisible()
  await expect(reviewPage.getByRole('cell', { name: 'FORCE_APPROVED', exact: true })).toBeVisible()

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
    reviewPage.getByText(`重开结果：OPEN / ${reopened.reviewCaseId}`),
  ).toBeVisible()
  await expect(reviewPage.getByText('OPEN', { exact: true })).toBeVisible()
  await expect(
    reviewPage.getByText(reviewCase.reviewCaseId, { exact: true }),
  ).toBeVisible()
  await expect(
    reviewPage.getByText('OEM_REJECTION:ADMIN-PILOT-001', { exact: true }),
  ).toBeVisible()

  await reviewPage.reload()
  await expect(reviewPage.getByText('OPEN', { exact: true })).toBeVisible()
  await expect(
    reviewPage.getByText(reviewCase.reviewCaseId, { exact: true }),
  ).toBeVisible()
  await reviewPage.close()
})

test('真实 OIDC 登录后审核驳回可经补传关闭并复审通过后完结工单', async ({ page }) => {
  test.setTimeout(180_000)
  const workOrderCode = process.env.ADMIN_PILOT_RESUBMIT_WORK_ORDER_CODE
  const taskId = process.env.ADMIN_PILOT_RESUBMIT_TASK_ID
  expect(workOrderCode, '缺少动态补传验证工单编码').toBeTruthy()
  expect(taskId, '缺少动态补传验证 Task ID').toBeTruthy()

  await loginWithLocalKeycloak(page)
  await page.getByRole('link', { name: workOrderCode! }).click()
  await expect(page.getByRole('heading', { name: '工单工作区' })).toBeVisible()

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
  await page.getByRole('button', { name: '领取任务' }).click()
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await page.getByRole('button', { name: '启动任务' }).click()
  expect((await startResponsePromise).status()).toBe(200)

  await page.getByRole('link', { name: new RegExp(taskId!) }).click()
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

  await expect(page.getByRole('cell', { name: 'completion.photo', exact: true })).toBeVisible({
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
          .filter({ hasText: 'VALIDATED' })
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
  await expect(reviewPage.getByText('已裁决为 REJECTED')).toBeVisible()

  const correctionPage = await page.context().newPage()
  const correctionQueuePromise = correctionPage.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/v1/correction-cases') &&
      response.url().includes('status=IN_PROGRESS'),
  )
  await correctionPage.goto(new URL('/corrections', page.url()).toString())
  const correctionQueueResponse = await correctionQueuePromise
  expect(correctionQueueResponse.status()).toBe(200)
  const correctionQueue = (await correctionQueueResponse.json()) as {
    items: Array<{
      correctionCaseId: string
      sourceReviewCaseId: string
      correctionTaskId: string | null
      status: string
    }>
  }
  const correction = correctionQueue.items.find(
    (item) => item.sourceReviewCaseId === rejectedReview.reviewCaseId,
  )
  expect(correction, '整改队列未返回本轮驳回生成的 CorrectionCase').toBeTruthy()
  expect(correction).toMatchObject({ status: 'IN_PROGRESS' })
  expect(correction?.correctionTaskId, '驳回未自动创建整改 Task').toBeTruthy()

  await correctionPage
    .getByRole('link', { name: `打开整改案例 ${correction!.correctionCaseId}` })
    .click()
  await expect(correctionPage.getByRole('heading', { name: '整改案例' })).toBeVisible()

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
          .filter({ hasText: 'VALIDATED' })
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
  await expect(correctionPage.getByText('已补传，status=RESUBMITTED')).toBeVisible()
  await expect(correctionPage.getByText('RESUBMITTED', { exact: true })).toBeVisible()
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
  await expect(correctionPage.getByText('已关闭，status=CLOSED')).toBeVisible()
  await expect(correctionPage.getByText('CLOSED', { exact: true })).toBeVisible()

  // close 不等于审核通过：对补传 Snapshot 新建 INTERNAL ReviewCase 并普通 APPROVED。
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
  await expect(reReviewPage.getByText('已裁决为 APPROVED')).toBeVisible()

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
  await page.getByRole('button', { name: '完成任务' }).click()
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
  await page.getByRole('link', { name: workOrderCode! }).click()
  await expect(page.getByRole('heading', { name: '工单工作区' })).toBeVisible()

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
  await page.getByRole('button', { name: '领取任务' }).click()
  expect((await claimResponsePromise).status()).toBe(200)

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:start`),
  )
  await page.getByRole('button', { name: '启动任务' }).click()
  expect((await startResponsePromise).status()).toBe(200)

  await page.getByRole('link', { name: new RegExp(taskId!) }).click()
  await expect(page.getByRole('heading', { name: '任务详情' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '联系 / 预约 / 上门' })).toBeVisible()

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
})

test('真实 OIDC 登录后可通过审核并创建 BYD 提审外发直至 ACKNOWLEDGED', async ({ page }) => {
  test.setTimeout(180_000)
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
  await expect(reviewPage.getByText('已裁决为 APPROVED')).toBeVisible()

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
        return reviewPage.getByText('ACKNOWLEDGED', { exact: true }).count()
      },
      { timeout: 60_000 },
    )
    .toBeGreaterThan(0)

  await reviewPage.close()
})
