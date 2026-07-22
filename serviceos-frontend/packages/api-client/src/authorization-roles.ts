import { get } from './http'

export type CapabilityRisk = 'NORMAL' | 'HIGH' | 'CRITICAL'

export type AuthorizationCapability = {
  capabilityCode: string
  capabilityName: string
  riskLevel: CapabilityRisk
}

export type AuthorizationRole = {
  roleId: string
  roleCode: string
  roleName: string
  roleKind: 'PLATFORM_TEMPLATE' | 'TENANT'
  roleStatus: 'ACTIVE' | 'DISABLED'
  description: string | null
  capabilityCodes: string[]
  version: number
  createdAt: string
  updatedAt: string
}

export type AuthorizationRolePage = {
  items: AuthorizationRole[]
  asOf: string
}

export function loadAuthorizationRoles() {
  return get<AuthorizationRolePage>('/roles').then((result) => result.data)
}

export function loadAuthorizationCapabilities() {
  return get<AuthorizationCapability[]>('/capabilities').then((result) => result.data)
}
