import { apiGet, apiPost, newIdempotencyKey } from './client'

export type TaskForm = {
  taskId: string
  formVersionId: string
  formKey: string
  semanticVersion: string
  schemaVersion: string
  definition: Record<string, unknown>
  contentDigest: string
}

export type FormSubmission = {
  submissionId: string
  taskId: string
  projectId: string
  formVersionId: string
  formKey: string
  submissionVersion: number
  values: Record<string, unknown>
  contentDigest: string
  validationStatus: 'VALIDATED' | 'INVALID'
  errors: Array<{ fieldKey: string; code: string; message: string }>
  warnings: Array<{ fieldKey: string; code: string; message: string }>
  prefillVersion?: string | null
  submittedBy: string
  submittedAt: string
}

export type EvidenceSlot = {
  slotId: string
  taskId: string
  projectId: string
  requirementCode: string
  requirementName: string
  mediaType: string
  required: boolean
  status: string
  active: boolean
  requiredDisposition: string | null
  resolutionId: string
  currentResolutionId?: string
}

export type EvidenceUploadSession = {
  uploadSessionId: string
  fileId: string
  taskId: string
  evidenceSlotId: string
  evidenceItemId: string | null
  status: string
  uploadMethod: 'PUT' | null
  uploadUrl: string | null
  requiredHeaders: Record<string, string>
  uploadAuthorizationExpiresAt: string
  sessionExpiresAt: string
}

export type EvidenceConditionDisposition = {
  dispositionId: string
  taskId: string
  slotId: string
  resolutionId: string
  decision: 'KEEP' | 'INVALIDATE'
  reasonCode: string
  reviewRef: string
  decidedBy: string
  decidedAt: string
}

export type EvidenceRevision = {
  evidenceRevisionId: string
  revisionNumber: number
  status: string
  contentDigest: string
  fileObjectId?: string
}

export type DownloadAuthorization = {
  authorizationId: string
  fileId: string
  method: 'GET'
  downloadUrl: string
  requiredHeaders: Record<string, string>
  expiresAt: string
}

export type EvidenceItem = {
  evidenceItemId: string
  taskId: string
  projectId: string
  evidenceSlotId: string
  itemOrdinal: number
  status: string
  createdBy?: string
  createdAt?: string
  revisions: EvidenceRevision[]
}

export type EvidenceSetSnapshotMember = {
  memberId: string
  evidenceSlotId: string
  evidenceItemId: string
  evidenceRevisionId: string
  revisionNumber: number
  revisionStatus: string
  contentDigest: string
  validationDigest?: string
  memberOrdinal: number
}

export type EvidenceSetSnapshot = {
  evidenceSetSnapshotId: string
  taskId: string
  projectId: string
  resolutionId?: string
  purpose: 'TASK_SUBMISSION'
  contentDigest: string
  memberCount: number
  eligibilitySummary?: Record<string, unknown>
  createdBy?: string
  createdAt: string
  members?: EvidenceSetSnapshotMember[]
}

export type StoredFile = {
  fileId: string
  tenantId: string
  originalFileName: string
  checksumSha256: string
  size: number
  declaredMimeType: string
  detectedMimeType: string
  lifecycleStatus: 'QUARANTINED' | 'AVAILABLE' | 'INVALIDATED'
  quarantineReason: string | null
  createdAt: string
  version: number
}

export function listTaskForms(taskId: string) {
  return apiGet<TaskForm[]>(`/tasks/${taskId}/forms`)
}

export function getFormSubmission(submissionId: string) {
  return apiGet<FormSubmission>(`/form-submissions/${submissionId}`)
}

export function getEvidenceItem(itemId: string) {
  return apiGet<EvidenceItem>(`/evidence-items/${itemId}`)
}

export function getEvidenceSetSnapshot(snapshotId: string) {
  return apiGet<EvidenceSetSnapshot>(`/evidence-set-snapshots/${snapshotId}`)
}

export function submitTaskForm(
  taskId: string,
  body: { formVersionId: string; values: Record<string, unknown>; prefillVersion?: string | null },
) {
  return apiPost<FormSubmission>(`/tasks/${taskId}/form-submissions`, {
    idempotencyKey: newIdempotencyKey('form-submit'),
    body,
  })
}

export function listTaskEvidenceSlots(taskId: string) {
  return apiGet<EvidenceSlot[]>(`/tasks/${taskId}/evidence-slots`)
}

export function listTaskEvidenceItems(taskId: string) {
  return apiGet<EvidenceItem[]>(`/tasks/${taskId}/evidence-items`)
}

export function createEvidenceSetSnapshot(taskId: string, memberRevisionIds: string[]) {
  return apiPost<EvidenceSetSnapshot>(`/tasks/${taskId}/evidence-set-snapshots`, {
    idempotencyKey: newIdempotencyKey('evidence-snapshot'),
    body: { purpose: 'TASK_SUBMISSION', memberRevisionIds },
  })
}

export async function sha256Hex(file: Blob): Promise<string> {
  const buffer = await file.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export function beginEvidenceUpload(
  taskId: string,
  slotId: string,
  body: {
    originalFileName: string
    declaredMimeType: string
    expectedSize: number
    expectedSha256: string
    captureMetadata: { capturedAt: string; captureSource?: string; source?: string }
    evidenceItemId?: string | null
  },
) {
  return apiPost<EvidenceUploadSession>(
    `/tasks/${taskId}/evidence-slots/${slotId}/upload-sessions`,
    {
      idempotencyKey: newIdempotencyKey('evidence-begin'),
      body,
    },
  )
}

export async function putAuthorizedUpload(
  session: EvidenceUploadSession,
  file: Blob,
): Promise<void> {
  if (!session.uploadUrl || session.uploadMethod !== 'PUT') {
    throw new Error('上传会话未返回 PUT uploadUrl')
  }
  const response = await fetch(session.uploadUrl, {
    method: 'PUT',
    headers: session.requiredHeaders,
    body: file,
  })
  if (!response.ok) {
    throw new Error(`对象上传失败: HTTP ${response.status}`)
  }
}

export function finalizeEvidenceUpload(
  taskId: string,
  slotId: string,
  uploadSessionId: string,
  body: { actualSha256: string; finalizeCommandId: string },
) {
  return apiPost<EvidenceItem>(
    `/tasks/${taskId}/evidence-slots/${slotId}/upload-sessions/${uploadSessionId}:finalize`,
    { body },
  )
}

export function resolveEvidenceConditionChange(
  taskId: string,
  slotId: string,
  body: {
    expectedResolutionId: string
    decision: 'KEEP' | 'INVALIDATE'
    reasonCode: string
    reviewRef: string
  },
) {
  return apiPost<EvidenceConditionDisposition>(
    `/tasks/${taskId}/evidence-slots/${slotId}:resolve-condition-change`,
    {
      idempotencyKey: newIdempotencyKey('evidence-disposition'),
      body,
    },
  )
}

export function authorizeFileDownload(fileId: string, purpose: string) {
  return apiPost<DownloadAuthorization>(`/files/${fileId}/download-authorizations`, {
    body: { purpose },
  })
}

export function invalidateEvidenceRevision(
  revisionId: string,
  body: { reasonCode: string; approvalRef?: string | null },
) {
  return apiPost<EvidenceRevision>(`/evidence-revisions/${revisionId}:invalidate`, {
    idempotencyKey: newIdempotencyKey('evidence-invalidate'),
    body,
  })
}

export function invalidateStoredFile(
  fileId: string,
  body: { reasonCode: string; sourceType: string; sourceId: string },
) {
  return apiPost<StoredFile>(`/files/${fileId}:invalidate`, {
    idempotencyKey: newIdempotencyKey('file-invalidate'),
    body,
  })
}
