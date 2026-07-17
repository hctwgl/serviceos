import {
  apiGet,
  apiGetWithMeta,
  apiPost,
  newIdempotencyKey,
  quotedVersion,
  type ApiResult,
} from './client'

export type TechnicianProfile = {
  id: string
  principalId: string
  displayName: string
  status: 'ACTIVE' | 'DISABLED'
  version: number
  createdAt: string
  updatedAt: string
  disabledAt: string | null
  disabledBy: string | null
  disabledReason: string | null
}

export type NetworkTechnicianMembership = {
  id: string
  serviceNetworkId: string
  technicianProfileId: string
  status: 'ACTIVE' | 'TERMINATED'
  validFrom: string
  validTo: string | null
  version: number
  createdAt: string
}

export type TechnicianQualification = {
  id: string
  technicianProfileId: string
  qualificationCode: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED'
  validFrom: string
  validTo: string | null
  version: number
  submittedAt: string
}

export type TechnicianEligibility = {
  technicianProfileId: string
  serviceNetworkId: string
  evaluatedAt: string
  eligible: boolean
  reason: string | null
}

export type TechnicianProfilePage = {
  items: TechnicianProfile[]
  asOf: string
}

export type NetworkTechnicianMembershipPage = {
  items: NetworkTechnicianMembership[]
  asOf: string
}

export type TechnicianQualificationPage = {
  items: TechnicianQualification[]
  asOf: string
}

export function listTechnicianProfiles() {
  return apiGet<TechnicianProfilePage>('/technician-profiles')
}

export function createTechnicianProfile(body: {
  principalId: string
  displayName: string
}): Promise<ApiResult<TechnicianProfile>> {
  return apiPost('/technician-profiles', {
    idempotencyKey: newIdempotencyKey('tech-profile-create'),
    body,
  })
}

export function getTechnicianProfile(profileId: string) {
  return apiGetWithMeta<TechnicianProfile>(`/technician-profiles/${profileId}`)
}

export function disableTechnicianProfile(
  profileId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<TechnicianProfile>> {
  return apiPost(`/technician-profiles/${profileId}:disable`, {
    idempotencyKey: newIdempotencyKey('tech-profile-disable'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function enableTechnicianProfile(
  profileId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<TechnicianProfile>> {
  return apiPost(`/technician-profiles/${profileId}:enable`, {
    idempotencyKey: newIdempotencyKey('tech-profile-enable'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function getTechnicianEligibility(profileId: string, networkId: string) {
  return apiGet<TechnicianEligibility>(`/technician-profiles/${profileId}/eligibility`, {
    networkId,
  })
}

export function listTechnicianQualifications(profileId: string) {
  return apiGet<TechnicianQualificationPage>(`/technician-profiles/${profileId}/qualifications`)
}

export function listNetworkTechnicianMemberships(query: Record<string, string | undefined> = {}) {
  return apiGet<NetworkTechnicianMembershipPage>('/network-technician-memberships', query)
}

export function createNetworkTechnicianMembership(body: {
  networkId: string
  technicianProfileId: string
  validFrom: string
}): Promise<ApiResult<NetworkTechnicianMembership>> {
  return apiPost('/network-technician-memberships', {
    idempotencyKey: newIdempotencyKey('tech-network-link'),
    body,
  })
}

export function terminateNetworkTechnicianMembership(
  membershipId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<NetworkTechnicianMembership>> {
  return apiPost(`/network-technician-memberships/${membershipId}:terminate`, {
    idempotencyKey: newIdempotencyKey('tech-network-terminate'),
    ifMatch: quotedVersion(version),
    body,
  })
}
