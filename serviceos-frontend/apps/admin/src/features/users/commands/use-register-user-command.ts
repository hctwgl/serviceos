import type { RegisterSecurityPrincipalInput } from '@serviceos/api-client'
import { registerSecurityPrincipal } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useRegisterUserCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RegisterSecurityPrincipalInput) => registerSecurityPrincipal(input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-user-directory'] })
    },
  })
}
