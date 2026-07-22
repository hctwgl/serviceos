import { loadAdminClientProjectDirectory } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'

/** 客户和项目共用一个页面投影，避免两个页面分别重建客户、品牌和项目关系。 */
export function useClientProjectDirectoryQuery() {
  return useQuery({
    queryKey: ['admin-client-project-directory'],
    queryFn: loadAdminClientProjectDirectory,
  })
}
