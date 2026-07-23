import { createPartnerOrganization, createServiceNetwork } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useCreatePartnerCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { code: string; name: string }) =>
      createPartnerOrganization(input.code, input.name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-resource-directory'] }),
  })
}

export function useCreateNetworkCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { partnerOrganizationId: string; networkCode: string; networkName: string }) =>
      createServiceNetwork(input.partnerOrganizationId, input.networkCode, input.networkName),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-resource-directory'] }),
  })
}
