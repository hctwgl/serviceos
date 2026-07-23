import { get, newIdempotencyKey, post } from './http'

export type RoleGrantScopeType = 'TENANT' | 'PROJECT' | 'REGION' | 'NETWORK'

export type RoleGrantStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'REVOKED'

export type RoleGrantEffect = 'ALLOW' | 'DENY'

export type RoleGrantDecision = 'APPROVE' | 'REJECT'

/** 与 serviceos-core-v1.yaml 的 RoleGrant schema 逐字段对齐，审批/回收留痕由服务端维护。 */
export type RoleGrant = {
  grantId: string
  principalId: string
  roleId: string
  roleCode: string
  scopeType: RoleGrantScopeType
  scopeRef: string
  grantStatus: RoleGrantStatus
  grantEffect: RoleGrantEffect
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

export type RoleGrantPage = {
  items: RoleGrant[]
  asOf: string
}

export type RequestRoleGrantInput = {
  principalId: string
  roleId: string
  scopeType: RoleGrantScopeType
  scopeRef: string
  grantEffect?: RoleGrantEffect | null
  validFrom: string
  validTo?: string | null
  requestReason: string
}

export function listRoleGrants(principalId: string, grantStatus?: RoleGrantStatus) {
  return get<RoleGrantPage>('/role-grants', { principalId, grantStatus }).then(
    (result) => result.data,
  )
}

export function requestRoleGrant(input: RequestRoleGrantInput) {
  return post<RoleGrant>('/role-grants', input, {
    'Idempotency-Key': newIdempotencyKey('role-grant-request'),
  }).then((result) => result.data)
}

/**
 * 批准或拒绝授权申请。If-Match 必须携带双引号包裹的聚合版本（契约 IfMatch 参数），
 * 版本通常取申请响应 body 的 version 字段。
 */
export function approveRoleGrant(
  grantId: string,
  version: number,
  decision: RoleGrantDecision = 'APPROVE',
  note?: string,
) {
  return post<RoleGrant>(
    `/role-grants/${grantId}:approve`,
    { decision, note: note ?? null },
    {
      'Idempotency-Key': newIdempotencyKey('role-grant-decide'),
      'If-Match': `"${version}"`,
    },
  ).then((result) => result.data)
}

export function revokeRoleGrant(grantId: string, version: number, reason: string) {
  return post<RoleGrant>(
    `/role-grants/${grantId}:revoke`,
    { reason },
    {
      'Idempotency-Key': newIdempotencyKey('role-grant-revoke'),
      'If-Match': `"${version}"`,
    },
  ).then((result) => result.data)
}
