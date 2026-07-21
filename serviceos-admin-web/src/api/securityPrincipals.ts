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

export type AdminUserDirectoryItem = SecurityPrincipal & {
  organizationSummary: string | null
  roleSummary: string | null
  lastLoginAt: string | null
}

export type PrincipalLoginEvent = {
  loginEventId: string
  principalId: string
  clientId: string
  issuer: string
  authChannel: 'OIDC'
  outcome: 'SUCCEEDED'
  occurredAt: string
}

export type PrincipalLoginEventPage = {
  items: PrincipalLoginEvent[]
  asOf: string
}

export type AdminUserDirectoryPage = {
  items: AdminUserDirectoryItem[]
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

export function listAdminUserDirectory(query: Record<string, string | undefined> = {}) {
  return apiGet<AdminUserDirectoryPage>('/admin/user-directory', query)
}

export function registerSecurityPrincipal(body: {
  displayName: string
  employeeNumber?: string | null
  personaType?: PrincipalPersona['personaType'] | null
}): Promise<ApiResult<SecurityPrincipal>> {
  return apiPost('/security-principals', {
    idempotencyKey: newIdempotencyKey('principal-register'),
    body,
  })
}

export function getSecurityPrincipal(principalId: string) {
  return apiGetWithMeta<SecurityPrincipalDetail>(`/security-principals/${principalId}`)
}

export function listPrincipalIdentityLinks(principalId: string) {
  return apiGet<IdentityLink[]>(`/security-principals/${principalId}/identities`)
}

export function listPrincipalRecentLogins(principalId: string, limit = 20) {
  return apiGet<PrincipalLoginEventPage>(`/security-principals/${principalId}/recent-logins`, {
    limit: String(limit),
  })
}

export type PrincipalChangeTimelineItem = {
  source: 'LIFECYCLE' | 'AUDIT' | 'LOGIN' | 'MEMBERSHIP' | 'ROLE_GRANT'
  eventCode: string
  summary: string
  actorId: string
  actorDisplayName: string | null
  result: string
  correlationId: string
  principalVersion: number | null
  occurredAt: string
  refId: string
}

export type PrincipalChangeTimelinePage = {
  items: PrincipalChangeTimelineItem[]
  omittedSources: Array<'MEMBERSHIP' | 'ROLE_GRANT'>
  asOf: string
}

export function listPrincipalChangeTimeline(principalId: string, limit = 50) {
  return apiGet<PrincipalChangeTimelinePage>(
    `/security-principals/${principalId}/change-timeline`,
    { limit: String(limit) },
  )
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
