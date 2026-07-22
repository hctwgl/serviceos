import { loadAdminProjectCreationOptions } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import type { Ref } from 'vue'

export function useProjectCreationOptionsQuery(enabled: Ref<boolean>, regionQuery: Ref<string>) {
  return useQuery({
    queryKey: computed(() => ['admin-project-creation-options', regionQuery.value.trim()]),
    queryFn: () => loadAdminProjectCreationOptions(regionQuery.value),
    enabled,
  })
}
