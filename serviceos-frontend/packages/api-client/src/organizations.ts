import { get } from './http'

export type Organization = {
  id: string
  code: string
  name: string
  authorityMode: 'LOCAL' | 'EXTERNAL_AUTHORITATIVE'
  status: 'ACTIVE' | 'DISABLED'
  sourceSystem: string | null
  sourceKey: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type OrganizationUnit = {
  id: string
  organizationId: string
  parentUnitId: string | null
  unitCode: string
  unitName: string
  status: 'ACTIVE' | 'DISABLED'
  sourceSystem: string | null
  sourceKey: string | null
  sourceVersion: number | null
  version: number
  createdAt: string
  updatedAt: string
}

export type OrganizationPage = { items: Organization[]; asOf: string }
export type OrganizationDetail = { organization: Organization; units: OrganizationUnit[]; asOf: string }

export function loadOrganizations() {
  return get<OrganizationPage>('/organizations').then((result) => result.data)
}

export function loadOrganizationDetail(organizationId: string) {
  return get<OrganizationDetail>(`/organizations/${organizationId}`).then((result) => result.data)
}
