/**
 * 项目履约配置 API 薄封装。
 *
 * <p>路径与 OpenAPI operationId 对齐；页面禁止手写零散 URL。后续切换到
 * `@serviceos/core-client` 时仅替换本文件实现。</p>
 */
import { apiGet, apiGetWithMeta, apiPost, apiPut, newIdempotencyKey, quotedVersion } from './client'

export type ProjectFulfillmentProfileSummary = {
  profileId: string
  projectId: string
  serviceProductCode: string
  profileName: string
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'RETIRED'
  stageCount?: number
  formCount?: number
  evidenceCount?: number
  activeVersion?: string | null
  effectiveFrom?: string | null
  workflowSummary?: string | null
  slaSummary?: string | null
  aggregateVersion: number
  updatedAt: string
}

export type ProjectFulfillmentProfileDetail = {
  profileId: string
  projectId: string
  serviceProductCode: string
  profileName: string
  description?: string | null
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'RETIRED'
  draftRevisionId?: string | null
  activeRevisionId?: string | null
  activeVersion?: string | null
  activeEffectiveFrom?: string | null
  allowedActions: string[]
  aggregateVersion: number
  createdAt: string
  updatedAt: string
  asOf: string
}

export type ProjectFulfillmentDraft = {
  profileId: string
  revisionId: string
  serviceProductCode: string
  profileName: string
  description?: string | null
  documentJson: string
  workflowAssetVersionId?: string | null
  sourceBundleId?: string | null
  validationJson?: string | null
  aggregateVersion: number
  updatedAt: string
}

export type ProjectFulfillmentRevision = {
  revisionId: string
  profileId: string
  versionNo: number
  revisionStatus: 'DRAFT' | 'PUBLISHED'
  documentJson: string
  manifestJson?: string | null
  validationJson?: string | null
  contentDigest?: string | null
  sourceBundleId?: string | null
  workflowAssetVersionId?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  supersedesRevisionId?: string | null
  publishedBy?: string | null
  publishedAt?: string | null
  createdAt: string
}

export type ProjectFulfillmentManifest = {
  manifestJson: string
  contentDigest: string
}

export type ProjectFulfillmentValidationIssue = {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  errorCode: string
  profileId?: string | null
  stageCode?: string | null
  assetType?: string | null
  assetRef?: string | null
  fieldPath?: string | null
  userMessage: string
  technicalMessage?: string | null
  suggestion?: string | null
}

export function listProjectFulfillmentProfiles(projectId: string) {
  return apiGet<ProjectFulfillmentProfileSummary[]>(
    `/projects/${projectId}/fulfillment-profiles`,
  )
}

export function getProjectFulfillmentProfile(projectId: string, profileId: string) {
  return apiGetWithMeta<ProjectFulfillmentProfileDetail>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}`,
  )
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
) {
  return apiPost<ProjectFulfillmentProfileDetail>(
    `/projects/${projectId}/fulfillment-profiles`,
    {
      idempotencyKey: newIdempotencyKey('pfp-create'),
      body,
    },
  )
}

export function getProjectFulfillmentDraft(projectId: string, profileId: string) {
  return apiGetWithMeta<ProjectFulfillmentDraft>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/draft`,
  )
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
  return apiPut<ProjectFulfillmentDraft>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/draft`,
    {
      idempotencyKey: newIdempotencyKey('pfp-draft'),
      ifMatch: quotedVersion(aggregateVersion),
      body,
    },
  )
}

export function validateProjectFulfillmentDraft(projectId: string, profileId: string) {
  return apiPost<ProjectFulfillmentValidationIssue[]>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}:validate`,
    { idempotencyKey: newIdempotencyKey('pfp-validate') },
  )
}

export function compileProjectFulfillmentPreview(projectId: string, profileId: string) {
  return apiPost<ProjectFulfillmentManifest>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}:compile-preview`,
    { idempotencyKey: newIdempotencyKey('pfp-preview') },
  )
}

export function listProjectFulfillmentRevisions(projectId: string, profileId: string) {
  return apiGet<ProjectFulfillmentRevision[]>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/revisions`,
  )
}

export function getProjectFulfillmentRevision(
  projectId: string,
  profileId: string,
  revisionId: string,
) {
  return apiGet<ProjectFulfillmentRevision>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/revisions/${revisionId}`,
  )
}

export type WorkOrderFulfillmentSnapshot = {
  workOrderId: string
  projectId: string
  serviceProductCode: string
  configKind: 'PROFILE_REVISION' | 'LEGACY_BUNDLE'
  profileId?: string | null
  profileName?: string | null
  revisionId?: string | null
  fulfillmentVersion?: string | null
  configurationBundleId?: string | null
  configurationBundleVersion?: string | null
  configurationBundleDigest?: string | null
  manifestJson?: string | null
  contentDigest?: string | null
  frozenAt?: string | null
  legacyExplanation?: string | null
}

export function getWorkOrderFulfillmentSnapshot(workOrderId: string) {
  return apiGet<WorkOrderFulfillmentSnapshot>(`/work-orders/${workOrderId}/fulfillment-snapshot`)
}
