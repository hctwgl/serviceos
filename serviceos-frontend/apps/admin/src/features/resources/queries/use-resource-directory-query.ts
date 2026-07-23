import { loadAdminResourceDirectory } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'

export function useResourceDirectoryQuery() {
  return useQuery({ queryKey: ['admin-resource-directory'], queryFn: loadAdminResourceDirectory })
}
