import { loadAdminUserDirectory } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export type UserDirectoryFilters = {
  query?: string
  status?: string
  cursor?: string
}

/** 用户目录属于服务端状态，筛选和游标共同构成缓存键，页面不得自行缓存第二份用户事实。 */
export function useUserDirectoryQuery(filters: Ref<UserDirectoryFilters>) {
  return useQuery({
    queryKey: computed(() => ['admin-user-directory', filters.value]),
    queryFn: () => loadAdminUserDirectory({ ...filters.value, limit: 20 }),
  })
}
