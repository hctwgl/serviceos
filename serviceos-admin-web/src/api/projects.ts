import { apiGet } from './client'

export type Project = {
  id: string
  tenantId: string
  code: string
  clientId: string
  name: string
  startsOn: string
  endsOn: string | null
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
  version: number
  createdAt: string
}

export type ProjectPage = {
  items: Project[]
  nextCursor: string | null
  asOf: string
}

export function listAuthorizedProjects(query: Record<string, string | undefined> = {}) {
  return apiGet<ProjectPage>('/projects', query)
}
