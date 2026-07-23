import type { AssignProjectRegionPersonnelInput } from '@serviceos/api-client'
import { addProjectTeamMember, assignProjectRegionPersonnel } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useAddProjectTeamMemberCommand(projectId: () => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (principalId: string) => addProjectTeamMember(projectId(), principalId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['project-team', projectId()] })
    },
  })
}

export function useAssignProjectRegionPersonnelCommand(projectId: () => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AssignProjectRegionPersonnelInput) =>
      assignProjectRegionPersonnel(projectId(), input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['project-team', projectId()] })
      await queryClient.invalidateQueries({ queryKey: ['project-team-match', projectId()] })
    },
  })
}
