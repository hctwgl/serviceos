import { apiGet, apiPost, newIdempotencyKey, type ApiResult } from './client'

export type ProjectClientDirectoryItem = {
  clientCode: string
  displayName: string
  status: 'ACTIVE' | 'DISABLED'
}

export type ProjectClientDirectoryPage = {
  items: ProjectClientDirectoryItem[]
  asOf: string
}

export type RegionCatalogItem = {
  regionCode: string
  parentCode: string | null
  regionName: string
  regionLevel: 'PROVINCE' | 'CITY' | 'DISTRICT'
  sortOrder: number
}

export type RegionCatalogPage = {
  items: RegionCatalogItem[]
  asOf: string
}

export function listProjectClients() {
  return apiGet<ProjectClientDirectoryPage>('/project-clients')
}

export function registerProjectClient(body: {
  clientCode: string
  displayName: string
}): Promise<ApiResult<ProjectClientDirectoryItem>> {
  return apiPost('/project-clients', {
    idempotencyKey: newIdempotencyKey('project-client-register'),
    body,
  })
}

export function listRegionCatalog(query: {
  parentCode?: string
  query?: string
  level?: RegionCatalogItem['regionLevel']
  limit?: number
} = {}) {
  return apiGet<RegionCatalogPage>('/region-catalog', {
    parentCode: query.parentCode,
    query: query.query,
    level: query.level,
    limit: query.limit != null ? String(query.limit) : undefined,
  })
}
