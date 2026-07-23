import {
  compareProjectFulfillmentImpact,
  compileProjectFulfillmentPreview,
  loadProjectFulfillmentRevisions,
  validateProjectFulfillmentDraft,
} from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export function useProjectFulfillmentPublishQuery(
  projectId: Ref<string>,
  profileId: Ref<string>,
) {
  const enabled = computed(() => Boolean(projectId.value && profileId.value))
  const identity = computed(() => [projectId.value, profileId.value])

  const validation = useQuery({
    queryKey: computed(() => ['project-fulfillment-publish-validation', ...identity.value]),
    queryFn: () => validateProjectFulfillmentDraft(projectId.value, profileId.value),
    enabled,
    retry: false,
  })
  const preview = useQuery({
    queryKey: computed(() => ['project-fulfillment-publish-preview', ...identity.value]),
    queryFn: () => compileProjectFulfillmentPreview(projectId.value, profileId.value),
    enabled,
    retry: false,
  })
  const impact = useQuery({
    queryKey: computed(() => ['project-fulfillment-publish-impact', ...identity.value]),
    queryFn: () => compareProjectFulfillmentImpact(projectId.value, profileId.value),
    enabled,
    retry: false,
  })
  const revisions = useQuery({
    queryKey: computed(() => ['project-fulfillment-revisions', ...identity.value]),
    queryFn: () => loadProjectFulfillmentRevisions(projectId.value, profileId.value),
    enabled,
  })

  async function refreshPreparation() {
    await Promise.all([validation.refetch(), preview.refetch(), impact.refetch()])
  }

  return { impact, preview, refreshPreparation, revisions, validation }
}
