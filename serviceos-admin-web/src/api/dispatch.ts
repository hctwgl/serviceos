import { apiGet, apiPost, newIdempotencyKey } from './client'

export type ManualAssignNetworkRequest = {
  networkAssigneeId: string
  businessType: string
}

export type ManualAssignNetworkReceipt = {
  taskId: string
  workOrderId: string
  networkServiceAssignmentId: string
  networkAssigneeId: string
  occurredAt: string
}

export type NetworkAssignmentCandidate = {
  networkId: string
  networkName: string
  rank: number
  coverageSummary: string
  remainingCapacity: number
  recommendationSummary: string
}

export type NetworkAssignmentCandidateView = {
  taskId: string
  workOrderId: string
  businessType: string
  generatedAt: string
  rankingExplanation: string
  emptyReason: string | null
  candidates: NetworkAssignmentCandidate[]
}

/** Admin 责任网点候选：项目、区域、业务类型、容量和冻结策略均由服务端权威计算。 */
export function getNetworkAssignmentCandidates(taskId: string) {
  return apiGet<NetworkAssignmentCandidateView>(`/tasks/${taskId}/network-assignment-candidates`)
}

/** Admin 初审派网点：仅激活 ACTIVE NETWORK，不强制师傅。 */
export function manualAssignNetworkServiceAssignment(
  taskId: string,
  body: ManualAssignNetworkRequest,
) {
  return apiPost<ManualAssignNetworkReceipt>(
    `/tasks/${taskId}/service-assignments:manual-assign-network`,
    {
      idempotencyKey: newIdempotencyKey('manual-assign-network'),
      body,
    },
  )
}
