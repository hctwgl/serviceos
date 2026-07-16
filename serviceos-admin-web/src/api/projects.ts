import { apiGet, apiPost, newIdempotencyKey } from './client'

export type Project = {
  id: string
  tenantId: string
  code: string
  clientId: string
  name: string
  startsOn: string
  endsOn: string | null
  regionCodes?: string[]
  networkIds?: string[]
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
  version: number
  createdAt: string
}

export type ProjectPage = {
  items: Project[]
  nextCursor: string | null
  asOf: string
}

export type CreateProjectRequest = {
  code: string
  clientId: string
  name: string
  startsOn: string
  endsOn?: string | null
  regionCodes?: string[]
  networkIds?: string[]
}

export function listAuthorizedProjects(query: Record<string, string | undefined> = {}) {
  return apiGet<ProjectPage>('/projects', query)
}

export function createProject(body: CreateProjectRequest) {
  return apiPost<Project>('/projects', {
    idempotencyKey: newIdempotencyKey('project-create'),
    body,
  })
}
