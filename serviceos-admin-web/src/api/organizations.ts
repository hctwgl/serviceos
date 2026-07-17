import {
  apiGet,
  apiGetWithMeta,
  apiPost,
  newIdempotencyKey,
  quotedVersion,
  type ApiResult,
} from './client'

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

export type OrgUnit = {
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

export type OrgMembership = {
  id: string
  organizationId: string
  orgUnitId: string
  principalId: string
  membershipType: 'PRIMARY' | 'SECONDARY' | 'MANAGER'
  status: 'ACTIVE' | 'TERMINATED'
  validFrom: string
  validTo: string | null
  sourceSystem: string | null
  sourceKey: string | null
  sourceVersion: number | null
  version: number
  createdBy: string
  createdAt: string
  terminatedBy: string | null
  terminatedAt: string | null
  terminateReason: string | null
}

export type OrganizationPage = {
  items: Organization[]
  asOf: string
}

export type OrganizationDetail = {
  organization: Organization
  units: OrgUnit[]
  asOf: string
}

export type OrgMembershipPage = {
  items: OrgMembership[]
  asOf: string
}

export type ReassignmentWorkItem = {
  id: string
  organizationId: string
  membershipId: string
  principalId: string
  workItemStatus: 'OPEN' | 'CLOSED'
  reason: string
  createdBy: string
  createdAt: string
  correlationId: string
}

export type ReassignmentWorkItemPage = {
  items: ReassignmentWorkItem[]
  asOf: string
}

export function listOrganizations() {
  return apiGet<OrganizationPage>('/organizations')
}

export function getOrganization(organizationId: string) {
  return apiGetWithMeta<OrganizationDetail>(`/organizations/${organizationId}`)
}

export function createOrganization(body: {
  code: string
  name: string
  authorityMode: Organization['authorityMode']
  sourceSystem?: string | null
  sourceKey?: string | null
}): Promise<ApiResult<Organization>> {
  return apiPost('/organizations', {
    idempotencyKey: newIdempotencyKey('org-create'),
    body,
  })
}

export function createOrganizationUnit(
  organizationId: string,
  organizationVersion: number,
  body: { parentUnitId?: string | null; unitCode: string; unitName: string },
): Promise<ApiResult<OrgUnit>> {
  return apiPost(`/organizations/${organizationId}/units`, {
    idempotencyKey: newIdempotencyKey('org-unit-create'),
    ifMatch: quotedVersion(organizationVersion),
    body,
  })
}

export function moveOrganizationUnit(
  organizationId: string,
  orgUnitId: string,
  unitVersion: number,
  body: { newParentUnitId?: string | null },
): Promise<ApiResult<OrgUnit>> {
  return apiPost(`/organizations/${organizationId}/units/${orgUnitId}:move`, {
    idempotencyKey: newIdempotencyKey('org-unit-move'),
    ifMatch: quotedVersion(unitVersion),
    body,
  })
}

export function listOrganizationMemberships(
  organizationId: string,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<OrgMembershipPage>(`/organizations/${organizationId}/memberships`, query)
}

export function createOrganizationMembership(
  organizationId: string,
  body: {
    unitId: string
    principalId: string
    membershipType: OrgMembership['membershipType']
    validFrom: string
  },
): Promise<ApiResult<OrgMembership>> {
  return apiPost(`/organizations/${organizationId}/memberships`, {
    idempotencyKey: newIdempotencyKey('org-membership-create'),
    body,
  })
}

export function transferOrganizationMembership(
  membershipId: string,
  version: number,
  body: {
    targetUnitId: string
    membershipType?: OrgMembership['membershipType'] | null
    validFrom?: string | null
  },
): Promise<ApiResult<OrgMembership>> {
  return apiPost(`/org-memberships/${membershipId}:transfer`, {
    idempotencyKey: newIdempotencyKey('org-membership-transfer'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function terminateOrganizationMembership(
  membershipId: string,
  version: number,
  body: { reason: string; disablePrincipal: boolean },
): Promise<ApiResult<OrgMembership>> {
  return apiPost(`/org-memberships/${membershipId}:terminate`, {
    idempotencyKey: newIdempotencyKey('org-membership-terminate'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function listOpenReassignmentWorkItems() {
  return apiGet<ReassignmentWorkItemPage>('/reassignment-work-items')
}
