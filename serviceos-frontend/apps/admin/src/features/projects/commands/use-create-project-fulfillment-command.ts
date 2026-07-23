import type { CreateProjectFulfillmentProfileInput } from '@serviceos/api-client'
import { createProjectFulfillmentProfile } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useCreateProjectFulfillmentCommand(projectId: () => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateProjectFulfillmentProfileInput) =>
      createProjectFulfillmentProfile(projectId(), input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['project-fulfillment-profiles', projectId()],
      })
      await queryClient.invalidateQueries({
        queryKey: ['admin-project-workspace', projectId()],
      })
    },
  })
}
