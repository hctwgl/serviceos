import {
  apiGet,
  apiGetWithMeta,
  apiPost,
  newIdempotencyKey,
  quotedVersion,
  type ApiResult,
} from './client'

export type Capability = {
  capabilityCode: string
  capabilityName: string
  riskLevel: 'NORMAL' | 'HIGH' | 'CRITICAL'
}

export type Role = {
  roleId: string
  roleCode: string
  roleName: string
  roleKind: 'PLATFORM_TEMPLATE' | 'TENANT'
  roleStatus: 'ACTIVE' | 'DISABLED'
  description?: string | null
  capabilityCodes: string[]
  version: number
  createdAt: string
  updatedAt: string
}

export type RoleGrant = {
  grantId: string
  principalId: string
  roleId: string
  roleCode: string
  scopeType: 'TENANT' | 'PROJECT' | 'REGION' | 'NETWORK'
  scopeRef: string
  grantStatus: 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'REVOKED'
  grantEffect: 'ALLOW' | 'DENY'
  validFrom: string
  validTo: string | null
  sourceCode: string
  requestedBy: string | null
  requestReason: string | null
  approvedBy: string | null
  approvedAt: string | null
  rejectedBy: string | null
  rejectedAt: string | null
  rejectReason: string | null
  revokedAt: string | null
  revokedBy: string | null
  revokeReason: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type Delegation = {
  delegationId: string
  delegatorPrincipalId: string
  delegatePrincipalId: string
  capabilityCodes: string[]
  scopeType: 'TENANT' | 'PROJECT' | 'REGION' | 'NETWORK'
  scopeRef: string
  validFrom: string
  validTo: string | null
  reason: string
  delegationStatus: 'ACTIVE' | 'REVOKED'
  version: number
  createdAt: string
  updatedAt: string
  revokedAt: string | null
  revokedBy: string | null
  revokeReason: string | null
}

export type AuthorizationExplainResult = {
  effect: 'ALLOW' | 'DENY'
  reasonCodes: string[]
  matchedGrantIds: string[]
  dataScopeExplanations: string[]
  obligations: string[]
  policyVersion: string
}

export type RolePage = {
  items: Role[]
  asOf: string
}

export type RoleGrantPage = {
  items: RoleGrant[]
  asOf: string
}

export type DelegationPage = {
  items: Delegation[]
  asOf: string
}

export function listCapabilities() {
  return apiGet<Capability[]>('/capabilities')
}

export function listRoles() {
  return apiGet<RolePage>('/roles')
}

export function getRole(roleId: string) {
  return apiGetWithMeta<Role>(`/roles/${roleId}`)
}

export function createRole(body: {
  roleCode: string
  roleName: string
  description?: string | null
  capabilityCodes: string[]
}): Promise<ApiResult<Role>> {
  return apiPost('/roles', {
    idempotencyKey: newIdempotencyKey('role-create'),
    body,
  })
}

export function listRoleGrants(query: Record<string, string | undefined> = {}) {
  return apiGet<RoleGrantPage>('/role-grants', query)
}

export function requestRoleGrant(body: {
  principalId: string
  roleId: string
  scopeType: RoleGrant['scopeType']
  scopeRef: string
  grantEffect?: 'ALLOW' | 'DENY' | null
  validFrom: string
  validTo?: string | null
  requestReason: string
}): Promise<ApiResult<RoleGrant>> {
  return apiPost('/role-grants', {
    idempotencyKey: newIdempotencyKey('role-grant-request'),
    body,
  })
}

export function decideRoleGrant(
  grantId: string,
  version: number,
  body: { decision: 'APPROVE' | 'REJECT'; note?: string | null },
): Promise<ApiResult<RoleGrant>> {
  return apiPost(`/role-grants/${grantId}:approve`, {
    idempotencyKey: newIdempotencyKey('role-grant-decide'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function revokeRoleGrant(
  grantId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<RoleGrant>> {
  return apiPost(`/role-grants/${grantId}:revoke`, {
    idempotencyKey: newIdempotencyKey('role-grant-revoke'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function listDelegations(query: Record<string, string | undefined> = {}) {
  return apiGet<DelegationPage>('/delegations', query)
}

export function createDelegation(body: {
  delegatePrincipalId: string
  capabilityCodes: string[]
  scopeType: Delegation['scopeType']
  scopeRef: string
  validFrom: string
  validTo?: string | null
  reason: string
}): Promise<ApiResult<Delegation>> {
  return apiPost('/delegations', {
    idempotencyKey: newIdempotencyKey('delegation-create'),
    body,
  })
}

export function revokeDelegation(
  delegationId: string,
  version: number,
  body: { reason: string },
): Promise<ApiResult<Delegation>> {
  return apiPost(`/delegations/${delegationId}:revoke`, {
    idempotencyKey: newIdempotencyKey('delegation-revoke'),
    ifMatch: quotedVersion(version),
    body,
  })
}

export function explainAuthorization(body: {
  subjectPrincipalId: string
  capability: string
  resourceType: string
  resourceId: string
  projectId?: string | null
  organizationId?: string | null
  regionCode?: string | null
  networkId?: string | null
}) {
  return apiPost<AuthorizationExplainResult>('/authorization:explain', {
    idempotencyKey: newIdempotencyKey('authz-explain'),
    body,
  })
}
