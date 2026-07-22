import { get, newIdempotencyKey, post } from './http'

export type PrincipalPersonaType =
  | 'INTERNAL_EMPLOYEE'
  | 'NETWORK_MEMBER'
  | 'TECHNICIAN'
  | 'CONSUMER'
  | 'SERVICE_ACCOUNT'

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

export type AdminUserDirectoryItem = SecurityPrincipal & {
  organizationSummary: string | null
  roleSummary: string | null
  lastLoginAt: string | null
}

export type AdminUserDirectoryPage = {
  items: AdminUserDirectoryItem[]
  nextCursor: string | null
  asOf: string
}

export type RegisterSecurityPrincipalInput = {
  displayName: string
  employeeNumber?: string | null
  personaType?: PrincipalPersonaType | null
}

export function loadAdminUserDirectory(query: Record<string, string | number | undefined>) {
  return get<AdminUserDirectoryPage>('/admin/user-directory', query).then((result) => result.data)
}

export function registerSecurityPrincipal(input: RegisterSecurityPrincipalInput) {
  return post<SecurityPrincipal>('/security-principals', input, {
    'Idempotency-Key': newIdempotencyKey('principal-register'),
  }).then((result) => result.data)
}
