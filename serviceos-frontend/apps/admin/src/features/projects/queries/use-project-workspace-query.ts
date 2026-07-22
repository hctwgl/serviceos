import { loadAdminProjectWorkspace } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export function useProjectWorkspaceQuery(projectId: Ref<string>) {
  return useQuery({
    queryKey: computed(() => ['admin-project-workspace', projectId.value]),
    queryFn: () => loadAdminProjectWorkspace(projectId.value),
    enabled: computed(() => Boolean(projectId.value)),
  })
}
