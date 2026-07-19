import { apiGet, apiPost, newIdempotencyKey } from './client'

export type OutboundDelivery = {
  deliveryId: string
  projectId: string
  businessMessageType: string
  businessKey: string
  sourceReviewCaseId: string
  sourceTaskId: string
  sourceWorkOrderId: string
  sourceSnapshotId: string
  sourceSnapshotDigest: string
  externalOrderCode: string
  /** OpenAPI nullable；队列侧已深链，详情类型对齐。 */
  executionTaskId: string | null
  status: string
  aggregateVersion: number
  createdAt: string
  deliveredAt: string | null
  acknowledgedAt: string | null
  clientReviewCaseId: string | null
  reviewRouteId: string | null
  attempts: Array<Record<string, unknown>>
  acknowledgements: Array<Record<string, unknown>>
  replayRequests: Array<Record<string, unknown>>
}

export type DeliveryReplayRequest = {
  replayRequestId: string
  deliveryId: string
  executionTaskId: string
  status: string
  reason: string
  approvalRef: string
  requestedBy: string
  requestedAt: string
}

export function getOutboundDelivery(deliveryId: string) {
  return apiGet<OutboundDelivery>(`/outbound-deliveries/${deliveryId}`)
}

export function retryUnknownOutboundDelivery(
  deliveryId: string,
  body: { expectedAggregateVersion: number; reason: string; approvalRef: string },
) {
  return apiPost<DeliveryReplayRequest>(`/outbound-deliveries/${deliveryId}:retry`, {
    idempotencyKey: newIdempotencyKey('outbound-retry'),
    body,
  })
}

export type ManualOutboundDisposition = {
  dispositionId: string
  deliveryId: string
  result: 'MANUAL_CONFIRMED' | 'ABANDONED'
  reason: string
  approvalRef: string
  externalRef: string | null
  evidenceRefs: string[]
  requestedBy: string
  requestedAt: string
  deliveryAggregateVersion: number
}

/** M318/M328：UNKNOWN Delivery 人工确认或放弃；状态保持 UNKNOWN。 */
export function recordManualOutboundAck(
  deliveryId: string,
  body: {
    expectedAggregateVersion: number
    result: 'MANUAL_CONFIRMED' | 'ABANDONED'
    reason: string
    approvalRef: string
    externalRef?: string | null
    evidenceRefs?: string[]
  },
) {
  return apiPost<ManualOutboundDisposition>(
    `/outbound-deliveries/${deliveryId}:record-manual-ack`,
    {
      idempotencyKey: newIdempotencyKey('outbound-manual-ack'),
      body,
    },
  )
}
