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

test('真实 OIDC 登录后可读取核心投影并完成 Task 分配领取释放写链路', async ({ page }) => {
  await loginWithLocalKeycloak(page)
  const pilotLink = page.getByRole('link', { name: 'ADMIN-PILOT-001' })
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

  await page.getByLabel('resultRef').fill(`admin-pilot://completion/${taskId}`)
  await page.getByLabel('resultDigest').fill('6'.repeat(64))
  const completeResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      response.url().endsWith(`/api/v1/tasks/${taskId}:complete`),
  )
  await page.getByRole('button', { name: '完成任务' }).click()
  const completeResponse = await completeResponsePromise
  expect(completeResponse.status()).toBe(200)
  expect(await completeResponse.json()).toMatchObject({
    taskId,
    status: 'COMPLETED',
  })
})
