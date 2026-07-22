import { loadAuthorizationCapabilities, loadAuthorizationRoles } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'

export function useRoleDirectoryQuery() {
  return useQuery({ queryKey: ['authorization-roles'], queryFn: loadAuthorizationRoles })
}

export function useCapabilityDirectoryQuery() {
  return useQuery({ queryKey: ['authorization-capabilities'], queryFn: loadAuthorizationCapabilities })
}
