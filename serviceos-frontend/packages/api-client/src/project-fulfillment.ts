import { get, newIdempotencyKey, post, put } from './http'

export type ProjectFulfillmentProfileStatus = 'ACTIVE' | 'DRAFT' | 'RETIRED' | 'SUSPENDED'

export type ProjectFulfillmentProfileSummary = {
  activeVersion: string | null
  aggregateVersion: number
  effectiveFrom: string | null
  evidenceCount: number
  formCount: number
  profileId: string
  profileName: string
  projectId: string
  serviceProductCode: string
  slaSummary: string | null
  stageCount: number
  status: ProjectFulfillmentProfileStatus
  updatedAt: string
  workflowSummary: string | null
}

export type ProjectFulfillmentProfileDetail = {
  activeEffectiveFrom: string | null
  activeRevisionId: string | null
  activeVersion: string | null
  aggregateVersion: number
  allowedActions: string[]
  asOf: string
  createdAt: string
  description: string | null
  draftRevisionId: string | null
  profileId: string
  profileName: string
  projectId: string
  serviceProductCode: string
  status: ProjectFulfillmentProfileStatus
  updatedAt: string
}

export type CreateProjectFulfillmentProfileInput = {
  description?: string
  profileName: string
  serviceProductCode: string
  templateCode: 'BLANK' | 'HOME_CHARGING_SURVEY_INSTALL'
}

export type ProjectFulfillmentStageDraft = {
  actions?: Record<string, unknown>[]
  description: string | null
  evidenceRefs?: string[]
  exceptionPaths?: Record<string, unknown>[]
  formRefs?: string[]
  ownerType: 'NETWORK' | 'PLATFORM' | 'SYSTEM' | 'TECHNICIAN'
  sequence: number
  slaRef: string | null
  stageCode: string
  stageName: string
  stageType: string | null
  taskType: string | null
  terminal: boolean
  transitions?: Record<string, unknown>[]
}

export type ProjectFulfillmentDocument = {
  orderTypeName: string | null
  schemaVersion: string
  stages: ProjectFulfillmentStageDraft[]
  supportedClientKinds?: Array<'ADMIN_WEB' | 'NETWORK_WEB' | 'TECHNICIAN_IOS' | 'TECHNICIAN_WEB'>
}

export type ProjectFulfillmentDraft = {
  aggregateVersion: number
  description: string | null
  document: ProjectFulfillmentDocument
  profileId: string
  profileName: string
  revisionId: string
  serviceProductCode: string
  updatedAt: string
}

export type ProjectFulfillmentValidationIssue = {
  errorCode: string
  fieldPath: string | null
  severity: 'ERROR' | 'INFO' | 'WARNING'
  stageCode: string | null
  suggestion: string | null
  userMessage: string
}

export function loadProjectFulfillmentProfiles(projectId: string) {
  return get<ProjectFulfillmentProfileSummary[]>(`/projects/${projectId}/fulfillment-profiles`)
    .then((result) => result.data)
}

export function loadProjectFulfillmentProfile(projectId: string, profileId: string) {
  return get<ProjectFulfillmentProfileDetail>(`/projects/${projectId}/fulfillment-profiles/${profileId}`)
    .then((result) => result.data)
}

export function createProjectFulfillmentProfile(
  projectId: string,
  input: CreateProjectFulfillmentProfileInput,
) {
  return post<ProjectFulfillmentProfileDetail>(
    `/projects/${projectId}/fulfillment-profiles`,
    input,
    { 'Idempotency-Key': newIdempotencyKey('project-fulfillment-profile') },
  ).then((result) => result.data)
}

export function loadProjectFulfillmentDraft(projectId: string, profileId: string) {
  return get<ProjectFulfillmentDraft>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/draft`,
  ).then((result) => result.data)
}

export function updateProjectFulfillmentDraft(
  projectId: string,
  profileId: string,
  aggregateVersion: number,
  input: {
    description?: string
    document: ProjectFulfillmentDocument
    profileName?: string
  },
) {
  return put<ProjectFulfillmentDraft>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}/draft`,
    input,
    {
      'Idempotency-Key': newIdempotencyKey('project-fulfillment-draft'),
      'If-Match': `"${aggregateVersion}"`,
    },
  ).then((result) => result.data)
}

export function validateProjectFulfillmentDraft(projectId: string, profileId: string) {
  return post<ProjectFulfillmentValidationIssue[]>(
    `/projects/${projectId}/fulfillment-profiles/${profileId}:validate`,
    {},
    { 'Idempotency-Key': newIdempotencyKey('project-fulfillment-validate') },
  ).then((result) => result.data)
}
