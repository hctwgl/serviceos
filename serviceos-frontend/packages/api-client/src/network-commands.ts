import { newIdempotencyKey, post } from './http'

export type PartnerOrganization = {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'DEACTIVATED'
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
}

export function createPartnerOrganization(code: string, name: string) {
  return post<PartnerOrganization>('/partner-organizations', { code, name }, {
    'Idempotency-Key': newIdempotencyKey('admin-create-partner'),
  }).then((result) => result.data)
}

export function createServiceNetwork(
  partnerOrganizationId: string,
  networkCode: string,
  networkName: string,
) {
  return post<ServiceNetwork>('/service-networks', {
    partnerOrganizationId,
    networkCode,
    networkName,
  }, {
    'Idempotency-Key': newIdempotencyKey('admin-create-service-network'),
  }).then((result) => result.data)
}
