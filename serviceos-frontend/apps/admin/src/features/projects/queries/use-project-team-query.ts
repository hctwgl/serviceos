import { loadProjectTeamWorkspace, matchProjectRegionPersonnel } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export function useProjectTeamQuery(projectId: Ref<string>) {
  return useQuery({
    queryKey: computed(() => ['project-team', projectId.value]),
    queryFn: () => loadProjectTeamWorkspace(projectId.value),
    enabled: computed(() => Boolean(projectId.value)),
  })
}

export function useProjectPersonnelMatchQuery(projectId: Ref<string>, regionCode: Ref<string>) {
  return useQuery({
    queryKey: computed(() => ['project-team-match', projectId.value, regionCode.value]),
    queryFn: () => matchProjectRegionPersonnel(projectId.value, regionCode.value),
    enabled: computed(() => Boolean(projectId.value && regionCode.value)),
  })
}
