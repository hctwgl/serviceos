/**
 * 项目履约配置 API：委托 `@serviceos/core-client` 生成方法，页面禁止手写 URL。
 */
import { createCoreApi, fromRaw, newIdempotencyKey, quotedVersion, type ApiResult } from './coreApi'
import type {
  ProjectFulfillmentCompareImpact,
  ProjectFulfillmentDraft,
  ProjectFulfillmentManifest,
  ProjectFulfillmentProfileDetail,
  ProjectFulfillmentProfileSummary,
  ProjectFulfillmentRevision,
  ProjectFulfillmentRunbook,
  ProjectFulfillmentValidationIssue,
  WorkOrderFulfillmentSnapshot,
} from '@serviceos/core-client'

export type {
  ProjectFulfillmentCompareImpact,
  ProjectFulfillmentDraft,
  ProjectFulfillmentManifest,
  ProjectFulfillmentProfileDetail,
  ProjectFulfillmentProfileSummary,
  ProjectFulfillmentRevision,
  ProjectFulfillmentRunbook,
  ProjectFulfillmentValidationIssue,
  WorkOrderFulfillmentSnapshot,
}

const api = () => createCoreApi()

export function listProjectFulfillmentProfiles(projectId: string) {
  return api().listProjectFulfillmentProfiles({ projectId })
}

export function getProjectFulfillmentProfile(projectId: string, profileId: string) {
  return fromRaw(api().getProjectFulfillmentProfileRaw({ projectId, profileId }))
}

export function createProjectFulfillmentProfile(
  projectId: string,
  body: {
    serviceProductCode: string
    profileName: string
    description?: string
    templateCode?: 'HOME_CHARGING_SURVEY_INSTALL' | 'BLANK'
    copyFromProfileId?: string
  },
): Promise<ApiResult<ProjectFulfillmentProfileDetail>> {
  return fromRaw(
    api().createProjectFulfillmentProfileRaw({
      projectId,
      idempotencyKey: newIdempotencyKey('pfp-create'),
      createProjectFulfillmentProfileRequest: body,
    }),
  )
}

export function getProjectFulfillmentDraft(projectId: string, profileId: string) {
  return fromRaw(api().getProjectFulfillmentDraftRaw({ projectId, profileId }))
}

export function updateProjectFulfillmentDraft(
  projectId: string,
  profileId: string,
  aggregateVersion: number,
  body: {
    profileName?: string
    description?: string
    documentJson: string
    workflowAssetVersionId?: string
    sourceBundleId?: string
  },
) {
  return fromRaw(
    api().updateProjectFulfillmentDraftRaw({
      projectId,
      profileId,
      ifMatch: quotedVersion(aggregateVersion),
      idempotencyKey: newIdempotencyKey('pfp-draft'),
      updateProjectFulfillmentDraftRequest: body,
    }),
  )
}

export function validateProjectFulfillmentDraft(projectId: string, profileId: string) {
  return fromRaw(
    api().validateProjectFulfillmentDraftRaw({
      projectId,
      profileId,
      idempotencyKey: newIdempotencyKey('pfp-validate'),
    }),
  )
}

export function compileProjectFulfillmentPreview(projectId: string, profileId: string) {
  return fromRaw(
    api().compileProjectFulfillmentPreviewRaw({
      projectId,
      profileId,
      idempotencyKey: newIdempotencyKey('pfp-preview'),
    }),
  )
}

export function compareProjectFulfillmentImpact(projectId: string, profileId: string) {
  return api().compareProjectFulfillmentImpact({ projectId, profileId })
}

export function listProjectFulfillmentRevisions(projectId: string, profileId: string) {
  return api().listProjectFulfillmentRevisions({ projectId, profileId })
}

export function getProjectFulfillmentRevision(
  projectId: string,
  profileId: string,
  revisionId: string,
) {
  return api().getProjectFulfillmentRevision({ projectId, profileId, revisionId })
}

export function getWorkOrderFulfillmentSnapshot(workOrderId: string) {
  return api().getWorkOrderFulfillmentSnapshot({ workOrderId })
}

export function hasAllowedAction(
  detail: ProjectFulfillmentProfileDetail | null | undefined,
  action: string,
): boolean {
  return !!detail?.allowedActions?.includes(action)
}
