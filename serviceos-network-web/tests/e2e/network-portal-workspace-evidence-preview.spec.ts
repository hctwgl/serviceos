import { expect, test, type Page, type Route } from './support/fixture'
import { navigateNetwork } from './support/fixture'

const NETWORK_ID = '019f84a0-2244-7f8c-9505-36fe5c0e8803'
const CONTEXT_ID = `NETWORK|NETWORK|${NETWORK_ID}`
const WORK_ORDER_ID = '019f84a0-aaaa-7f8c-9505-36fe5c0ef001'
const TASK_ID = '019f84a0-bbbb-7f8c-9505-36fe5c0ef002'
const SLOT_ID = '019f84a0-cccc-7f8c-9505-36fe5c0ef003'
const ITEM_ID = '019f84a0-dddd-7f8c-9505-36fe5c0ef004'
const REVISION_ID = '019f84a0-eeee-7f8c-9505-36fe5c0ef005'

async function loginWithLocalKeycloak(page: Page) {
  await page.goto('/settings/token')
  await page.getByRole('button', { name: '使用本地 Keycloak 登录' }).click()
  await page.locator('input[name="username"]').fill('developer')
  await page.locator('input[name="password"]').fill('local-dev-change-me')
  await page.locator('input[type="submit"], button[type="submit"]').click()
  if (page.url().includes('execution=VERIFY_PROFILE')) {
    await page.locator('input[name="email"]').fill('developer@serviceos.local')
    await page.locator('input[type="submit"], button[type="submit"]').click()
  }
  await expect(page).toHaveURL(/\/work-orders$/)
}

async function stubWorkspaceEvidencePreview(page: Page) {
  await page.route('**/api/v1/me/contexts**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        asOf: '2026-07-20T12:00:00Z',
        contexts: [
          {
            contextId: CONTEXT_ID,
            portal: 'NETWORK',
            personaType: 'NETWORK_MEMBER',
            scopeType: 'NETWORK',
            scopeRef: NETWORK_ID,
            displayLabel: '测试网点预览',
            contextVersion: 'ctx-v1',
          },
        ],
      }),
    })
  })
  await page.route('**/api/v1/me/capabilities**', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portal: 'NETWORK',
        contextVersion: 'ctx-v1',
        capabilityCodes: ['networkTask.read', 'evidence.read', 'file.download'],
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
        asOf: '2026-07-20T12:00:00Z',
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
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          networkId: NETWORK_ID,
          workOrderId: WORK_ORDER_ID,
          projectId: null,
          taskIds: [TASK_ID],
          businessType: 'INSTALLATION',
          technicianId: 'tech-a',
          effectiveFrom: '2026-07-20T10:00:00Z',
          asOf: '2026-07-20T12:00:00Z',
          maskedCustomerName: '王*',
          maskedCustomerPhone: '*******5678',
          maskedServiceAddress: '杭州市西湖区***',
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
              effectiveFrom: '2026-07-20T10:00:00Z',
            },
          ],
          evidenceSlots: [
            {
              slotId: SLOT_ID,
              taskId: TASK_ID,
              projectId: '019f84a0-ffff-7f8c-9505-36fe5c0ef006',
              templateKey: 'survey.site',
              templateVersion: '1.0.0',
              requirementCode: 'site.photo',
              occurrenceKey: 'default',
              requirementName: '现场照片',
              mediaType: 'PHOTO',
              required: true,
              minCount: 1,
              maxCount: 2,
              status: 'SATISFIED',
              resolvedAt: '2026-07-20T11:00:00Z',
              slotGeneration: 1,
              active: true,
              transition: 'ACTIVATED',
              requiredDisposition: 'NONE',
            },
          ],
          evidenceItems: [
            {
              evidenceItemId: ITEM_ID,
              taskId: TASK_ID,
              projectId: '019f84a0-ffff-7f8c-9505-36fe5c0ef006',
              evidenceSlotId: SLOT_ID,
              itemOrdinal: 1,
              status: 'SUBMITTED',
              revisionCount: 1,
              latestRevisionNumber: 1,
              latestRevisionStatus: 'STORED',
              latestRevisionId: REVISION_ID,
              latestMimeType: 'image/jpeg',
            },
          ],
        }),
      })
    },
  )
  await page.route(
    `**/api/v1/evidence-revisions/${REVISION_ID}/download-authorizations**`,
    async (route: Route) => {
      if (route.request().method() !== 'POST') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          authorizationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01',
          fileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02',
          method: 'GET',
          downloadUrl: 'https://files.example.test/network-workspace-evidence-preview.jpg',
          requiredHeaders: {},
          expiresAt: '2026-07-20T13:00:00Z',
        }),
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

test.describe('M427 Network Portal 工作区资料授权预览', () => {
  test('M427-01：图片资料经短时授权展示预览', async ({ page }) => {
    await loginWithLocalKeycloak(page)
    await stubWorkspaceEvidencePreview(page)
    await navigateNetwork(page, `/network-portal/work-orders/${WORK_ORDER_ID}`)

    await expect(page.getByTestId('network-portal-work-order-workspace')).toBeVisible({
      timeout: 15_000,
    })
    await expect(page.getByTestId('workspace-evidence-items')).toBeVisible()
    await expect(page.getByTestId(`workspace-evidence-item-${ITEM_ID}`)).toBeVisible()
    await expect(page.getByTestId('workspace-evidence-preview-image')).toBeVisible({
      timeout: 10_000,
    })
    await expect(page.getByTestId('workspace-evidence-preview-image')).toHaveAttribute(
      'src',
      /network-workspace-evidence-preview\.jpg/,
    )
    await expect(page.getByTestId('workspace-evidence-items')).toContainText(
      'WORKSPACE_EVIDENCE_PREVIEW',
    )

    await page.setViewportSize({ width: 1440, height: 1024 })
    await page.screenshot({
      path: 'tests/e2e/__screenshots__/network-workspace-evidence-preview-1440.png',
      fullPage: true,
    })
  })
})
