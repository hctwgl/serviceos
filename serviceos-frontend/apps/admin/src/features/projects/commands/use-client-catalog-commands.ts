import { createProjectClient, createProjectClientBrand } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export function useCreateClientCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { clientCode: string; displayName: string }) =>
      createProjectClient(input.clientCode, input.displayName),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-client-project-directory'] }),
  })
}

export function useCreateBrandCommand() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { clientCode: string; brandCode: string; displayName: string; sortOrder: number }) =>
      createProjectClientBrand(input.clientCode, input.brandCode, input.displayName, input.sortOrder),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-client-project-directory'] }),
  })
}
