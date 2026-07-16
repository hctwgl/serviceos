import { apiGet, apiPost, newIdempotencyKey } from './client'

export type ReviewDecision = {
  reviewDecisionId: string
  reviewCaseId: string
  decisionOrdinal: number
  decision: string
  reasonCodes: string[]
  decidedBy: string
  decidedAt: string
  note?: string | null
}

export type ReviewCase = {
  reviewCaseId: string
  projectId: string
  taskId: string
  evidenceSetSnapshotId: string
  snapshotContentDigest: string
  origin: string
  policyVersion: string
  status: string
  createdBy: string
  createdAt: string
  decidedAt: string | null
  decisions: ReviewDecision[]
}

export function getReviewCase(reviewCaseId: string) {
  return apiGet<ReviewCase>(`/review-cases/${reviewCaseId}`)
}

export function createReviewCase(
  body: { evidenceSetSnapshotId: string; policyVersion?: string | null },
) {
  return apiPost<ReviewCase>('/review-cases', {
    idempotencyKey: newIdempotencyKey('review-create'),
    body,
  })
}

export function decideReviewCase(
  reviewCaseId: string,
  body: { decision: 'APPROVED' | 'REJECTED'; reasonCodes?: string[]; note?: string | null },
) {
  return apiPost<ReviewCase>(`/review-cases/${reviewCaseId}:decide`, {
    idempotencyKey: newIdempotencyKey('review-decide'),
    body,
  })
}

export function forceApproveReviewCase(
  reviewCaseId: string,
  body: { reasonCodes: string[]; approvalRef: string; note?: string | null },
) {
  return apiPost<ReviewCase>(`/review-cases/${reviewCaseId}:force-approve`, {
    idempotencyKey: newIdempotencyKey('review-force'),
    body,
  })
}

export function reopenReviewCase(
  reviewCaseId: string,
  body: { reason: string; triggerRef: string; approvalRef?: string | null },
) {
  return apiPost<ReviewCase>(`/review-cases/${reviewCaseId}:reopen`, {
    idempotencyKey: newIdempotencyKey('review-reopen'),
    body,
  })
}
