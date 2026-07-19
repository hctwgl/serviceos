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

async function openDesigner(page: Page) {
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
}

test.describe('M284/M287/M289 Admin 配置设计器与画布', () => {
  test('打开设计器并渲染 WORKFLOW 画布节点', async ({ page }) => {
    await openDesigner(page)
    await expect(page.getByTestId('workflow-canvas')).toBeVisible()
    await expect(page.getByTestId('canvas-node-TASK_A')).toBeVisible()
    await expect(page.getByTestId('canvas-node-GW_BRANCH')).toContainText('EXCLUSIVE_GATEWAY')
  })

  test('拖拽节点后 JSON metadata.layout 更新', async ({ page }) => {
    await openDesigner(page)
    await expect(page.getByTestId('canvas-node-TASK_A')).toBeVisible({ timeout: 15_000 })

    const node = page.getByTestId('canvas-node-TASK_A')
    const box = await node.boundingBox()
    expect(box).toBeTruthy()
    await page.mouse.move(box!.x + 20, box!.y + 20)
    await page.mouse.down()
    await page.mouse.move(box!.x + 140, box!.y + 80, { steps: 8 })
    await page.mouse.up()

    const json = await page.getByTestId('definition-json').inputValue()
    const parsed = JSON.parse(json) as { metadata?: { layout?: { TASK_A?: { x: number; y: number } } } }
    expect(parsed.metadata?.layout?.TASK_A?.x).toBeGreaterThan(40)
  })

  test('连接模式可创建边并编辑网关条件', async ({ page }) => {
    await openDesigner(page)
    await page.getByTestId('connect-mode').click()
    await page.getByTestId('canvas-node-TASK_B').click()
    await page.getByTestId('canvas-node-TASK_A').click()

    const jsonAfterConnect = await page.getByTestId('definition-json').inputValue()
    expect(jsonAfterConnect).toContain('"from": "TASK_B"')
    expect(jsonAfterConnect).toContain('"to": "TASK_A"')

    await page.getByTestId('canvas-edge-t3').click({ force: true })
    await expect(page.getByTestId('edge-editor')).toBeVisible()
    await page.getByTestId('edge-condition-source').fill('workOrder.brandCode == "PLATFORM"')
    await page.getByTestId('save-edge-condition').click()

    const jsonAfterCondition = await page.getByTestId('definition-json').inputValue()
    expect(jsonAfterCondition).toContain('workOrder.brandCode == "PLATFORM"')
  })

  test('M310/M315 RULE 策略设计器条件积木写入 when', async ({ page }) => {
    await openDesigner(page)
    await page.getByTestId('asset-type').selectOption('RULE')
    await expect(page.getByTestId('rule-structure-editor')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('condition-builder').first()).toBeVisible()
    await page.getByTestId('condition-value').first().fill('BYD_OCEAN')
    await expect(page.getByTestId('condition-preview').first()).toContainText(
      'workOrder.brandCode == "BYD_OCEAN"',
    )
    const json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('workOrder.brandCode == "BYD_OCEAN"')
  })

  test('M315 PRICING/INTEGRATION 结构化设计器同步 JSON', async ({ page }) => {
    await openDesigner(page)
    await page.getByTestId('asset-type').selectOption('PRICING')
    await expect(page.getByTestId('pricing-structure-editor')).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('pricing-amount').first().fill('28800')
    await page.getByTestId('pricing-amount').first().dispatchEvent('change')
    let json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('28800')

    await page.getByTestId('asset-type').selectOption('INTEGRATION')
    await expect(page.getByTestId('integration-structure-editor')).toBeVisible()
    await page.getByTestId('add-field-mapping').click()
    json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('map_')
  })

  test('M312 节点属性面板与撤销重做', async ({ page }) => {
    await openDesigner(page)
    await expect(page.getByTestId('workflow-minimap')).toBeVisible()
    await page.getByTestId('palette-node-type').selectOption('WAIT_EVENT')
    await page.getByTestId('add-node').click()
    await expect(page.getByTestId('node-property-panel')).toBeVisible()
    await page.getByTestId('node-prop-name').fill('等待事件节点')
    await page.getByTestId('node-prop-name').dispatchEvent('change')
    let json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('WAIT_EVENT')
    expect(json).toContain('等待事件节点')
    await page.getByTestId('undo-canvas').click()
    json = await page.getByTestId('definition-json').inputValue()
    expect(json).not.toContain('等待事件节点')
    await page.getByTestId('redo-canvas').click()
    json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('等待事件节点')
  })

  test('M313 FORM/EVIDENCE/SLA 可视配置器同步 JSON', async ({ page }) => {
    await openDesigner(page)
    await page.getByTestId('asset-type').selectOption('FORM')
    await expect(page.getByTestId('form-structure-editor')).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('form-title').fill('勘安表单可视')
    await page.getByTestId('form-title').dispatchEvent('change')
    await page.getByTestId('add-form-field').click()
    let json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('勘安表单可视')
    expect(json).toContain('field_')

    await page.getByTestId('asset-type').selectOption('EVIDENCE')
    await expect(page.getByTestId('evidence-structure-editor')).toBeVisible()
    await page.getByTestId('add-evidence-item').click()
    json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('item_')

    await page.getByTestId('asset-type').selectOption('SLA')
    await expect(page.getByTestId('sla-structure-editor')).toBeVisible()
    await page.getByTestId('sla-duration').fill('7200')
    await page.getByTestId('sla-duration').dispatchEvent('change')
    json = await page.getByTestId('definition-json').inputValue()
    expect(json).toContain('7200')
  })
})
