import { expect, test, type Page } from '@playwright/test'

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

test.describe('M284/M287 Admin 配置设计器与画布', () => {
  test('打开设计器并渲染 WORKFLOW 画布节点', async ({ page }) => {
    await page.route('**/api/v1/configuration/drafts**', async (route) => {
      if (route.request().method() === 'GET' && !route.request().url().includes(':')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
        return
      }
      await route.continue()
    })

    await loginWithLocalKeycloak(page)
    await page.goto('/configuration/designer')
    await expect(page.getByTestId('configuration-designer')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('workflow-canvas')).toBeVisible()
    await expect(page.getByTestId('canvas-node-TASK_A')).toBeVisible()
    await expect(page.getByTestId('canvas-node-TASK_A')).toContainText('SERVICE_TASK')
  })

  test('拖拽节点后 JSON metadata.layout 更新', async ({ page }) => {
    await page.route('**/api/v1/configuration/drafts**', async (route) => {
      if (route.request().method() === 'GET' && !route.request().url().includes(':')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        })
        return
      }
      await route.continue()
    })

    await loginWithLocalKeycloak(page)
    await page.goto('/configuration/designer')
    await expect(page.getByTestId('canvas-node-TASK_A')).toBeVisible({ timeout: 15_000 })

    const node = page.getByTestId('canvas-node-TASK_A')
    const box = await node.boundingBox()
    expect(box).toBeTruthy()
    await page.mouse.move(box!.x + 20, box!.y + 20)
    await page.mouse.down()
    await page.mouse.move(box!.x + 140, box!.y + 80, { steps: 8 })
    await page.mouse.up()

    const json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('"layout"')
    expect(json).toContain('TASK_A')
    const parsed = JSON.parse(json) as { metadata?: { layout?: { TASK_A?: { x: number; y: number } } } }
    expect(parsed.metadata?.layout?.TASK_A?.x).toBeGreaterThan(40)
  })
})
