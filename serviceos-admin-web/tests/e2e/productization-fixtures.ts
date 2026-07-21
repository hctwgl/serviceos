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
  /** M385：履约配置中心空列表 */
  fulfillmentEmpty?: boolean
  /** M385：详情页展示 SUSPENDED 只读态 */
  fulfillmentSuspended?: boolean
}

export async function mockProductizationApis(
  page: Page,
  options: ProductizationMockOptions = {},
) {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()
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
            pageId: 'ADMIN.MASTERDATA.CATALOG',
            routeKey: 'master-data',
            title: '主数据治理',
            order: 5,
            section: '基础资料',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.USER.DIRECTORY',
            routeKey: 'users',
            title: '用户目录',
            order: 6,
            section: '基础资料',
            requiredCapabilities: [],
          },
          {
            pageId: 'ADMIN.ROLE.DIRECTORY',
            routeKey: 'roles',
            title: '角色与 Capability',
            order: 7,
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
    if (url.includes('/project-clients') && url.includes('/brands') && url.includes('/status')) {
      await fulfillJson(route, {
        clientCode: 'client-geely',
        brandCode: 'brand-geometry',
        displayName: '几何',
        status: 'DISABLED',
        sortOrder: 1,
      })
      return
    }
    if (url.includes('/project-clients') && url.includes('/brands')) {
      if (method === 'POST') {
        await fulfillJson(
          route,
          {
            clientCode: 'client-geely',
            brandCode: 'brand-geometry',
            displayName: '几何',
            status: 'ACTIVE',
            sortOrder: 1,
          },
          201,
        )
        return
      }
      await fulfillJson(route, {
        items: [
          {
            clientCode: 'client-geely',
            brandCode: 'brand-geometry',
            displayName: '几何',
            status: 'ACTIVE',
            sortOrder: 1,
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/project-clients') && url.includes('/status')) {
      await fulfillJson(route, {
        clientCode: 'client-geely',
        displayName: '吉利汽车',
        status: 'DISABLED',
      })
      return
    }
    if (url.includes('/project-clients')) {
      if (method === 'POST') {
        await fulfillJson(
          route,
          { clientCode: 'client-geely', displayName: '吉利汽车', status: 'ACTIVE' },
          201,
        )
        return
      }
      await fulfillJson(route, {
        items: [{ clientCode: 'client-geely', displayName: '吉利汽车', status: 'ACTIVE' }],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/region-catalog')) {
      const parentMatch = url.match(/[?&]parentCode=([^&]*)/)
      const parentCode = parentMatch ? decodeURIComponent(parentMatch[1]) : ''
      if (parentCode === '440000') {
        await fulfillJson(route, {
          items: [
            {
              regionCode: '440300',
              parentCode: '440000',
              regionName: '深圳市',
              regionLevel: 'CITY',
              sortOrder: 440300,
              childCount: 3,
            },
          ],
          asOf: '2026-07-20T04:00:00Z',
        })
        return
      }
      await fulfillJson(route, {
        items: [
          {
            regionCode: 'CN-3702',
            parentCode: 'CN-3700',
            regionName: '青岛市',
            regionLevel: 'CITY',
            sortOrder: 3702,
            childCount: 2,
          },
          {
            regionCode: 'CN-3700',
            parentCode: null,
            regionName: '山东省',
            regionLevel: 'PROVINCE',
            sortOrder: 3700,
            childCount: 2,
          },
          {
            regionCode: '440000',
            parentCode: null,
            regionName: '广东省',
            regionLevel: 'PROVINCE',
            sortOrder: 440000,
            childCount: 3,
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/projects/reference-options')) {
      await fulfillJson(route, {
        clients: [{ clientId: 'client-geely', displayName: '吉利汽车', projectCount: 1 }],
        regions: [{ regionCode: 'CN-3702', regionName: '青岛市', projectCount: 1 }],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/me/followed-projects/') && url.includes('/status')) {
      await fulfillJson(route, {
        projectId: '22222222-2222-4222-8222-222222222222',
        followed: true,
      })
      return
    }
    if (url.includes('/me/followed-projects')) {
      if (method === 'DELETE') {
        await fulfillJson(route, null, 204)
        return
      }
      if (method === 'PUT') {
        await fulfillJson(route, {
          projectId: '22222222-2222-4222-8222-222222222222',
          displayRef: '演示家充项目',
          projectCode: 'PRJ-DEMO',
          clientId: 'client-geely',
          status: 'ACTIVE',
          followedAt: '2026-07-20T04:00:00Z',
          deepLink: '/projects/22222222-2222-4222-8222-222222222222',
          activeWorkOrderCount: 2,
          activeWorkOrderCountTruncated: false,
          openReviewCount: 1,
          openReviewCountTruncated: false,
          openCorrectionCount: 0,
          openCorrectionCountTruncated: false,
          slaBreachedCount: 1,
          slaBreachedCountTruncated: false,
          openTodoCount: 2,
        })
        return
      }
      await fulfillJson(route, {
        items: [
          {
            projectId: '22222222-2222-4222-8222-222222222222',
            displayRef: '演示家充项目',
            projectCode: 'PRJ-DEMO',
            clientId: 'client-geely',
            status: 'ACTIVE',
            followedAt: '2026-07-20T04:00:00Z',
            deepLink: '/projects/22222222-2222-4222-8222-222222222222',
            activeWorkOrderCount: 2,
            activeWorkOrderCountTruncated: false,
            openReviewCount: 1,
            openReviewCountTruncated: false,
            openCorrectionCount: 0,
            openCorrectionCountTruncated: false,
            slaBreachedCount: 1,
            slaBreachedCountTruncated: false,
            openTodoCount: 2,
          },
        ],
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
            publishedSchemeCount: 1,
            draftSchemeCount: 1,
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
      if (options.fulfillmentEmpty) {
        await fulfillJson(route, [])
        return
      }
      await fulfillJson(route, [
        {
          profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          projectId: '22222222-2222-4222-8222-222222222222',
          serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
          profileName: '标准家充履约方案',
          status: options.fulfillmentSuspended ? 'SUSPENDED' : 'ACTIVE',
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
    const fulfillmentProfileBase =
      '/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    const isFulfillmentAction = (action: string) =>
      url.includes(`${fulfillmentProfileBase}:${action}`) ||
      url.includes(`${fulfillmentProfileBase}%3A${action}`) ||
      url.includes(`${fulfillmentProfileBase}%3a${action}`)

    if (isFulfillmentAction('publish')) {
      await fulfillJson(route, {
        revisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        versionNo: 2,
        revisionStatus: 'PUBLISHED',
        documentJson: '{"stages":[]}',
        createdAt: '2026-07-20T04:10:00Z',
      })
      return
    }
    if (isFulfillmentAction('validate')) {
      await fulfillJson(route, [])
      return
    }
    if (isFulfillmentAction('compile-preview')) {
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
        runbook: {
          profileName: '标准家充履约方案',
          serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
          serviceProductLabel: '家充勘测安装',
          orderTypeName: '勘测安装',
          versionLabel: 'draft-preview',
          stageCount: 1,
          stages: [
            {
              stageName: '现场勘测',
              sequence: 1,
              ownerTypeLabel: '师傅',
              taskTypeLabel: '勘测任务',
              formCount: 0,
              formSummary: '未配置表单',
              evidenceCount: 0,
              evidenceSummary: '未配置必传资料槽位',
              actionCount: 1,
              actionSummary: '允许动作 1 项',
              nextStageSummary: '结束',
              exceptionSummary: '无异常出口',
              slaSummary: '未绑定 SLA',
              terminal: false,
            },
          ],
          clientSupportSummary: '支持客户端：Admin Web、师傅 H5',
          impactSummary: '发布后仅影响生效时间之后的新工单；存量工单继续使用创建时冻结的配置版本。',
        },
      })
      return
    }
    if (url.includes('/fulfillment-profiles/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/compare-impact')) {
      await fulfillJson(route, {
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        draftRevisionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        baselineKind: 'PUBLISHED',
        baselineRevisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        baselineVersionLabel: 'v1',
        changeCount: 1,
        changes: [
          {
            category: 'STAGE',
            changeType: 'MODIFIED',
            summary: '阶段「现场勘测」责任或顺序已调整',
            detail: null,
          },
        ],
        impact: {
          newWorkOrdersScope: '生效时间之后创建的新工单将使用本次发布版本',
          existingWorkOrdersScope: '已创建工单继续使用各自冻结的履约配置版本，不会自动迁移',
          effectiveFromHint: '请在发布确认页设置生效时间',
        },
        risks: [],
        asOf: '2026-07-20T04:00:00Z',
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
      const document = {
        schemaVersion: '1.0.0',
        orderTypeName: '勘测安装',
        supportedClientKinds: ['ADMIN_WEB', 'TECHNICIAN_WEB'],
        stages: [
          {
            stageCode: 'SURVEY',
            stageName: '现场勘测',
            sequence: 1,
            stageType: 'USER_TASK',
            taskType: 'SURVEY',
            ownerType: 'TECHNICIAN',
            description: '上门勘测',
            formRefs: [],
            evidenceRefs: [],
            actions: [],
            transitions: [],
            exceptionPaths: [],
            slaRef: null,
            terminal: false,
          },
        ],
      }
      await fulfillJson(route, {
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        revisionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
        profileName: '标准家充履约方案',
        document,
        documentJson: JSON.stringify(document),
        aggregateVersion: 2,
        updatedAt: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (
      url.includes(fulfillmentProfileBase) &&
      !url.includes('/draft') &&
      !url.includes('/revisions') &&
      !url.includes('/compare-impact') &&
      !isFulfillmentAction('publish') &&
      !isFulfillmentAction('validate') &&
      !isFulfillmentAction('compile-preview') &&
      !isFulfillmentAction('suspend') &&
      !isFulfillmentAction('resume')
    ) {
      await fulfillJson(route, {
        profileId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        projectId: '22222222-2222-4222-8222-222222222222',
        serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
        profileName: '标准家充履约方案',
        description: '演示方案',
        status: options.fulfillmentSuspended ? 'SUSPENDED' : 'ACTIVE',
        draftRevisionId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        activeRevisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        activeVersion: '1',
        activeEffectiveFrom: '2026-07-20T00:00:00Z',
        allowedActions: options.fulfillmentSuspended
          ? ['VIEW', 'VIEW_REVISIONS', 'RESUME']
          : ['VIEW', 'EDIT_DRAFT', 'COMPILE_PREVIEW', 'PUBLISH', 'VALIDATE'],
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
    if (url.includes('/admin/user-directory')) {
      await fulfillJson(route, {
        items: [
          {
            id: '66666666-6666-4666-8666-666666666666',
            type: 'USER',
            status: 'ACTIVE',
            displayName: '演示用户',
            employeeNumber: 'DEMO-001',
            version: 1,
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-20T04:00:00Z',
            organizationSummary: '演示总部',
            roleSummary: 'OPS',
            lastLoginAt: '2026-07-20T03:30:00Z',
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (
      url.includes('/org-memberships') &&
      !url.includes(':transfer') &&
      !url.includes(':terminate')
    ) {
      await fulfillJson(route, {
        items: [
          {
            id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
            organizationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            organizationCode: 'DEMO-HQ',
            organizationName: '演示总部',
            organizationAuthorityMode: 'LOCAL',
            orgUnitId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
            unitCode: 'OPS',
            unitName: '运营部',
            principalId: '66666666-6666-4666-8666-666666666666',
            membershipType: 'PRIMARY',
            status: 'ACTIVE',
            validFrom: '2026-07-01T00:00:00Z',
            validTo: null,
            version: 1,
            createdAt: '2026-07-01T00:00:00Z',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/organizations/') && url.includes('/memberships') && method === 'POST') {
      await fulfillJson(route, {
        id: 'ffffffff-ffff-4fff-8fff-ffffffffffff',
        organizationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
        orgUnitId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
        principalId: '66666666-6666-4666-8666-666666666666',
        membershipType: 'PRIMARY',
        status: 'ACTIVE',
        validFrom: '2026-07-20T04:00:00Z',
        validTo: null,
        sourceSystem: null,
        sourceKey: null,
        sourceVersion: null,
        version: 1,
        createdBy: 'demo',
        createdAt: '2026-07-20T04:00:00Z',
        terminatedBy: null,
        terminatedAt: null,
        terminateReason: null,
      })
      return
    }
    if (
      (url.endsWith('/organizations') || url.includes('/organizations?')) &&
      !url.includes('/organizations/')
    ) {
      await fulfillJson(route, {
        items: [
          {
            id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            code: 'DEMO-HQ',
            name: '演示总部',
            authorityMode: 'LOCAL',
            status: 'ACTIVE',
            sourceSystem: null,
            sourceKey: null,
            version: 1,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-07-01T00:00:00Z',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/organizations/dddddddd-dddd-4ddd-8ddd-dddddddddddd') && method === 'GET') {
      await fulfillJson(route, {
        organization: {
          id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
          code: 'DEMO-HQ',
          name: '演示总部',
          authorityMode: 'LOCAL',
          status: 'ACTIVE',
          sourceSystem: null,
          sourceKey: null,
          version: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-07-01T00:00:00Z',
        },
        units: [
          {
            id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
            organizationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            parentUnitId: null,
            unitCode: 'OPS',
            unitName: '运营部',
            status: 'ACTIVE',
            sourceSystem: null,
            sourceKey: null,
            sourceVersion: null,
            version: 1,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-07-01T00:00:00Z',
          },
          {
            id: '99999999-9999-4999-8999-999999999999',
            organizationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            parentUnitId: null,
            unitCode: 'FLD',
            unitName: '现场部',
            status: 'ACTIVE',
            sourceSystem: null,
            sourceKey: null,
            sourceVersion: null,
            version: 1,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-07-01T00:00:00Z',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/recent-logins')) {
      await fulfillJson(route, {
        items: [
          {
            loginEventId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            principalId: '66666666-6666-4666-8666-666666666666',
            clientId: 'admin-web',
            issuer: 'https://idp.example.com/realms/serviceos',
            authChannel: 'OIDC',
            outcome: 'SUCCEEDED',
            occurredAt: '2026-07-20T03:30:00Z',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/change-timeline')) {
      await fulfillJson(route, {
        items: [
          {
            source: 'LOGIN',
            eventCode: 'LOGIN_SUCCEEDED',
            summary: 'OIDC 登录成功 · 客户端 admin-web',
            actorId: '66666666-6666-4666-8666-666666666666',
            result: 'SUCCEEDED',
            correlationId: 'corr-login',
            principalVersion: null,
            occurredAt: '2026-07-20T03:30:00Z',
            refId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          },
          {
            source: 'LIFECYCLE',
            eventCode: 'REGISTERED',
            summary: '主体已登记 · OIDC_JIT',
            actorId: 'jit-registration',
            result: 'SUCCEEDED',
            correlationId: 'corr-register',
            principalVersion: 1,
            occurredAt: '2026-07-01T00:00:00Z',
            refId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/security-principals') || url.includes('/security/principals')) {
      if (method === 'POST' && !url.includes('/security-principals/')) {
        await fulfillJson(
          route,
          {
            id: '66666666-6666-4666-8666-666666666666',
            type: 'USER',
            status: 'ACTIVE',
            displayName: '演示用户',
            employeeNumber: 'DEMO-001',
            version: 1,
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-20T04:00:00Z',
          },
          201,
        )
        return
      }
      if (url.includes('/security-principals/66666666-6666-4666-8666-666666666666')
        && !url.includes('/identities')
        && !url.includes('/recent-logins')
        && method === 'GET') {
        await fulfillJson(route, {
          principal: {
            id: '66666666-6666-4666-8666-666666666666',
            type: 'USER',
            status: 'ACTIVE',
            displayName: '演示用户',
            employeeNumber: 'DEMO-001',
            version: 1,
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-20T04:00:00Z',
          },
          personas: [
            {
              id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
              personaType: 'INTERNAL_EMPLOYEE',
              status: 'ACTIVE',
              validFrom: '2026-07-01T00:00:00Z',
              validTo: null,
              version: 1,
            },
          ],
          asOf: '2026-07-20T04:00:00Z',
        })
        return
      }
      await fulfillJson(route, {
        items: [
          {
            id: '66666666-6666-4666-8666-666666666666',
            type: 'USER',
            status: 'ACTIVE',
            displayName: '演示用户',
            employeeNumber: 'DEMO-001',
            version: 1,
            createdAt: '2026-07-01T00:00:00Z',
            updatedAt: '2026-07-20T04:00:00Z',
          },
        ],
        nextCursor: null,
        asOf: '2026-07-20T04:00:00Z',
      })
      return
    }
    if (url.includes('/me/capabilities')) {
      await fulfillJson(route, {
        contextId: 'ctx-admin',
        portal: 'ADMIN',
        capabilityCodes: [
          'workOrder.read',
          'project.read',
          'project.create',
          'identity.read',
        ],
        contextVersion: '1',
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
        currentTaskSummary: {
          taskId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          taskType: 'INSTALL',
          status: 'IN_PROGRESS',
          stageCode: 'INSTALLATION',
        },
        serviceAssignmentSummary: {
          networkId: '55555555-5555-4555-8555-555555555555',
          technicianId: '66666666-6666-4666-8666-666666666666',
        },
        slaSummary: { openCount: 1, breachedCount: 0 },
        exceptionSummary: { openCount: 0 },
        timelineFreshnessStatus: 'FRESH',
        allowedActionLink:
          '/api/v1/tasks/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/allowed-actions',
        sectionAvailability: {
          TASKS: 'AVAILABLE',
          TIMELINE_AUDIT: 'AVAILABLE',
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
    if (
      url.includes('/tasks/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/allowed-actions')
    ) {
      await fulfillJson(route, {
        taskId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        resourceVersion: 1,
        asOf: '2026-07-20T04:00:00Z',
        actions: [
          { code: 'complete', label: '完成任务' },
          { code: 'release', label: '释放任务' },
        ],
      })
      return
    }
    if (url.includes('/work-orders/11111111-1111-4111-8111-111111111111/stages')) {
      await fulfillJson(route, {
        workflow: {
          id: 'wf-1',
          projectId: '22222222-2222-4222-8222-222222222222',
          workOrderId: '11111111-1111-4111-8111-111111111111',
          workflowKey: 'home-charging',
          workflowVersion: '1.0.0',
          status: 'ACTIVE',
          version: 1,
          startedAt: '2026-07-20T03:00:00Z',
          completedAt: null,
        },
        stages: [
          {
            id: 'st-1',
            workflowInstanceId: 'wf-1',
            workOrderId: '11111111-1111-4111-8111-111111111111',
            stageCode: 'INTAKE',
            sequenceNo: 1,
            status: 'COMPLETED',
            version: 1,
            activatedAt: '2026-07-20T03:00:00Z',
            completedAt: '2026-07-20T03:10:00Z',
          },
          {
            id: 'st-2',
            workflowInstanceId: 'wf-1',
            workOrderId: '11111111-1111-4111-8111-111111111111',
            stageCode: 'INSTALLATION',
            sequenceNo: 2,
            status: 'ACTIVE',
            version: 1,
            activatedAt: '2026-07-20T03:10:00Z',
            completedAt: null,
          },
          {
            id: 'st-3',
            workflowInstanceId: 'wf-1',
            workOrderId: '11111111-1111-4111-8111-111111111111',
            stageCode: 'FINAL_REVIEW',
            sequenceNo: 3,
            status: 'PENDING',
            version: 1,
            activatedAt: '2026-07-20T03:10:00Z',
            completedAt: null,
          },
        ],
        asOf: '2026-07-20T04:00:00Z',
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
        stages: [],
      })
      return
    }
    await fulfillJson(route, { items: [], nextCursor: null, asOf: '2026-07-20T04:00:00Z' })
  })
}
