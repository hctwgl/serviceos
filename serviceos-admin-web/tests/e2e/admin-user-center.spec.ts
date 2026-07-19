import { expect, test, type Page } from '@playwright/test'

const DEVELOPER_PRINCIPAL_ID = '06b612f3-a901-4b0e-bd90-86b4259cc087'

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

async function logout(page: Page) {
  await page.goto('/settings/token')
  const logout = page.getByRole('button', { name: '清除本机会话' })
  if (await logout.isVisible().catch(() => false)) {
    await logout.click()
  }
}

test.describe('M187 Admin 统一用户中心', () => {
  test('M187-01/02：按姓名/工号搜索并查看分区详情（不依赖 UUID 粘贴）', async ({ page }) => {
    await loginWithLocalKeycloak(page)

    await expect(page.getByTestId('nav-users')).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('nav-users').click()
    await expect(page.getByRole('heading', { name: '用户目录' })).toBeVisible()

    const searchResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/security-principals') &&
        new URL(response.url()).searchParams.get('query') === 'LOCAL-DEVELOPER',
    )
    await page.getByLabel('user directory search').fill('LOCAL-DEVELOPER')
    await page.getByRole('button', { name: '查询' }).click()
    expect((await searchResponse).status()).toBe(200)

    await expect(page.getByTestId('user-directory-table')).toContainText('Local Developer')
    // 列表交互以显示名/工号为主；打开链接不要求运营粘贴 UUID。
    // 避免命中侧栏「按 ID 打开」。
    await page.getByTestId('user-directory-table').getByRole('link', { name: '打开' }).first().click()

    await expect(page.getByTestId('user-detail-page')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('section-identity')).toBeVisible()
    await expect(page.getByTestId('section-personas')).toBeVisible()
    await expect(page.getByTestId('section-grants')).toBeVisible()
    await expect(page.getByTestId('principal-display-name')).toHaveText('Local Developer')
  })

  test('M187-03/06：组织创建、EXTERNAL 只读徽章、任职影响与重读', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`

    await page.getByTestId('nav-organizations').click()
    await expect(page.getByRole('heading', { name: '企业组织' })).toBeVisible()

    // EXTERNAL_AUTHORITATIVE：来源字段只读边界可见
    await page.getByLabel('organization code').fill(`EXT-${suffix}`)
    await page.getByLabel('organization name').fill(`外部组织-${suffix}`)
    await page.getByLabel('organization authorityMode').selectOption('EXTERNAL_AUTHORITATIVE')
    await page.getByLabel('organization sourceSystem').fill('HR_DEMO')
    await page.getByLabel('organization sourceKey').fill(`ext-key-${suffix}`)
    const createExt = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/v1/organizations'),
    )
    await page.getByRole('button', { name: '创建' }).click()
    expect((await createExt).status()).toBe(200)
    await expect(page.getByTestId('external-authoritative-badge').first()).toBeVisible()
    await expect(page.getByTestId('external-authoritative-badge').first()).toContainText(
      '来源字段只读',
    )

    // LOCAL 组织：单元 + 任职 + 终止影响
    await page.getByLabel('organization authorityMode').selectOption('LOCAL')
    await page.getByLabel('organization code').fill(`LOC-${suffix}`)
    await page.getByLabel('organization name').fill(`本地组织-${suffix}`)
    const createLocal = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/v1/organizations'),
    )
    await page.getByRole('button', { name: '创建' }).click()
    expect((await createLocal).status()).toBe(200)

    await page
      .getByTestId('organization-table')
      .getByRole('row', { name: new RegExp(`本地组织-${suffix}`) })
      .getByRole('link', { name: '打开' })
      .click()
    await expect(page.getByTestId('organization-detail-page')).toBeVisible()
    await page.getByTestId('organization-detail-page').getByRole('button', { name: '刷新', exact: true }).click()
    await expect(page.getByTestId('organization-detail-page')).toBeVisible()

    await page.getByLabel('unit code').fill(`U-${suffix}`)
    await page.getByLabel('unit name').fill(`单元-${suffix}`)
    const createUnit = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/units') &&
        !response.url().includes(':move'),
    )
    await page.getByTestId('versioned-command-form').getByRole('button', { name: '提交' }).click()
    expect((await createUnit).status()).toBe(200)
    await expect(page.getByTestId('command-message')).toContainText('已重读权威状态')

    // 使用 viewer，避免 developer 已有 PRIMARY 任职导致 409。
    await page.getByLabel('principal directory search').fill('LOCAL-VIEWER')
    const pickerSearch = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes('/api/v1/security-principals') &&
        (new URL(response.url()).searchParams.get('query') ?? '').includes('VIEWER'),
    )
    await page.getByRole('button', { name: '搜索' }).click()
    expect((await pickerSearch).status()).toBe(200)
    await page.getByTestId('principal-picker-results').getByRole('button').first().click()
    await expect(page.getByTestId('principal-picker-selected')).toContainText('Limited Viewer')

    await page.getByLabel('membership type').selectOption('SECONDARY')
    const createMembership = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/memberships'),
    )
    await page.getByRole('button', { name: '创建任职' }).click()
    expect((await createMembership).status()).toBe(200)
    await expect(page.getByTestId('membership-list')).toBeVisible()
    await expect(page.getByTestId('impact-panel')).toBeVisible()
  })

  test('M187-05：过期 If-Match 返回 409 并可恢复', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await page.goto(`/users/${DEVELOPER_PRINCIPAL_ID}`)
    await expect(page.getByTestId('user-detail-page')).toBeVisible()
    await expect(page.getByTestId('principal-version')).toBeVisible()

    const originalName = await page.getByTestId('principal-display-name').innerText()
    await page.getByTestId('prepare-stale-if-match').click()
    await expect(page.getByTestId('command-message')).toContainText(/已准备过期 If-Match/)
    await page.getByLabel('profile displayName').fill(`${originalName} Conflict`)

    const conflict = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes(':update-profile'),
    )
    await page.getByRole('button', { name: '保存档案' }).click()
    expect((await conflict).status()).toBe(409)
    await expect(page.getByText(/版本冲突（409）/)).toBeVisible()

    // 刷新后用新版本恢复
    await page.getByLabel('profile displayName').fill(originalName)
    const recovered = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().includes(':update-profile'),
    )
    await page.getByRole('button', { name: '保存档案' }).click()
    expect((await recovered).status()).toBe(200)
    await expect(page.getByTestId('command-message')).toContainText('已重读权威状态')
  })

  test('M187-04：低权限管理员深链失败关闭且不泄露 PII', async ({ page, context }) => {
    // 清掉 Keycloak SSO，避免沿用 developer 会话。
    await context.clearCookies()
    await page.goto('http://127.0.0.1:8081/realms/serviceos/protocol/openid-connect/logout')
    await loginWithLocalKeycloak(page, 'viewer', 'local-viewer-change-me')

    // 导航不应暴露用户中心入口
    await expect(page.getByTestId('nav-users')).toHaveCount(0)
    await expect(page.getByTestId('nav-organizations')).toHaveCount(0)
    await expect(page.getByTestId('nav-grants')).toHaveCount(0)

    const denied = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes(`/api/v1/security-principals/${DEVELOPER_PRINCIPAL_ID}`),
    )
    await page.goto(`/users/${DEVELOPER_PRINCIPAL_ID}`)
    const deniedStatus = (await denied).status()
    expect([401, 403, 404]).toContain(deniedStatus)
    // 鉴权失败关闭时允许 401（会话不可用）或 403/404；页面不得泄露 PII。
    await expect(page.getByTestId('access-denied')).toBeVisible()
    const deniedText = await page.getByTestId('access-denied').innerText()
    expect(['无权访问或不存在', '需要重新登录']).toContain(deniedText)
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('Local Developer')
    expect(body).not.toContain('LOCAL-DEVELOPER')
    expect(body).not.toContain('local-project-admin')
    expect(body).not.toMatch(/1\d{10}/)

    await page.goto(`/organizations/00000000-0000-4000-8000-000000000099`)
    await expect(page.getByTestId('access-denied')).toBeVisible()
    const orgDenied = await page.getByTestId('access-denied').innerText()
    expect(['无权访问或不存在', '需要重新登录']).toContain(orgDenied)

    await logout(page)
  })

  test('M187 授权页：目录选人申请 RoleGrant 并展示影响面板', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await page.getByTestId('nav-grants').click()
    await expect(page.getByRole('heading', { name: '用户授权与委托' })).toBeVisible()

    await page.getByLabel('principal directory search').first().fill('LOCAL-DEVELOPER')
    await page.getByRole('button', { name: '搜索' }).first().click()
    await page.getByTestId('principal-picker-results').getByRole('button').first().click()

    await expect(page.getByTestId('impact-panel').first()).toBeVisible()
    const request = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith('/api/v1/role-grants'),
    )
    await page.getByTestId('request-grant').click()
    const status = (await request).status()
    // 可能因 SoD/重复授权失败关闭；至少证明不依赖 UUID 粘贴，且影响面板存在。
    expect([200, 400, 409]).toContain(status)
    if (status === 200) {
      await expect(page.getByTestId('command-message')).toContainText('已重读权威状态')
    }
  })
})
