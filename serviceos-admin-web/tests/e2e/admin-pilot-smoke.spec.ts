import { expect, test } from '@playwright/test'

test('真实 OIDC 登录后可读取核心投影并完成 Task 领取释放写链路', async ({ page }) => {
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

  // 通过页面真实调用后端命令，证明 allowed-actions、数据库 RoleGrant、候选责任、
  // If-Match 版本与 Idempotency-Key 共同生效；release 后回到 READY，夹具可重复执行。
  const taskId = '70000000-0000-4000-8000-000000000001'
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
