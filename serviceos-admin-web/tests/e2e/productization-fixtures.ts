import type { Page, Route } from '@playwright/test'

/** 产品化视觉 / a11y / 冒烟共用的会话与 API mock（不伪造业务状态机）。 */

export async function seedLocalSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('serviceos.accessToken', 'visual-mock-token')
    localStorage.setItem('serviceos.accessTokenExpiresAt', String(Date.now() + 60 * 60 * 1000))
  })
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

export type ProductizationMockOptions = {
  workOrdersEmpty?: boolean
  workOrdersError?: boolean
  reviewsEmpty?: boolean
}

export async function mockProductizationApis(
  page: Page,
  options: ProductizationMockOptions = {},
) {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/me/contexts')) {
      await fulfillJson(route, {
        contexts: [
          {
            contextId: 'ctx-admin',
            portal: 'ADMIN',
            personaType: 'INTERNAL_EMPLOYEE',
            scopeType: 'TENANT',
            scopeRef: 'tenant-demo',
            scopeSummary: { organizationIds: [], networkIds: [], projectIds: [] },
            version: '1',
          },
        ],
        contextVersion: '1',
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/me/navigation')) {
      await fulfillJson(route, {
        contextId: 'ctx-admin',
        portal: 'ADMIN',
        contextVersion: '1',
        navigationCatalogVersion: '1',
        items: [
          {
            pageId: 'ADMIN.WORKBENCH',
            routeKey: 'workbench',
            title: '工作台',
            order: 1,
            section: '工作台',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.WORKORDER.LIST',
            routeKey: 'work-orders',
            title: '工单中心',
            order: 2,
            section: '工单运营',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.REVIEW.QUEUE',
            routeKey: 'reviews',
            title: '审核队列',
            order: 3,
            section: '工单运营',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.PROJECT.LIST',
            routeKey: 'projects',
            title: '项目目录',
            order: 4,
            section: '基础资料',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.USER.DIRECTORY',
            routeKey: 'users',
            title: '用户目录',
            order: 5,
            section: '基础资料',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.ROLE.DIRECTORY',
            routeKey: 'roles',
            title: '角色与 Capability',
            order: 6,
            section: '系统管理',
            requiredCapabilities: [],
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/recent-resources')) {
      await fulfillJson(route, { items: [], nextCursor: null })
      return
    }
    if (url.includes('/ui-preferences') || url.includes('/saved-views')) {
      await fulfillJson(route, { items: [], preferences: {} })
      return
    }
    if (url.match(/\/api\/v1\/me(\?|$)/)) {
      await fulfillJson(route, {
        principalId: 'p1',
        tenantId: 't1',
        displayName: '演示运营',
        personas: [],
        contextVersion: '1',
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/work-orders') && !url.includes('/work-orders/')) {
      if (options.workOrdersError) {
        await fulfillJson(route, { title: 'error', detail: 'boom' }, 500)
        return
      }
      await fulfillJson(route, {
        items: options.workOrdersEmpty
          ? []
          : [
              {
                id: '11111111-1111-4111-8111-111111111111',
                projectId: '22222222-2222-4222-8222-222222222222',
                clientCode: 'GEELY',
                brandCode: 'GEELY',
                serviceProductCode: 'INSTALLATION',
                externalOrderCode: 'WO-DEMO-001',
                status: 'ACTIVE',
                receivedAt: '2026-07-20T03:00:00Z',
                version: 1,
              },
            ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/reviews')) {
      await fulfillJson(route, {
        items: options.reviewsEmpty
          ? []
          : [
              {
                reviewCaseId: '33333333-3333-4333-8333-333333333333',
                projectId: '22222222-2222-4222-8222-222222222222',
                taskId: '44444444-4444-4444-8444-444444444444',
                status: 'OPEN',
                origin: 'INTERNAL',
                createdAt: '2026-07-20T03:10:00Z',
                latestDecision: null,
              },
            ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/projects') && !url.includes('/projects/')) {
      await fulfillJson(route, {
        items: [
          {
            id: '22222222-2222-4222-8222-222222222222',
            tenantId: 't1',
            code: 'PRJ-DEMO',
            clientId: 'client-geely',
            name: '演示家充项目',
            startsOn: '2026-01-01',
            endsOn: null,
            status: 'ACTIVE',
            version: 3,
            createdAt: '2026-01-01T00:00:00Z',
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/projects/22222222-2222-4222-8222-222222222222/scope-revisions')) {
      await fulfillJson(route, { items: [], nextCursor: null, asOf: '2026-07-20T04:00:00Z' })
      return
    }
    if (
      url.includes('/projects/22222222-2222-4222-8222-222222222222/fulfillment-profiles')
      && !url.includes('/fulfillment-profiles/')
    ) {
      await fulfillJson(route, [
        {
          profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          projectId: '22222222-2222-4222-8222-222222222222',
          serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
          profileName: '标准家充履约方案',
          status: 'ACTIVE',
          stageCount: 4,
          formCount: 0,
          evidenceCount: 0,
          activeVersion: '1',
          effectiveFrom: '2026-07-20T00:00:00Z',
          workflowSummary: '4 个阶段',
          slaSummary: null,
          aggregateVersion: 2,
          updatedAt: '2026-07-20T04:00:00Z',
        },
      ])
      return
    }
    if (url.includes('/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:compile-preview')) {
      await fulfillJson(route, {
        manifestJson: JSON.stringify({
          profileName: '标准家充履约方案',
          stages: [
            {
              stageCode: 'SURVEY',
              stageName: '现场勘测',
              ownerType: 'TECHNICIAN',
              formRefs: [],
              evidenceRefs: [],
              actions: [{ actionLabel: '提交勘测' }],
              transitions: [{ targetStage: 'INSTALLATION' }],
              exceptionPaths: [],
            },
          ],
        }),
        contentDigest: 'a'.repeat(64),
      })
      return
    }
    if (url.includes('/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/revisions')) {
      await fulfillJson(route, [
        {
          revisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          versionNo: 1,
          revisionStatus: 'PUBLISHED',
          documentJson: '{"stages":[]}',
          createdAt: '2026-07-20T00:00:00Z',
        },
      ])
      return
    }
    if (url.includes('/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/draft')) {
      await fulfillJson(route, {
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        revisionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
        profileName: '标准家充履约方案',
        documentJson: JSON.stringify({
          stages: [
            {
              stageCode: 'SURVEY',
              stageName: '现场勘测',
              sequence: 1,
              ownerType: 'TECHNICIAN',
              formRefs: [],
              evidenceRefs: [],
              actions: [],
            },
          ],
        }),
        aggregateVersion: 2,
        updatedAt: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (
      url.includes('/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
      && !url.includes('/draft')
      && !url.includes('/revisions')
      && !url.includes(':')
    ) {
      await fulfillJson(route, {
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        projectId: '22222222-2222-4222-8222-222222222222',
        serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
        profileName: '标准家充履约方案',
        description: '演示方案',
        status: 'ACTIVE',
        draftRevisionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        activeRevisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        activeVersion: '1',
        activeEffectiveFrom: '2026-07-20T00:00:00Z',
        allowedActions: ['VIEW', 'EDIT_DRAFT', 'COMPILE_PREVIEW', 'PUBLISH'],
        aggregateVersion: 2,
        createdAt: '2026-07-19T00:00:00Z',
        updatedAt: '2026-07-20T04:00:00Z',
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/projects/22222222-2222-4222-8222-222222222222')) {
      await fulfillJson(route, {
        project: {
          id: '22222222-2222-4222-8222-222222222222',
          tenantId: 't1',
          code: 'PRJ-DEMO',
          clientId: 'client-geely',
          name: '演示家充项目',
          startsOn: '2026-01-01',
          endsOn: null,
          regionCodes: ['370100'],
          networkIds: [],
          status: 'ACTIVE',
          version: 3,
          createdAt: '2026-01-01T00:00:00Z',
        },
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/service-networks')) {
      await fulfillJson(route, {
        items: [
          {
            id: '55555555-5555-4555-8555-555555555555',
            partnerOrganizationId: 'po1',
            networkCode: 'JN-01',
            networkName: '济南历下服务中心',
            status: 'ACTIVE',
            version: 1,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-01T00:00:00Z',
            deactivatedAt: null,
            deactivatedBy: null,
            deactivateReason: null,
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/security/principals') || url.includes('/users')) {
      await fulfillJson(route, {
        items: [
          {
            principalId: '66666666-6666-4666-8666-666666666666',
            displayName: '演示用户',
            status: 'ACTIVE',
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/capabilities')) {
      await fulfillJson(route, [
        {
          capabilityCode: 'workOrder.read',
          riskLevel: 'LOW',
          description: '读取工单',
        },
      ])
      return
    }
    if (url.includes('/roles')) {
      await fulfillJson(route, {
        items: [
          {
            roleId: '77777777-7777-4777-8777-777777777777',
            roleCode: 'OPS',
            roleName: '平台运营',
            roleStatus: 'ACTIVE',
            capabilityCodes: ['workOrder.read'],
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    // 工单详情最小壳：避免页面硬失败
    if (url.includes('/work-orders/11111111-1111-4111-8111-111111111111/workspace')) {
      if (url.includes('/sections/')) {
        await fulfillJson(route, { sectionCode: 'TASKS', tasks: { items: [] } })
        return
      }
      await fulfillJson(route, {
        header: {
          id: '11111111-1111-4111-8111-111111111111',
          projectId: '22222222-2222-4222-8222-222222222222',
          status: 'ACTIVE',
          externalOrderCode: 'WO-DEMO-001',
          clientCode: 'GEELY',
          receivedAt: '2026-07-20T03:00:00Z',
        },
        currentTaskSummary: null,
        serviceAssignmentSummary: null,
        slaSummary: { openCount: 0, breachedCount: 0 },
        exceptionSummary: { openCount: 0 },
        timelineFreshnessStatus: 'FRESH',
        allowedActionLink: '/api/v1/tasks/x/allowed-actions',
        sectionAvailability: {
          TASKS: 'EMPTY',
          TIMELINE_AUDIT: 'EMPTY',
          APPOINTMENTS_VISITS: 'EMPTY',
          FORMS_EVIDENCE: 'EMPTY',
          REVIEWS_CORRECTIONS: 'EMPTY',
          FINAL_REVIEW: 'EMPTY',
          INTEGRATION: 'EMPTY',
        },
        meta: { asOf: '2026-07-20T04:00:00Z' },
      })
      return
    }
    if (url.includes('/work-orders/11111111-1111-4111-8111-111111111111')) {
      await fulfillJson(route, {
        workOrder: {
          id: '11111111-1111-4111-8111-111111111111',
          status: 'ACTIVE',
          clientCode: 'GEELY',
          brandCode: 'GEELY',
          serviceProductCode: 'INSTALLATION',
          externalOrderCode: 'WO-DEMO-001',
          version: 1,
        },
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (
      url.includes('/activity') ||
      url.includes('/sla') ||
      url.includes('/stages') ||
      url.includes('/tasks') ||
      url.includes('/timeline') ||
      url.includes('/pricing')
    ) {
      await fulfillJson(route, {
        items: [],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
        emptyHint: null,
        workflow: null,
      })
      return
    }
    await fulfillJson(route, { items: [], nextCursor: null, asOf: '2026-07-20T04:00:00Z' })
  })
}
