import type { CreateProjectInput } from '@serviceos/api-client'
import { createProject } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useCreateProjectCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateProjectInput) => createProject(input),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-client-project-directory'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-project-creation-options'] }),
      ])
    },
  })
}
