import { apiGet, apiGetWithMeta, apiPost, apiPut, newIdempotencyKey, quotedVersion } from './client'

export type DesignerAssetType = 'WORKFLOW' | 'FORM' | 'EVIDENCE' | 'SLA'

export type ConfigurationDraft = {
  draftId: string
  assetType: DesignerAssetType
  assetKey: string
  intendedSemanticVersion: string
  schemaVersion: string
  definitionJson: string
  contentDigest: string
  status: 'DRAFT' | 'VALIDATED' | 'PUBLISHED' | 'DISCARDED'
  baseVersionId: string | null
  publishedVersionId: string | null
  validationErrors: string[]
  aggregateVersion: number
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
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

export function publishConfigurationDraft(draftId: string) {
  return apiPost<ConfigurationDraft>(`/configuration/drafts/${draftId}:publish`, {
    idempotencyKey: newIdempotencyKey('cfg-draft-publish'),
  })
}
