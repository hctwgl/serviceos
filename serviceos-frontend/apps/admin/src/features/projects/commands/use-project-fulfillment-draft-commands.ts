import type { ProjectFulfillmentDocument } from '@serviceos/api-client'
import {
  updateProjectFulfillmentDraft,
  validateProjectFulfillmentDraft,
} from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useUpdateProjectFulfillmentDraftCommand(
  projectId: () => string,
  profileId: () => string,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      aggregateVersion: number
      description?: string
      document: ProjectFulfillmentDocument
      profileName: string
      sourceBundleId: string | null
      workflowAssetVersionId: string | null
    }) => updateProjectFulfillmentDraft(
      projectId(),
      profileId(),
      input.aggregateVersion,
      input,
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['project-fulfillment-draft', projectId(), profileId()],
      })
      await queryClient.invalidateQueries({
        queryKey: ['project-fulfillment-profile', projectId(), profileId()],
      })
      await queryClient.invalidateQueries({
        queryKey: ['project-fulfillment-profiles', projectId()],
      })
    },
  })
}

export function useValidateProjectFulfillmentDraftCommand(
  projectId: () => string,
  profileId: () => string,
) {
  return useMutation({
    mutationFn: () => validateProjectFulfillmentDraft(projectId(), profileId()),
  })
}
