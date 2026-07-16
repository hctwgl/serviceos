import { apiGet, apiGetWithMeta, apiPost, newIdempotencyKey, quotedVersion } from './client'
import type { Project } from './projects'

export type ProjectDetail = {
  project: Project
  asOf: string
}

export type ProjectScopeRelationRevision = {
  revisionId: string
  projectId: string
  regionCodes: string[]
  networkIds: string[]
  addedRegionCodes: string[]
  removedRegionCodes: string[]
  addedNetworkIds: string[]
  removedNetworkIds: string[]
  reason: string
  aggregateVersion: number
  revisedAt: string
}

export type ProjectScopeRelationRevisionPage = {
  items: ProjectScopeRelationRevision[]
  nextCursor: string | null
  asOf: string
}

export function getAuthorizedProject(projectId: string) {
  return apiGet<ProjectDetail>(`/projects/${projectId}`)
}

export function getAuthorizedProjectWithMeta(projectId: string) {
  return apiGetWithMeta<ProjectDetail>(`/projects/${projectId}`)
}

export function listAuthorizedProjectScopeRevisions(
  projectId: string,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<ProjectScopeRelationRevisionPage>(
    `/projects/${projectId}/scope-revisions`,
    query,
  )
}

export function reviseProjectScopeRelations(
  projectId: string,
  aggregateVersion: number,
  body: { regionCodes: string[]; networkIds: string[]; reason: string },
) {
  return apiPost<ProjectScopeRelationRevision>(`/projects/${projectId}:revise-scope-relations`, {
    idempotencyKey: newIdempotencyKey('project-scope'),
    ifMatch: quotedVersion(aggregateVersion),
    body,
  })
}
