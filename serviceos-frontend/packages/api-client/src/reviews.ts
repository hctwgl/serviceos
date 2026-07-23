import { get, newIdempotencyKey, post } from './http'

export type ReviewDecision = {
  reviewDecisionId: string
  reviewCaseId: string
  decisionOrdinal: number
  decision: 'APPROVED' | 'REJECTED' | 'FORCE_APPROVED'
  decisionSource: 'INTERNAL' | 'EXTERNAL'
  reasonCodes: string[]
  note: string | null
  approvalRef: string | null
  decidedBy: string
  decidedAt: string
}

/** 审核案例完整聚合；aggregateVersion 用于 decide 的 If-Match 强 ETag（双引号包裹的正整数）。 */
export type ReviewCase = {
  reviewCaseId: string
  projectId: string
  taskId: string
  reviewTaskId: string | null
  evidenceSetSnapshotId: string
  snapshotContentDigest: string
  scopeType: 'EVIDENCE_SET_SNAPSHOT'
  origin: 'INTERNAL' | 'CLIENT'
  policyVersion: string
  status: 'OPEN' | 'APPROVED' | 'REJECTED' | 'FORCE_APPROVED' | 'REOPENED'
  createdBy: string
  createdAt: string
  decidedAt: string | null
  sourceReviewCaseId: string | null
  externalSubmissionRef: string | null
  callbackBatchRef: string | null
  mappingVersionId: string | null
  reopenedFromReviewCaseId: string | null
  reopenTriggerRef: string | null
  decisions: ReviewDecision[]
  aggregateVersion: number
  derivedOverallDecision: 'APPROVED' | 'REJECTED' | null
  correctionCaseId: string | null
}

export type EvidenceSetSnapshotMember = {
  memberId: string
  evidenceSlotId: string
  evidenceItemId: string
  evidenceRevisionId: string
  revisionNumber: number
  revisionStatus: 'VALIDATED'
  contentDigest: string
  validationDigest: string
  memberOrdinal: number
}

/** 冻结资料集合快照；members 的 evidenceRevisionId/revisionNumber 是审核裁决的 targetDecisions 输入。 */
export type EvidenceSetSnapshot = {
  evidenceSetSnapshotId: string
  taskId: string
  projectId: string
  resolutionId: string
  purpose: 'TASK_SUBMISSION'
  memberCount: number
  contentDigest: string
  eligibilitySummary: Record<string, unknown>
  createdBy: string
  createdAt: string
  members: EvidenceSetSnapshotMember[]
}

/**
 * 审核裁决目标。契约只接受 targetDecisions，不允许客户端提交 overallDecision；
 * 整组结论由服务端按冻结 Snapshot 与 policyVersion 派生。
 */
export type ReviewTargetDecision = {
  targetType: 'EvidenceRevision'
  targetId: string
  targetVersion: number
  decision: 'APPROVED' | 'REJECTED'
  reasonCodes?: string[]
  note?: string | null
}

export type DecideReviewCaseRequest = {
  targetDecisions: ReviewTargetDecision[]
  note?: string | null
}

export type CreateReviewCaseRequest = {
  evidenceSetSnapshotId: string
  policyVersion?: string | null
}

/** 资料版本短时下载授权；downloadUrl 为短时 URL，不得落日志或长期保存。 */
export type DownloadAuthorization = {
  authorizationId: string
  fileId: string
  method: 'GET'
  downloadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}

export function getReviewCase(reviewCaseId: string) {
  return get<ReviewCase>(`/review-cases/${reviewCaseId}`).then((result) => result.data)
}

export function getEvidenceSetSnapshot(snapshotId: string) {
  return get<EvidenceSetSnapshot>(`/evidence-set-snapshots/${snapshotId}`).then(
    (result) => result.data,
  )
}

/** 对 OPEN ReviewCase 追加 APPROVED/REJECTED 决定；If-Match 绑定 aggregateVersion。 */
export function decideReviewCase(
  reviewCaseId: string,
  aggregateVersion: number,
  input: DecideReviewCaseRequest,
) {
  return post<ReviewCase>(`/review-cases/${reviewCaseId}:decide`, input, {
    'Idempotency-Key': newIdempotencyKey('decide-review-case'),
    'If-Match': `"${aggregateVersion}"`,
  }).then((result) => result.data)
}

/** 为 TASK_SUBMISSION 快照创建审核案例（整改重提后发起复审也走此入口）。 */
export function createReviewCase(input: CreateReviewCaseRequest) {
  return post<ReviewCase>('/review-cases', input, {
    'Idempotency-Key': newIdempotencyKey('create-review-case'),
  }).then((result) => result.data)
}

/**
 * 申请资料版本短时下载授权。契约要求请求体 purpose，
 * 工作区资料预览固定使用 WORKSPACE_EVIDENCE_PREVIEW。
 */
export function authorizeEvidenceRevisionDownload(revisionId: string) {
  return post<DownloadAuthorization>(
    `/evidence-revisions/${revisionId}/download-authorizations`,
    { purpose: 'WORKSPACE_EVIDENCE_PREVIEW' },
    { 'Idempotency-Key': newIdempotencyKey('evidence-download-authorization') },
  ).then((result) => result.data)
}
