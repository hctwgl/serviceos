import { apiGet, apiGetWithMeta, apiPost, apiPut, newIdempotencyKey, quotedVersion } from './client'

export type DesignerAssetType =
  | 'WORKFLOW'
  | 'FORM'
  | 'EVIDENCE'
  | 'SLA'
  | 'RULE'
  | 'DISPATCH'
  | 'NOTIFICATION'
  | 'ASSIGNEE_POLICY'
  | 'INTEGRATION'
  | 'PRICING'

export type ClientCompatibilityClientReport = {
  clientKind: 'TECHNICIAN_WEB' | 'TECHNICIAN_IOS'
  compatible: boolean
  missingCapabilities: string[]
  notes: string[]
}

export type ClientCompatibilityReport = {
  requiredCapabilities: string[]
  blockingErrors: string[]
  clientReports: ClientCompatibilityClientReport[]
}

export type ConfigurationDraft = {
  draftId: string
  assetType: DesignerAssetType
  assetKey: string
  intendedSemanticVersion: string
  schemaVersion: string
  definitionJson: string
  contentDigest: string
  status: 'DRAFT' | 'VALIDATED' | 'APPROVED' | 'PUBLISHED' | 'DISCARDED'
  baseVersionId: string | null
  publishedVersionId: string | null
  validationErrors: string[]
  approvalRef: string | null
  approvedBy: string | null
  approvedAt: string | null
  aggregateVersion: number
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
  clientCompatibility?: ClientCompatibilityReport | null
}

export type ConfigurationDraftDiff = {
  draftId: string
  baseVersionId: string | null
  baseLabel: string
  draftLabel: string
  unifiedDiff: string
  identical: boolean
}

export type ConfigurationDependencyItem = {
  refField: string
  refValue: string
  sourceNodeId: string | null
  expectedAssetType: string
  status: 'SATISFIED' | 'MISSING' | 'UNKNOWN_TYPE'
  satisfiedVersionId: string | null
  detail: string
}

export type ConfigurationDependencyReport = {
  assetType: string
  assetKey: string
  draftId: string | null
  bundleId: string | null
  complete: boolean
  dependencies: ConfigurationDependencyItem[]
}

export type ConfigurationSimulationStep = {
  index: number
  nodeId: string
  nodeType: string
  action: string
  detail: string
}

export type ConfigurationSimulationReport = {
  assetType: string
  assetKey: string
  draftId: string | null
  outcome: 'COMPLETED' | 'WAITING' | 'FAIL_CLOSED' | 'STEP_LIMIT'
  message: string
  steps: ConfigurationSimulationStep[]
}

export type ConfigurationSimulationContext = {
  workOrder?: { clientCode?: string | null; brandCode?: string | null; serviceProductCode?: string | null }
  region?: { provinceCode?: string | null; cityCode?: string | null; districtCode?: string | null }
  task?: { stageCode?: string | null; taskType?: string | null }
  formValues?: Record<string, unknown>
}

export type CreateConfigurationDraftRequest = {
  assetType: DesignerAssetType
  assetKey: string
  intendedSemanticVersion: string
  schemaVersion: string
  definitionJson: string
  baseVersionId?: string | null
}

export function listConfigurationDrafts(assetType: DesignerAssetType = 'WORKFLOW') {
  return apiGet<ConfigurationDraft[]>('/configuration/drafts', { assetType })
}

export function getConfigurationDraft(draftId: string) {
  return apiGetWithMeta<ConfigurationDraft>(`/configuration/drafts/${draftId}`)
}

export function createConfigurationDraft(body: CreateConfigurationDraftRequest) {
  return apiPost<ConfigurationDraft>('/configuration/drafts', {
    idempotencyKey: newIdempotencyKey('cfg-draft-create'),
    body,
  })
}

export function updateConfigurationDraft(draftId: string, definitionJson: string, aggregateVersion: number) {
  return apiPut<ConfigurationDraft>(`/configuration/drafts/${draftId}`, {
    ifMatch: quotedVersion(aggregateVersion),
    body: { definitionJson },
  })
}

export function validateConfigurationDraft(draftId: string) {
  return apiPost<ConfigurationDraft>(`/configuration/drafts/${draftId}:validate`, {
    idempotencyKey: newIdempotencyKey('cfg-draft-validate'),
  })
}

export function diffConfigurationDraft(draftId: string) {
  return apiGet<ConfigurationDraftDiff>(`/configuration/drafts/${draftId}:diff`)
}

export function analyzeConfigurationDraftDependencies(draftId: string) {
  return apiGet<ConfigurationDependencyReport>(`/configuration/drafts/${draftId}:dependencies`)
}

export function simulateConfigurationDraft(
  draftId: string,
  body: { context?: ConfigurationSimulationContext; maxSteps?: number } = {},
) {
  return apiPost<ConfigurationSimulationReport>(`/configuration/drafts/${draftId}:simulate`, {
    body,
  })
}

export type ConfigurationHistoricalReplayReport = {
  bundleId: string
  bundleCode: string
  bundleVersion: string
  manifestDigest: string
  workflowVersionId: string
  workflowAssetKey: string
  workflowSemanticVersion: string
  outcome: 'COMPLETED' | 'WAITING' | 'FAIL_CLOSED' | 'STEP_LIMIT'
  message: string
  steps: ConfigurationSimulationStep[]
}

export function runConfigurationHistoricalReplay(body: {
  bundleId: string
  workflowAssetKey?: string | null
  context?: ConfigurationSimulationContext
  maxSteps?: number
}) {
  return apiPost<ConfigurationHistoricalReplayReport>('/configuration/replays:run', { body })
}

export function approveConfigurationDraft(draftId: string, approvalRef: string, aggregateVersion: number) {
  return apiPost<ConfigurationDraft>(`/configuration/drafts/${draftId}:approve`, {
    idempotencyKey: newIdempotencyKey('cfg-draft-approve'),
    ifMatch: quotedVersion(aggregateVersion),
    body: { approvalRef },
  })
}

export function publishConfigurationDraft(draftId: string) {
  return apiPost<ConfigurationDraft>(`/configuration/drafts/${draftId}:publish`, {
    idempotencyKey: newIdempotencyKey('cfg-draft-publish'),
  })
}
