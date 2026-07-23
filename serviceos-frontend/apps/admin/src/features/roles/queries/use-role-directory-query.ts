import { loadAuthorizationCapabilities, loadAuthorizationRoles } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'

async function loadRoleDirectoryView() {
  const [roles, capabilityCatalog] = await Promise.all([
    loadAuthorizationRoles(),
    loadAuthorizationCapabilities(),
  ])
  const catalog = new Map(capabilityCatalog.map((item) => [item.capabilityCode, item]))
  return {
    asOf: roles.asOf,
    items: roles.items.map((role) => ({
      id: role.roleId,
      code: role.roleCode,
      name: role.roleName,
      kind: role.roleKind,
      status: role.roleStatus,
      description: role.description,
      updatedAt: role.updatedAt,
      permissions: role.capabilityCodes.map((code) => ({
        code,
        name: catalog.get(code)?.capabilityName ?? '权限名称缺失',
        risk: catalog.get(code)?.riskLevel ?? 'NORMAL',
      })),
    })),
  }
}

export function useRoleDirectoryQuery() {
  return useQuery({ queryKey: ['authorization-role-directory'], queryFn: loadRoleDirectoryView })
}
