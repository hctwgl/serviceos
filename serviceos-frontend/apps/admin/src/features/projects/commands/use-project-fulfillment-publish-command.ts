import { publishProjectFulfillmentRevision } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useProjectFulfillmentPublishCommand(
  projectId: () => string,
  profileId: () => string,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      aggregateVersion: number
      effectiveFrom?: string
      publishNote?: string
    }) => publishProjectFulfillmentRevision(
      projectId(),
      profileId(),
      input.aggregateVersion,
      { effectiveFrom: input.effectiveFrom, publishNote: input.publishNote },
    ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['project-fulfillment-profile', projectId(), profileId()] }),
        queryClient.invalidateQueries({ queryKey: ['project-fulfillment-profiles', projectId()] }),
        queryClient.invalidateQueries({ queryKey: ['project-fulfillment-revisions', projectId(), profileId()] }),
      ])
    },
  })
}
