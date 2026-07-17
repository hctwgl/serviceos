import {
  apiGet,
  apiGetWithMeta,
  apiPost,
  newIdempotencyKey,
  quotedVersion,
  type ApiResult,
} from './client'

export type PartnerOrganization = {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'DISABLED'
  version: number
  createdAt: string
  updatedAt: string
}

export type ServiceNetwork = {
  id: string
  partnerOrganizationId: string
  networkCode: string
  networkName: string
  status: 'ACTIVE' | 'DEACTIVATED'
  version: number
  createdAt: string
  updatedAt: string
  deactivatedAt: string | null
  deactivatedBy: string | null
  deactivateReason: string | null
}

export type NetworkMembership = {
  id: string
  serviceNetworkId: string
  principalId: string
  role: 'MANAGER' | 'STAFF'
  status: 'ACTIVE' | 'TERMINATED'
  validFrom: string
  validTo: string | null
  version: number
  createdAt: string
}

export type NetworkDeactivationImpact = {
  serviceNetworkId: string
  openTaskCount: number
  openAppointmentCount: number
  openVisitCount: number
  activeAssignmentCount: number
  offlinePackageCount: number
}

export type PartnerOrganizationPage = {
  items: PartnerOrganization[]
  asOf: string
}

export type ServiceNetworkPage = {
  items: ServiceNetwork[]
  asOf: string
}

export type NetworkMembershipPage = {
  items: NetworkMembership[]
  asOf: string
}

export function listPartnerOrganizations() {
  return apiGet<PartnerOrganizationPage>('/partner-organizations')
}

export function createPartnerOrganization(body: {
  code: string
  name: string
}): Promise<ApiResult<PartnerOrganization>> {
  return apiPost('/partner-organizations', {
    idempotencyKey: newIdempotencyKey('partner-create'),
    body,
  })
}

export function getPartnerOrganization(partnerOrganizationId: string) {
  return apiGetWithMeta<PartnerOrganization>(`/partner-organizations/${partnerOrganizationId}`)
}

export function listServiceNetworks() {
  return apiGet<ServiceNetworkPage>('/service-networks')
}

export function createServiceNetwork(body: {
  partnerOrganizationId: string
  networkCode: string
  networkName: string
}): Promise<ApiResult<ServiceNetwork>> {
  return apiPost('/service-networks', {
    idempotencyKey: newIdempotencyKey('network-create'),
    body,
  })
}

export function getServiceNetwork(networkId: string) {
  return apiGetWithMeta<ServiceNetwork>(`/service-networks/${networkId}`)
}

export function getServiceNetworkDeactivationImpact(networkId: string) {
  return apiGet<NetworkDeactivationImpact>(`/service-networks/${networkId}/deactivation-impact`)
}

export function deactivateServiceNetwork(
  networkId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<ServiceNetwork>> {
  return apiPost(`/service-networks/${networkId}:deactivate`, {
    idempotencyKey: newIdempotencyKey('network-deactivate'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function listNetworkMemberships(networkId: string) {
  return apiGet<NetworkMembershipPage>(`/service-networks/${networkId}/memberships`)
}

export function inviteNetworkMember(
  networkId: string,
  body: {
    principalId: string
    role: NetworkMembership['role']
    validFrom: string
  },
): Promise<ApiResult<NetworkMembership>> {
  return apiPost(`/service-networks/${networkId}/memberships`, {
    idempotencyKey: newIdempotencyKey('network-member-invite'),
    body,
  })
}

export function terminateNetworkMembership(
  membershipId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<NetworkMembership>> {
  return apiPost(`/network-memberships/${membershipId}:terminate`, {
    idempotencyKey: newIdempotencyKey('network-member-terminate'),
    ifMatch: quotedVersion(version),
    body,
  })
}
