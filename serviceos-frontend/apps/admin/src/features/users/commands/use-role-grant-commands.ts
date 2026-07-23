import type { RequestRoleGrantInput, RoleGrant } from '@serviceos/api-client'
import { ApiProblem, approveRoleGrant, requestRoleGrant, revokeRoleGrant } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export type GrantRoleInput = Omit<RequestRoleGrantInput, 'principalId' | 'validFrom'>

function invalidateRoleGrantQueries(queryClient: ReturnType<typeof useQueryClient>, principalId: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin-user-role-grants', principalId] }),
    queryClient.invalidateQueries({ queryKey: ['admin-user-directory'] }),
  ])
}

/**
 * “授予角色”在 UI 上是一个动作，但治理流程要求先申请（PENDING_APPROVAL）再审批（ACTIVE）。
 * 审批失败时保留申请事实并给出中文上下文，由调用方刷新列表展示待审批记录。
 */
export function useGrantRoleCommand(principalId: () => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (input: GrantRoleInput): Promise<RoleGrant> => {
      const requested = await requestRoleGrant({
        ...input,
        principalId: principalId(),
        validFrom: new Date().toISOString(),
      })
      try {
        return await approveRoleGrant(requested.grantId, requested.version)
      } catch (error) {
        if (error instanceof ApiProblem) {
          throw new ApiProblem(
            `授权申请已提交，但审批未通过：${error.message}`,
            error.status,
            error.problem,
            error.correlationId,
          )
        }
        throw error
      }
    },
    onSettled: async () => {
      await invalidateRoleGrantQueries(queryClient, principalId())
    },
  })
}

export function useRevokeRoleGrantCommand(principalId: () => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { grantId: string; version: number; reason: string }) =>
      revokeRoleGrant(input.grantId, input.version, input.reason),
    onSuccess: async () => {
      await invalidateRoleGrantQueries(queryClient, principalId())
    },
  })
}
