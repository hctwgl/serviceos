import { apiGet, apiPost, newIdempotencyKey, type ApiResult } from './client'

export type CatalogLifecycleStatus = 'ACTIVE' | 'DISABLED'

export type ProjectClientDirectoryItem = {
  clientCode: string
  displayName: string
  status: CatalogLifecycleStatus
}

export type ProjectClientDirectoryPage = {
  items: ProjectClientDirectoryItem[]
  asOf: string
}

export type ProjectClientBrandItem = {
  clientCode: string
  brandCode: string
  displayName: string
  status: CatalogLifecycleStatus
  sortOrder: number
}

export type ProjectClientBrandPage = {
  items: ProjectClientBrandItem[]
  asOf: string
}

export type RegionCatalogItem = {
  regionCode: string
  parentCode: string | null
  regionName: string
  regionLevel: 'PROVINCE' | 'CITY' | 'DISTRICT'
  sortOrder: number
  childCount: number
}

export type RegionCatalogPage = {
  items: RegionCatalogItem[]
  asOf: string
}

export function listProjectClients(status: 'ACTIVE' | 'DISABLED' | 'ALL' = 'ACTIVE') {
  return apiGet<ProjectClientDirectoryPage>('/project-clients', { status })
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

export function setProjectClientStatus(
  clientCode: string,
  status: CatalogLifecycleStatus,
): Promise<ApiResult<ProjectClientDirectoryItem>> {
  return apiPost(`/project-clients/${encodeURIComponent(clientCode)}/status`, {
    idempotencyKey: newIdempotencyKey('project-client-status'),
    body: { status },
  })
}

export function listProjectClientBrands(
  clientCode: string,
  status: 'ACTIVE' | 'DISABLED' | 'ALL' = 'ALL',
) {
  return apiGet<ProjectClientBrandPage>(
    `/project-clients/${encodeURIComponent(clientCode)}/brands`,
    { status },
  )
}

export function registerProjectClientBrand(
  clientCode: string,
  body: { brandCode: string; displayName: string; sortOrder?: number },
): Promise<ApiResult<ProjectClientBrandItem>> {
  return apiPost(`/project-clients/${encodeURIComponent(clientCode)}/brands`, {
    idempotencyKey: newIdempotencyKey('project-client-brand-register'),
    body,
  })
}

export function setProjectClientBrandStatus(
  clientCode: string,
  brandCode: string,
  status: CatalogLifecycleStatus,
): Promise<ApiResult<ProjectClientBrandItem>> {
  return apiPost(
    `/project-clients/${encodeURIComponent(clientCode)}/brands/${encodeURIComponent(brandCode)}/status`,
    {
      idempotencyKey: newIdempotencyKey('project-client-brand-status'),
      body: { status },
    },
  )
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
