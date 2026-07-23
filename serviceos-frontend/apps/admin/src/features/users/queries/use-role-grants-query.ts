import { listRoleGrants, loadAuthorizationRoles } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

/** 指定用户的 RoleGrant 列表属于服务端治理事实，按 principalId 构成缓存键，页面不得自行留存第二份。 */
export function useUserRoleGrantsQuery(principalId: Ref<string | undefined>) {
  return useQuery({
    queryKey: computed(() => ['admin-user-role-grants', principalId.value]),
    queryFn: () => listRoleGrants(principalId.value as string),
    enabled: computed(() => Boolean(principalId.value)),
  })
}

/** 授权表单的角色下拉与 roleId→角色名映射都只使用启用中的角色。 */
export function useActiveRolesQuery() {
  return useQuery({
    queryKey: ['authorization-roles', 'active'],
    queryFn: () => loadAuthorizationRoles(),
    select: (page) => page.items.filter((role) => role.roleStatus === 'ACTIVE'),
  })
}
