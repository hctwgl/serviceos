import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2233-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ed001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ed002'
const SLOT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ed003'
const ITEM_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ed004'

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

async function stubWorkspaceEvidence(
  page: Page,
  options?: { includeEvidence?: boolean },
) {
  const includeEvidence = options?.includeEvidence !== false
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: '2026-07-17T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            displayLabel: '测试网点 A',
            contextVersion: 'ctx-v1',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/navigation**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        contextId: CONTEXT_ID,
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        navigationCatalogVersion: 'page-registry-v16',
        asOf: '2026-07-17T12:00:00Z',
        items: [
          {
            pageId: 'NETWORK.WORKORDER.WORKSPACE',
            routeKey: 'work-order-workspace',
            title: '工单工作区',
            order: 16,
            section: '工单任务',
            requiredCapabilities: ['networkTask.read'],
          },
        ],
      }),
    })
  })
  await page.route(
    `**/api/v1/network-portal/work-orders/${WORK_ORDER_ID}/workspace`,
    async (route: Route) => {
      const body: Record<string, unknown> = {
        networkId: NETWORK_ID,
        workOrderId: WORK_ORDER_ID,
        projectId: null,
        taskIds: [TASK_ID],
        businessType: 'INSTALLATION',
        technicianId: 'tech-a',
        effectiveFrom: '2026-07-17T10:00:00Z',
        asOf: '2026-07-17T12:00:00Z',
        tasks: [
          {
            taskId: TASK_ID,
            workOrderId: WORK_ORDER_ID,
            projectId: null,
            taskType: 'INSTALL',
            taskKind: 'HUMAN',
            stageCode: 'S1',
            status: 'READY',
            businessType: 'INSTALLATION',
            technicianId: 'tech-a',
            effectiveFrom: '2026-07-17T10:00:00Z',
          },
        ],
      }
      if (includeEvidence) {
        body.evidenceSlots = [
          {
            slotId: SLOT_ID,
            taskId: TASK_ID,
            projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ed005',
            templateKey: 'survey.site',
            templateVersion: '1.0.0',
            requirementCode: 'site.photo',
            occurrenceKey: 'default',
            requirementName: '现场照片',
            mediaType: 'PHOTO',
            required: true,
            minCount: 1,
            maxCount: 2,
            status: 'MISSING',
            resolvedAt: '2026-07-17T10:05:00Z',
            slotGeneration: 1,
            active: true,
            transition: 'ACTIVATED',
            requiredDisposition: 'NONE',
          },
        ]
        body.evidenceItems = [
          {
            evidenceItemId: ITEM_ID,
            taskId: TASK_ID,
            projectId: '019f84a0-eeee-7f8c-9505-36fe5c0ed005',
            evidenceSlotId: SLOT_ID,
            itemOrdinal: 1,
            status: 'OPEN',
            revisionCount: 0,
            latestRevisionNumber: null,
            latestRevisionStatus: null,
            latestRevisionId: null,
            latestMimeType: null,
          },
        ]
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
  for (const pattern of [
    '**/api/v1/network-portal/correction-cases**',
    '**/api/v1/network-portal/operational-exceptions**',
    '**/api/v1/network-portal/tasks/*/appointments**',
    '**/api/v1/network-portal/tasks/*/contact-attempts**',
    '**/api/v1/network-portal/technicians**',
  ]) {
    await page.route(pattern, async (route: Route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'ACCESS_DENIED', status: 403 }),
      })
    })
  }
}

test.describe('M223 Network Portal 工作区 Evidence 摘要', () => {
  test('M223-06a：展示 evidenceSlots 与 evidenceItems 摘要', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceEvidence(page)
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-evidence-slots')).toBeVisible()
    await expect(page.getByTestId(`workspace-evidence-slot-${SLOT_ID}`)).toContainText('现场照片')
    await expect(page.getByTestId('workspace-evidence-items')).toBeVisible()
    await expect(page.getByTestId(`workspace-evidence-item-${ITEM_ID}`)).toContainText('待处理')
  })

  test('M223-06b：缺字段时省略区块（不得伪装为空列表）', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceEvidence(page, { includeEvidence: false })
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)
    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-header-fields')).toBeVisible()
    await expect(page.getByTestId('workspace-evidence-slots')).toHaveCount(0)
    await expect(page.getByTestId('workspace-evidence-items')).toHaveCount(0)
  })
})
