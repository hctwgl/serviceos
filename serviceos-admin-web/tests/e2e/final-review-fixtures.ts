import type { Page, Route } from '@playwright/test'

export const WORK_ORDER_ID = '11111111-1111-4111-8111-111111111111'
export const REVIEW_CASE_ID = '22222222-2222-4222-8222-222222222222'
export const TARGET_A = '33333333-3333-4333-8333-333333333333'
export const TARGET_B = '44444444-4444-4444-8444-444444444444'

/** 伪造可被草稿逻辑解析 sub 的 JWT（不验签）。 */
export function fakeAccessToken(sub = 'e2e-reviewer') {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ sub })).toString('base64url')
  return `${header}.${payload}.sig`
}

export async function seedLocalSession(page: Page, sub = 'e2e-reviewer') {
  await page.addInitScript((token) => {
    localStorage.setItem('serviceos.accessToken', token)
    localStorage.setItem('serviceos.accessTokenExpiresAt', String(Date.now() + 60 * 60 * 1000))
  }, fakeAccessToken(sub))
}

export function finalReviewFixture(overrides: Record<string, unknown> = {}) {
  const base = {
    data: {
      workOrder: {
        workOrderId: WORK_ORDER_ID,
        displayNo: 'WO-E2E-FR-1',
        projectId: '55555555-5555-4555-8555-555555555555',
        projectName: '终审演示项目',
        statusCode: 'ACTIVE',
        statusLabel: '履约中',
        serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
        serviceProductName: '家充勘安',
        maskedCustomerName: '张**',
        maskedCustomerPhone: '*******5678',
        maskedServiceAddress: '山东省济南市***',
        networkName: '济南网点',
        technicianName: '李师傅',
        deviceModel: null,
        nextActionLabel: '提交平台终审',
      },
      reviewTask: {
        taskId: '66666666-6666-4666-8666-666666666666',
        status: 'READY',
        statusLabel: '待领取',
        assigneeDisplayName: '审核员甲',
        resourceVersion: 2,
        executionGuarded: false,
      },
      reviewCase: {
        reviewCaseId: REVIEW_CASE_ID,
        origin: 'INTERNAL',
        status: 'OPEN',
        aggregateVersion: 1,
        snapshotId: '77777777-7777-4777-8777-777777777777',
        snapshotDigest: 'a'.repeat(64),
        policyVersionId: 'REVIEW_POLICY_V1',
        targetCount: 2,
      },
      sla: {
        status: 'RUNNING',
        startedAt: '2026-07-19T01:00:00Z',
        dueAt: '2026-07-20T01:00:00Z',
        displayText: '剩余约 20 小时',
      },
      gateChecks: [
        {
          code: 'REVIEW_CASE_OPEN',
          label: '审核案例待审',
          status: 'PASS',
          blocking: true,
          detail: null,
        },
        {
          code: 'ALL_TARGETS_DECIDED',
          label: '全部目标已决定',
          status: 'PENDING',
          blocking: false,
          detail: '正式提交前由客户端与服务端共同校验',
        },
      ],
      targetGroups: [
        {
          groupCode: 'INSTALLATION',
          groupLabel: '安装资料',
          displayOrder: 0,
          targets: [
            {
              targetType: 'EvidenceRevision',
              targetId: TARGET_A,
              targetVersion: 1,
              requirementCode: 'installation.pillar',
              requirementLabel: '立柱安装照片',
              requirementDescription: '需清晰可见立柱安装位置',
              groupCode: 'INSTALLATION',
              groupLabel: '安装资料',
              displayOrder: 0,
              required: true,
              slotId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              evidenceItemId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
              revisionId: TARGET_A,
              revisionNo: 1,
              mimeType: 'image/jpeg',
              lifecycleStatus: 'VALIDATED',
              capturedAt: '2026-07-19T02:00:00Z',
              captureSource: 'CAMERA',
              uploaderDisplayName: '李师傅',
              offline: false,
              locationVerdict: '已采集（坐标已脱敏）',
              validationReadiness: 'READY',
              validationResult: 'PASS',
              validationCodes: [],
              validationMessages: [],
              structuredValues: {},
            },
            {
              targetType: 'EvidenceRevision',
              targetId: TARGET_B,
              targetVersion: 1,
              requirementCode: 'charger.nameplate',
              requirementLabel: '设备铭牌照片',
              requirementDescription: '铭牌文字需可辨认',
              groupCode: 'INSTALLATION',
              groupLabel: '安装资料',
              displayOrder: 1,
              required: true,
              slotId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
              evidenceItemId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
              revisionId: TARGET_B,
              revisionNo: 1,
              mimeType: 'image/jpeg',
              lifecycleStatus: 'VALIDATED',
              capturedAt: '2026-07-19T02:05:00Z',
              captureSource: 'CAMERA',
              uploaderDisplayName: '李师傅',
              offline: false,
              locationVerdict: '已采集（坐标已脱敏）',
              validationReadiness: 'READY',
              validationResult: 'WARN',
              validationCodes: ['IMAGE.SOFT'],
              validationMessages: ['清晰度偏低，请人工确认'],
              structuredValues: {},
            },
          ],
        },
      ],
      rejectionReasons: [
        { code: 'IMAGE.BLUR', label: '图片模糊', requiresNote: false },
        { code: 'IMAGE.WRONG_SN', label: '铭牌/SN 与工单不一致', requiresNote: true },
      ],
      allowedActions: [
        { action: 'DECIDE', enabled: true, reason: null },
        { action: 'PREVIEW_EVIDENCE', enabled: true, reason: null },
        { action: 'OPEN_CORRECTION', enabled: false, reason: '驳回后由服务端自动创建' },
        { action: 'VIEW_ONLY', enabled: false, reason: null },
      ],
      defaultTargetRef: { targetType: 'EvidenceRevision', targetId: TARGET_A },
      openCorrectionCaseId: null,
    },
    meta: {
      asOf: '2026-07-19T03:00:00Z',
      projectionCheckpoint: 'final-review.v1:live',
      freshnessStatus: 'FRESH',
      scopeVersion: 3,
      queryId: 'frq-e2e-1',
    },
  }
  return {
    ...base,
    data: { ...base.data, ...overrides },
  }
}

export async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

export type FinalReviewFixtureBody = {
  data: ReturnType<typeof finalReviewFixture>['data'] | null
  meta: ReturnType<typeof finalReviewFixture>['meta']
}

export type MockFinalReviewOptions = {
  fixture?: FinalReviewFixtureBody
  /** FINAL_REVIEW 段延迟（毫秒），用于 loading 态 */
  finalReviewDelayMs?: number
  /** FINAL_REVIEW 段 HTTP 状态；非 2xx 时用于 error 态 */
  finalReviewStatus?: number
  /** decide 响应；默认 200 驳回成功体 */
  decideStatus?: number
  decideBody?: unknown
}

export async function mockFinalReviewApis(page: Page, options: MockFinalReviewOptions = {}) {
  const fixture = options.fixture ?? finalReviewFixture()
  const delayMs = options.finalReviewDelayMs ?? 0
  const finalReviewStatus = options.finalReviewStatus ?? 200
  const decideStatus = options.decideStatus ?? 200
  const decideBody =
    options.decideBody ??
    {
      reviewCaseId: REVIEW_CASE_ID,
      projectId: fixture.data?.workOrder?.projectId,
      taskId: fixture.data?.reviewTask?.taskId,
      evidenceSetSnapshotId: fixture.data?.reviewCase?.snapshotId,
      snapshotContentDigest: fixture.data?.reviewCase?.snapshotDigest,
      scopeType: 'EVIDENCE_SET_SNAPSHOT',
      origin: 'INTERNAL',
      policyVersion: 'REVIEW_POLICY_V1',
      status: 'REJECTED',
      createdBy: 'e2e',
      createdAt: '2026-07-19T01:00:00Z',
      decidedAt: '2026-07-19T03:10:00Z',
      sourceReviewCaseId: null,
      externalSubmissionRef: null,
      callbackBatchRef: null,
      mappingVersionId: null,
      reopenedFromReviewCaseId: null,
      reopenTriggerRef: null,
      decisions: [],
      aggregateVersion: 2,
      derivedOverallDecision: 'REJECTED',
      correctionCaseId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
    }

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = decodeURIComponent(url.pathname)
    const method = route.request().method()

    if (path.endsWith('/me') || path.endsWith('/api/v1/me')) {
      await fulfillJson(route, {
        principalId: 'e2e-reviewer',
        tenantId: 'tenant-e2e',
        displayName: '终审演示员',
        personas: [],
        contextVersion: 'cv-1',
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/me/contexts')) {
      await fulfillJson(route, {
        contexts: [
          {
            contextId: 'ctx-admin',
            portal: 'ADMIN',
            personaType: 'STAFF',
            scopeType: 'TENANT',
            scopeRef: 'tenant-e2e',
            scopeSummary: { organizationIds: [], networkIds: [], projectIds: [] },
            version: '1',
          },
        ],
        contextVersion: 'cv-1',
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/me/navigation')) {
      await fulfillJson(route, {
        contextId: 'ctx-admin',
        portal: 'ADMIN',
        contextVersion: 'cv-1',
        navigationCatalogVersion: 'v1',
        items: [
          {
            pageId: 'ADMIN.WORKORDER.LIST',
            routeKey: 'ADMIN.WORKORDER.LIST',
            title: '工单目录',
            order: 1,
            section: '工单运营',
            requiredCapabilities: [],
          },
        ],
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/me/ui-preferences') || path.includes('/ui-preferences')) {
      await fulfillJson(route, {
        key: 'admin',
        value: {},
        schemaVersion: 1,
        aggregateVersion: 1,
        updatedAt: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/recent-resources')) {
      await fulfillJson(route, { items: [], nextCursor: null })
      return
    }
    if (path.includes(`/work-orders/${WORK_ORDER_ID}/workspace/sections/FINAL_REVIEW`)) {
      if (delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs))
      }
      if (finalReviewStatus >= 400) {
        await fulfillJson(
          route,
          {
            title: '加载失败',
            detail: '终审工作区暂时不可用，请稍后重试',
            errorCode: 'INTERNAL_ERROR',
          },
          finalReviewStatus,
        )
        return
      }
      await fulfillJson(route, fixture, finalReviewStatus)
      return
    }
    if (path.includes(`/work-orders/${WORK_ORDER_ID}/workspace/sections/`)) {
      await fulfillJson(route, {
        section: 'TASKS',
        sourceVersions: { workOrderVersion: 3 },
        meta: {
          asOf: '2026-07-19T03:00:00Z',
          projectionCheckpoint: 'x',
          freshnessStatus: 'FRESH',
          queryId: 's1',
        },
        tasks: { items: [], nextCursor: null },
        timeline: null,
        appointmentsVisits: null,
        formsEvidence: null,
        reviewsCorrections: null,
        integration: null,
      })
      return
    }
    if (path.endsWith(`/work-orders/${WORK_ORDER_ID}/workspace`)) {
      await fulfillJson(route, {
        header: {
          id: WORK_ORDER_ID,
          projectId: fixture.data?.workOrder?.projectId ?? '55555555-5555-4555-8555-555555555555',
          status: 'ACTIVE',
          externalOrderCode: 'WO-E2E-FR-1',
          clientCode: 'BYD',
          receivedAt: '2026-07-19T01:00:00Z',
        },
        currentTaskSummary: {
          taskId: fixture.data?.reviewTask?.taskId ?? null,
          taskType: 'EVIDENCE_REVIEW',
          status: 'READY',
          stageCode: 'REVIEW',
        },
        sectionAvailability: {
          TASKS: 'AVAILABLE',
          FINAL_REVIEW: 'AVAILABLE',
          REVIEWS_CORRECTIONS: 'AVAILABLE',
          TIMELINE_AUDIT: 'EMPTY',
          APPOINTMENTS_VISITS: 'EMPTY',
          FORMS_EVIDENCE: 'EMPTY',
          INTEGRATION: 'EMPTY',
        },
        allowedActionLink: null,
        serviceAssignmentSummary: null,
        slaSummary: null,
        exceptionSummary: null,
        timelineFreshnessStatus: 'FRESH',
        sourceVersions: { workOrderVersion: 3 },
        meta: { asOf: '2026-07-19T03:00:00Z', freshnessStatus: 'FRESH', queryId: 'ws-1' },
      })
      return
    }
    if (path.endsWith(`/work-orders/${WORK_ORDER_ID}`) || path.endsWith(`/work-orders/${WORK_ORDER_ID}/`)) {
      await fulfillJson(route, {
        workOrder: {
          id: WORK_ORDER_ID,
          tenantId: 'tenant-e2e',
          projectId: fixture.data?.workOrder?.projectId ?? '55555555-5555-4555-8555-555555555555',
          clientCode: 'BYD',
          brandCode: 'BYD_OCEAN',
          serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
          externalOrderCode: 'WO-E2E-FR-1',
          status: 'ACTIVE',
          configurationBundleId: '88888888-8888-4888-8888-888888888888',
          configurationBundleCode: 'BUNDLE',
          configurationBundleVersion: '1.0.0',
          configurationBundleDigest: 'b'.repeat(64),
          provinceCode: '370000',
          cityCode: '370100',
          districtCode: '370102',
          externalDispatchedAt: '2026-07-19T00:50:00Z',
          receivedAt: '2026-07-19T01:00:00Z',
          activatedAt: '2026-07-19T01:05:00Z',
          fulfilledAt: null,
          version: 3,
        },
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/activity-summary')) {
      await fulfillJson(route, { items: [], asOf: '2026-07-19T03:00:00Z' })
      return
    }
    if (path.includes('/allowed-actions')) {
      await fulfillJson(route, {
        resourceVersion: 2,
        actions: [],
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/stages')) {
      await fulfillJson(route, {
        workflowInstanceId: null,
        stages: [],
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/tasks') && !path.includes('allowed-actions')) {
      await fulfillJson(route, {
        items: [],
        nextCursor: null,
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/timeline')) {
      await fulfillJson(route, {
        items: [],
        nextCursor: null,
        lastProjectedAt: null,
        freshnessStatus: 'FRESH',
        asOf: '2026-07-19T03:00:00Z',
      })
      return
    }
    if (path.includes('/sla-instances') || path.includes('/pricing')) {
      await fulfillJson(route, { items: [], nextCursor: null, asOf: '2026-07-19T03:00:00Z' })
      return
    }
    if (path.includes('/download-authorizations') && method === 'POST') {
      await fulfillJson(
        route,
        {
          authorizationId: '99999999-9999-4999-8999-999999999999',
          fileId: '88888888-8888-4888-8888-888888888888',
          method: 'GET',
          downloadUrl: 'https://example.test/preview-short-lived.jpg',
          requiredHeaders: {},
          expiresAt: '2026-07-19T04:00:00Z',
        },
        201,
      )
      return
    }
    if ((path.includes(':decide') || path.includes('%3Adecide')) && method === 'POST') {
      if (decideStatus >= 400) {
        await fulfillJson(
          route,
          {
            title: '版本冲突',
            detail: '审核案例版本已变更',
            errorCode: 'VERSION_CONFLICT',
          },
          decideStatus,
        )
        return
      }
      await fulfillJson(route, decideBody, decideStatus)
      return
    }
    await fulfillJson(route, { items: [], nextCursor: null })
  })
}

/** 写入过期 aggregateVersion 的本地草稿，触发 stale-draft 提示。 */
export async function seedStaleDraft(page: Page, aggregateVersion = 0) {
  await page.addInitScript(
    ({ reviewCaseId, version, sub }) => {
      const key = `sos.final-review.draft.${sub}.${reviewCaseId}`
      sessionStorage.setItem(
        key,
        JSON.stringify({
          principalId: sub,
          reviewCaseId,
          aggregateVersion: version,
          savedAt: new Date().toISOString(),
          overallNote: '旧草稿说明',
          targetDecisions: [],
        }),
      )
    },
    { reviewCaseId: REVIEW_CASE_ID, version: aggregateVersion, sub: 'e2e-reviewer' },
  )
}
