import { newIdempotencyKey, post } from './http'

export type ProjectClientCatalogItem = {
  clientCode: string
  displayName: string
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  updatedAt: string
}

export type ProjectClientBrandCatalogItem = {
  clientCode: string
  brandCode: string
  displayName: string
  status: 'ACTIVE' | 'DISABLED'
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export function createProjectClient(clientCode: string, displayName: string) {
  return post<ProjectClientCatalogItem>('/project-clients', { clientCode, displayName }, {
    'Idempotency-Key': newIdempotencyKey('admin-create-client'),
  }).then((result) => result.data)
}

export function createProjectClientBrand(
  clientCode: string,
  brandCode: string,
  displayName: string,
  sortOrder: number,
) {
  return post<ProjectClientBrandCatalogItem>(
    `/project-clients/${encodeURIComponent(clientCode)}/brands`,
    { brandCode, displayName, sortOrder },
    { 'Idempotency-Key': newIdempotencyKey('admin-create-client-brand') },
  ).then((result) => result.data)
}
