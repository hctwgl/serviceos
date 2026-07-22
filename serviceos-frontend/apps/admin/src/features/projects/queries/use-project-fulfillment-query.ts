import {
  loadProjectFulfillmentProfile,
  loadProjectFulfillmentProfiles,
} from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export function useProjectFulfillmentProfilesQuery(projectId: Ref<string>) {
  return useQuery({
    queryKey: computed(() => ['project-fulfillment-profiles', projectId.value]),
    queryFn: () => loadProjectFulfillmentProfiles(projectId.value),
    enabled: computed(() => Boolean(projectId.value)),
  })
}

export function useProjectFulfillmentProfileQuery(
  projectId: Ref<string>,
  profileId: Ref<string | undefined>,
) {
  return useQuery({
    queryKey: computed(() => ['project-fulfillment-profile', projectId.value, profileId.value]),
    queryFn: () => loadProjectFulfillmentProfile(projectId.value, profileId.value!),
    enabled: computed(() => Boolean(projectId.value && profileId.value)),
  })
}
