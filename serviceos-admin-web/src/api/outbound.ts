import { apiGet, apiPost, newIdempotencyKey } from './client'

export type OutboundDelivery = {
  deliveryId: string
  projectId: string
  businessMessageType: string
  businessKey: string
  sourceReviewCaseId: string
  sourceTaskId: string
  sourceWorkOrderId: string
  externalOrderCode: string
  status: string
  aggregateVersion: number
  createdAt: string
  deliveredAt: string | null
  acknowledgedAt: string | null
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
