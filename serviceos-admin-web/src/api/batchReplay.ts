import { apiGet, apiPost, newIdempotencyKey } from './client'

export type BatchReplayItem = {
  deliveryId: string
  projectId: string | null
  eligibility: 'ELIGIBLE' | 'INELIGIBLE'
  ineligibilityCode: string | null
  expectedDeliveryVersion: number | null
  itemStatus: 'PREVIEWED' | 'PENDING' | 'SCHEDULED' | 'SKIPPED' | 'FAILED'
  singleReplayRequestId: string | null
  errorCode: string | null
}

export type BatchReplayRequest = {
  batchId: string
  mode: 'PREVIEW' | 'SUBMIT'
  status: 'PREVIEWED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'COMPLETED'
  reason: string
  approvalRef: string | null
  requestedBy: string
  decidedBy: string | null
  decision: 'APPROVE' | 'REJECT' | null
  decisionNote: string | null
  maxItems: number
  createdAt: string
  decidedAt: string | null
  items: BatchReplayItem[]
}

/** M319/M328：批量 UNKNOWN 重放预演或提交审批。 */
export function createBatchReplayRequest(body: {
  deliveryIds: string[]
  mode: 'PREVIEW' | 'SUBMIT'
  reason: string
  approvalRef?: string | null
  maxItems?: number | null
}) {
  return apiPost<BatchReplayRequest>('/replay-requests', {
    idempotencyKey: newIdempotencyKey('batch-replay-create'),
    body,
  })
}

export function getBatchReplayRequest(batchId: string) {
  return apiGet<BatchReplayRequest>(`/replay-requests/${batchId}`)
}

export function approveBatchReplayRequest(
  batchId: string,
  body: {
    decision: 'APPROVE' | 'REJECT'
    decisionNote?: string | null
    maxItems?: number | null
  },
) {
  return apiPost<BatchReplayRequest>(`/replay-requests/${batchId}:approve`, {
    idempotencyKey: newIdempotencyKey('batch-replay-approve'),
    body,
  })
}
