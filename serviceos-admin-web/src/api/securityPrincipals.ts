import {
  apiGet,
  apiGetWithMeta,
  apiPost,
  newIdempotencyKey,
  quotedVersion,
  type ApiResult,
} from './client'

export type SecurityPrincipal = {
  id: string
  type: 'USER' | 'SERVICE'
  status: 'ACTIVE' | 'DISABLED'
  displayName: string
  employeeNumber: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type PrincipalPersona = {
  id: string
  personaType:
    | 'INTERNAL_EMPLOYEE'
    | 'NETWORK_MEMBER'
    | 'TECHNICIAN'
    | 'CONSUMER'
    | 'SERVICE_ACCOUNT'
  status: 'ACTIVE' | 'DISABLED'
  validFrom: string
  validTo: string | null
  version: number
}

export type SecurityPrincipalPage = {
  items: SecurityPrincipal[]
  nextCursor: string | null
  asOf: string
}

export type SecurityPrincipalDetail = {
  principal: SecurityPrincipal
  personas: PrincipalPersona[]
  asOf: string
}

export type IdentityLink = {
  id: string
  issuer: string
  subject: string
  clientId: string | null
  linkedAt: string
}

export function listSecurityPrincipals(query: Record<string, string | undefined> = {}) {
  return apiGet<SecurityPrincipalPage>('/security-principals', query)
}

export function getSecurityPrincipal(principalId: string) {
  return apiGetWithMeta<SecurityPrincipalDetail>(`/security-principals/${principalId}`)
}

export function listPrincipalIdentityLinks(principalId: string) {
  return apiGet<IdentityLink[]>(`/security-principals/${principalId}/identities`)
}

export function disableSecurityPrincipal(
  principalId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<SecurityPrincipal>> {
  return apiPost(`/security-principals/${principalId}:disable`, {
    idempotencyKey: newIdempotencyKey('principal-disable'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function enableSecurityPrincipal(
  principalId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<SecurityPrincipal>> {
  return apiPost(`/security-principals/${principalId}:enable`, {
    idempotencyKey: newIdempotencyKey('principal-enable'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function updateSecurityPrincipalProfile(
  principalId: string,
  version: number,
  body: { displayName: string; employeeNumber?: string | null },
): Promise<ApiResult<SecurityPrincipal>> {
  return apiPost(`/security-principals/${principalId}:update-profile`, {
    idempotencyKey: newIdempotencyKey('principal-profile'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function addSecurityPrincipalPersona(
  principalId: string,
  version: number,
  body: {
    personaType: PrincipalPersona['personaType']
    validFrom: string
    validTo?: string | null
  },
) {
  return apiPost<PrincipalPersona>(`/security-principals/${principalId}/personas`, {
    idempotencyKey: newIdempotencyKey('principal-persona'),
    ifMatch: quotedVersion(version),
    body,
  })
}
