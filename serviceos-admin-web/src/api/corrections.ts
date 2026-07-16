import { apiGet, apiPost, newIdempotencyKey } from './client'

export type CorrectionResubmission = {
  correctionResubmissionId: string
  correctionCaseId: string
  resubmissionOrdinal: number
  evidenceSetSnapshotId: string
  snapshotContentDigest: string
  submittedBy?: string
  submittedAt?: string
}

export type CorrectionCase = {
  correctionCaseId: string
  projectId: string
  taskId: string
  sourceReviewCaseId: string
  sourceReviewDecisionId: string
  sourceEvidenceSetSnapshotId: string
  sourceSnapshotContentDigest: string
  reasonCodes: string[]
  correctionTaskId?: string | null
  status: string
  createdBy: string
  createdAt: string
  latestResubmissionSnapshotId?: string | null
  closedAt?: string | null
  waivedAt?: string | null
  resubmissions: CorrectionResubmission[]
}

export function getCorrectionCase(correctionCaseId: string) {
  return apiGet<CorrectionCase>(`/correction-cases/${correctionCaseId}`)
}

export function resubmitCorrectionCase(correctionCaseId: string, evidenceSetSnapshotId: string) {
  return apiPost<CorrectionCase>(`/correction-cases/${correctionCaseId}:resubmit`, {
    idempotencyKey: newIdempotencyKey('correction-resubmit'),
    body: { evidenceSetSnapshotId },
  })
}

export function closeCorrectionCase(correctionCaseId: string, note?: string) {
  return apiPost<CorrectionCase>(`/correction-cases/${correctionCaseId}:close`, {
    idempotencyKey: newIdempotencyKey('correction-close'),
    body: note ? { note } : {},
  })
}

export function waiveCorrectionCase(
  correctionCaseId: string,
  body: { reason: string; approvalRef: string },
) {
  return apiPost<CorrectionCase>(`/correction-cases/${correctionCaseId}:waive`, {
    idempotencyKey: newIdempotencyKey('correction-waive'),
    body,
  })
}
