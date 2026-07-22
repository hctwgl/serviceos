import { get, newIdempotencyKey, post } from './http'

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
