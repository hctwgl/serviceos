import { get, newIdempotencyKey, post } from './http'

export type AdminProjectCreationOptions = {
  clients: Array<{ code: string; name: string }>
  regions: Array<{ code: string; name: string; level: 'PROVINCE' | 'CITY' | 'DISTRICT'; parentCode: string | null }>
  networks: Array<{ id: string; code: string; name: string; status: 'ACTIVE' }>
  allowedActions: Array<'CREATE_PROJECT'>
  asOf: string
}

export type CreateProjectInput = {
  code: string
  clientId: string
  name: string
  startsOn: string
  endsOn: string | null
  regionCodes: string[]
  networkIds: string[]
}

export type Project = {
  id: string
  tenantId: string
  code: string
  clientId: string
  name: string
  startsOn: string
  endsOn: string | null
  regionCodes: string[]
  networkIds: string[]
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
  version: number
  createdAt: string
  publishedSchemeCount: number | null
  draftSchemeCount: number | null
}

export function loadAdminProjectCreationOptions(regionQuery = '') {
  const query = new URLSearchParams()
  if (regionQuery.trim()) query.set('regionQuery', regionQuery.trim())
  const suffix = query.size ? `?${query.toString()}` : ''
  return get<AdminProjectCreationOptions>(`/admin/projects/creation-options${suffix}`).then((result) => result.data)
}

export function createProject(input: CreateProjectInput) {
  return post<Project>('/projects', input, {
    'Idempotency-Key': newIdempotencyKey('admin-create-project'),
  }).then((result) => result.data)
}
