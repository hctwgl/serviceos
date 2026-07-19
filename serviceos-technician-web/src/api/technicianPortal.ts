/** Technician Portal Feed API：networkId 只经 X-Technician-Context。 */
import { apiGet, apiPost, type HttpStatusError } from './client'

export type TechnicianPortalFeedItem = {
  itemType: 'ASSIGNMENT' | 'TOMBSTONE'
  taskId: string
  workOrderId: string | null
  projectId: string | null
  serviceAssignmentId: string | null
  taskAssignmentId: string | null
  taskType: string | null
  taskKind: string | null
  stageCode: string | null
  taskStatus: string | null
  businessType: string | null
  effectiveFrom: string | null
  cursor: string
  invalidationReason: string | null
}

export type TechnicianPortalFeedPage = {
  networkId: string
  items: TechnicianPortalFeedItem[]
  nextCursor: string | null
  asOf: string
}

export type TechnicianPortalScheduleItem = {
  appointmentId: string
  taskId: string
  workOrderId: string
  projectId: string | null
  type: string
  status: string
  windowStart: string | null
  windowEnd: string | null
  timezone: string | null
}

export type TechnicianPortalSchedulePage = {
  networkId: string
  items: TechnicianPortalScheduleItem[]
  asOf: string
}

export type TechnicianPortalSyncSummary = {
  networkId: string
  pendingFeedItemCount: number
  appointmentWindowCount: number
  tombstoneCount: number
  asOf: string
}

export type TechnicianPortalTaskDetail = {
  networkId: string
  taskId: string
  workOrderId: string
  projectId: string | null
  serviceAssignmentId: string | null
  taskAssignmentId: string | null
  taskType: string
  taskKind: string
  stageCode: string
  taskStatus: string
  businessType: string | null
  effectiveFrom: string | null
  executionGuarded: boolean
  resourceVersion: number
  /** M350：SERVICEOS_EXPR_V1 白名单权威非 PII 头 */
  clientCode: string
  brandCode: string
  serviceProductCode: string
  provinceCode: string
  cityCode: string
  districtCode: string
  appointments: TechnicianPortalScheduleItem[]
  contactAttempts: TechnicianPortalContactAttemptItem[]
  visits: TechnicianPortalVisitItem[]
  formSubmissions: TechnicianPortalFormSubmissionItem[] | null
  asOf: string
}

export type TechnicianPortalFormSubmissionItem = {
  submissionId: string
  formVersionId: string
  formKey: string
  submissionVersion: number
  validationStatus: 'VALIDATED' | 'INVALID'
  errorCount: number
  warningCount: number
  submittedAt: string
}

export type TechnicianPortalVisitItem = {
  visitId: string
  taskId: string
  appointmentId: string
  visitSequence: number
  status: 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED'
  checkInCapturedAt: string
  checkInReceivedAt: string
  geofenceResult: 'WITHIN_GEOFENCE' | 'OUTSIDE_GEOFENCE' | 'LOCATION_UNAVAILABLE' | 'LOW_ACCURACY'
  policyDecision: 'ACCEPTED' | 'WARNING'
  checkOutCapturedAt: string | null
  checkOutReceivedAt: string | null
  resultCode: string | null
  exceptionCode: string | null
  aggregateVersion: number
}

export type TechnicianPortalContactAttemptItem = {
  contactAttemptId: string
  taskId: string
  channel: string
  startedAt: string
  endedAt: string
  resultCode: 'CONNECTED' | 'NO_ANSWER' | 'BUSY' | 'WRONG_NUMBER' | 'USER_REQUESTED_LATER' | 'INVALID_CONTACT'
  nextContactAt: string | null
  createdAt: string
}

export type VisitCommandReceipt = {
  visitId: string
  status: 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED'
  aggregateVersion: number
  geofenceResult: 'WITHIN_GEOFENCE' | 'OUTSIDE_GEOFENCE' | 'LOCATION_UNAVAILABLE' | 'LOW_ACCURACY'
  policyDecision: 'ACCEPTED' | 'WARNING'
  occurredAt: string
}

export type TechnicianVisitLocation = {
  latitude: number
  longitude: number
  accuracyMeters: number
}

export type TechnicianTaskFormField = {
  fieldKey: string
  label: string
  dataType: string
  binding: string
  required?: boolean
  requiredWhen?: unknown
  visibleWhen?: unknown
  editableWhen?: unknown
  defaultExpression?: unknown
  optionsRef?: string
  validators?: unknown[]
}

export type TechnicianTaskForm = {
  taskId: string
  formVersionId: string
  formKey: string
  semanticVersion: string
  schemaVersion: string
  definition: {
    title?: string
    sections: Array<{
      sectionKey: string
      title: string
      visibility?: unknown
      fields: TechnicianTaskFormField[]
    }>
    validationRules?: unknown[]
  }
  contentDigest: string
}

export type TechnicianFormSubmission = {
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
  submittedAt: string
}

export type TechnicianEvidenceSlot = {
  slotId: string
  requirementCode: string
  occurrenceKey: string
  requirementName: string
  mediaType: 'PHOTO' | 'VIDEO' | 'DOCUMENT' | 'SIGNATURE' | 'GENERATED_REPORT'
  required: boolean
  minCount: number
  maxCount: number | null
  status: 'MISSING' | 'PARTIAL' | 'SATISFIED' | 'INVALIDATED'
  active: boolean
  transition: string
  requiredDisposition: 'NONE' | 'REVIEW_REQUIRED'
}

export type TechnicianEvidenceRevision = {
  evidenceRevisionId: string
  revisionNumber: number
  contentDigest: string
  mimeType: string
  sizeBytes: number
  status: 'STORED' | 'VALIDATING' | 'VALIDATED' | 'VALIDATION_FAILED' | 'QUARANTINED' | 'INVALIDATED'
  createdAt: string
}

export type TechnicianEvidenceItem = {
  evidenceItemId: string
  taskId: string
  evidenceSlotId: string
  itemOrdinal: number
  status: 'ACTIVE' | 'INVALIDATED'
  createdAt: string
  revisions: TechnicianEvidenceRevision[]
}

export type TechnicianEvidenceUploadSession = {
  uploadSessionId: string
  evidenceSlotId: string
  evidenceItemId: string | null
  status: string
  uploadMethod: 'PUT' | null
  uploadUrl: string | null
  requiredHeaders: Record<string, string>
  uploadAuthorizationExpiresAt: string
  sessionExpiresAt: string
}

export type TechnicianEvidenceSetSnapshot = {
  evidenceSetSnapshotId: string
  taskId: string
  purpose: 'TASK_SUBMISSION'
  memberCount: number
  contentDigest: string
  createdAt: string
  members: Array<{ evidenceSlotId: string; evidenceItemId: string; evidenceRevisionId: string; revisionNumber: number; revisionStatus: 'VALIDATED'; contentDigest: string; memberOrdinal: number }>
}

export type TechnicianTaskCompletionReceipt = {
  taskId: string
  status: 'COMPLETED'
  resourceVersion: number
  occurredAt: string
}

export type TechnicianCorrection = {
  correctionCaseId: string
  sourceTaskId: string
  correctionTaskId: string
  caseStatus: 'IN_PROGRESS' | 'RESUBMITTED'
  reasonCodes: string[]
  taskStatus: 'READY' | 'CLAIMED' | 'RUNNING'
  taskVersion: number
  latestResubmissionSnapshotId: string | null
  resubmissionCount: number
}

function technicianHeaders(technicianContextId: string): Record<string, string> {
  return { 'X-Technician-Context': technicianContextId }
}

export function listTechnicianTaskFeed(technicianContextId: string, sinceCursor?: string) {
  return apiGet<TechnicianPortalFeedPage>(
    '/technician/me/task-feed',
    sinceCursor ? { sinceCursor } : {},
    technicianHeaders(technicianContextId),
  )
}

export function listTechnicianSchedule(technicianContextId: string) {
  return apiGet<TechnicianPortalSchedulePage>(
    '/technician/me/schedule',
    {},
    technicianHeaders(technicianContextId),
  )
}

export function getTechnicianSyncSummary(technicianContextId: string) {
  return apiGet<TechnicianPortalSyncSummary>(
    '/technician/me/sync-summary',
    {},
    technicianHeaders(technicianContextId),
  )
}

export function getTechnicianTaskDetail(technicianContextId: string, taskId: string) {
  return apiGet<TechnicianPortalTaskDetail>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}`,
    {},
    technicianHeaders(technicianContextId),
  )
}

export function listTechnicianTaskForms(technicianContextId: string, taskId: string) {
  return apiGet<TechnicianTaskForm[]>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/forms`,
    {},
    technicianHeaders(technicianContextId),
  )
}

/** 在线提交只产生不可变事实；草稿/prefill 冲突策略未接受，客户端不发送 prefillVersion。 */
export function submitTechnicianTaskForm(
  technicianContextId: string,
  taskId: string,
  formVersionId: string,
  values: Record<string, unknown>,
) {
  return apiPost<TechnicianFormSubmission>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/form-submissions`,
    {
      body: { formVersionId, values },
      idempotencyKey: crypto.randomUUID(),
      headers: technicianHeaders(technicianContextId),
    },
  )
}

export function listTechnicianTaskEvidenceSlots(technicianContextId: string, taskId: string) {
  return apiGet<TechnicianEvidenceSlot[]>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/evidence-slots`,
    {}, technicianHeaders(technicianContextId),
  )
}

export function listTechnicianTaskEvidenceItems(technicianContextId: string, taskId: string) {
  return apiGet<TechnicianEvidenceItem[]>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/evidence-items`,
    {}, technicianHeaders(technicianContextId),
  )
}

export async function sha256Hex(file: Blob): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

export function beginTechnicianEvidenceUpload(
  technicianContextId: string,
  taskId: string,
  slotId: string,
  body: {
    evidenceItemId?: string | null
    originalFileName: string
    declaredMimeType: string
    expectedSize: number
    expectedSha256: string
    captureSource: 'CAMERA' | 'GALLERY' | 'FILE'
    capturedAt: string
  },
) {
  return apiPost<TechnicianEvidenceUploadSession>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/evidence-slots/${encodeURIComponent(slotId)}/upload-sessions`,
    { body, idempotencyKey: crypto.randomUUID(), headers: technicianHeaders(technicianContextId) },
  )
}

/** 数据面只使用服务端返回的短期受限 URL/headers，不携带 JWT 或 Portal Context。 */
export async function putTechnicianEvidenceUpload(
  session: TechnicianEvidenceUploadSession,
  file: Blob,
): Promise<void> {
  if (!session.uploadUrl || session.uploadMethod !== 'PUT') {
    throw new Error('上传会话未返回受限 PUT 地址')
  }
  const response = await fetch(session.uploadUrl, {
    method: 'PUT', headers: session.requiredHeaders, body: file,
  })
  if (!response.ok) throw new Error(`资料上传失败（HTTP ${response.status}）`)
}

export function finalizeTechnicianEvidenceUpload(
  technicianContextId: string,
  taskId: string,
  slotId: string,
  uploadSessionId: string,
  actualSha256: string,
) {
  return apiPost<TechnicianEvidenceItem>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/evidence-slots/${encodeURIComponent(slotId)}`
      + `/upload-sessions/${encodeURIComponent(uploadSessionId)}:finalize`,
    {
      body: { actualSha256, finalizeCommandId: crypto.randomUUID() },
      headers: technicianHeaders(technicianContextId),
    },
  )
}

export function createTechnicianTaskEvidenceSetSnapshot(
  technicianContextId: string, taskId: string, memberRevisionIds: string[],
) {
  return apiPost<TechnicianEvidenceSetSnapshot>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}/evidence-set-snapshots`,
    { body: { memberRevisionIds }, idempotencyKey: crypto.randomUUID(), headers: technicianHeaders(technicianContextId) },
  )
}

export function completeTechnicianTask(
  technicianContextId: string, taskId: string, resourceVersion: number,
  evidenceSetSnapshotId: string, formSubmissionId: string | null,
) {
  return apiPost<TechnicianTaskCompletionReceipt>(
    `/technician/me/tasks/${encodeURIComponent(taskId)}:complete`,
    {
      body: { evidenceSetSnapshotId, ...(formSubmissionId ? { formSubmissionId } : {}) },
      idempotencyKey: crypto.randomUUID(), ifMatch: `"${resourceVersion}"`,
      headers: technicianHeaders(technicianContextId),
    },
  )
}

export function listTechnicianCorrections(technicianContextId: string) {
  return apiGet<TechnicianCorrection[]>(
    '/technician/me/corrections', {}, technicianHeaders(technicianContextId),
  )
}

export function claimTechnicianCorrection(
  technicianContextId: string, correctionCaseId: string, taskVersion: number,
) {
  return apiPost<TechnicianCorrection>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}:claim`,
    { idempotencyKey: crypto.randomUUID(), ifMatch: `"${taskVersion}"`, headers: technicianHeaders(technicianContextId) },
  )
}

export function startTechnicianCorrection(
  technicianContextId: string, correctionCaseId: string, taskVersion: number,
) {
  return apiPost<TechnicianCorrection>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}:start`,
    { idempotencyKey: crypto.randomUUID(), ifMatch: `"${taskVersion}"`, headers: technicianHeaders(technicianContextId) },
  )
}

export function listTechnicianCorrectionEvidenceSlots(
  technicianContextId: string, correctionCaseId: string,
) {
  return apiGet<TechnicianEvidenceSlot[]>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}/evidence-slots`,
    {}, technicianHeaders(technicianContextId),
  )
}

export function listTechnicianCorrectionEvidenceItems(
  technicianContextId: string, correctionCaseId: string,
) {
  return apiGet<TechnicianEvidenceItem[]>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}/evidence-items`,
    {}, technicianHeaders(technicianContextId),
  )
}

export function beginTechnicianCorrectionEvidenceUpload(
  technicianContextId: string,
  correctionCaseId: string,
  slotId: string,
  body: {
    evidenceItemId?: string | null
    originalFileName: string
    declaredMimeType: string
    expectedSize: number
    expectedSha256: string
    captureSource: 'CAMERA' | 'GALLERY' | 'FILE'
    capturedAt: string
  },
) {
  return apiPost<TechnicianEvidenceUploadSession>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}`
      + `/evidence-slots/${encodeURIComponent(slotId)}/upload-sessions`,
    { body, idempotencyKey: crypto.randomUUID(), headers: technicianHeaders(technicianContextId) },
  )
}

export function finalizeTechnicianCorrectionEvidenceUpload(
  technicianContextId: string,
  correctionCaseId: string,
  slotId: string,
  uploadSessionId: string,
  actualSha256: string,
) {
  return apiPost<TechnicianEvidenceItem>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}`
      + `/evidence-slots/${encodeURIComponent(slotId)}/upload-sessions/${encodeURIComponent(uploadSessionId)}:finalize`,
    { body: { actualSha256, finalizeCommandId: crypto.randomUUID() }, headers: technicianHeaders(technicianContextId) },
  )
}

export function createTechnicianCorrectionEvidenceSetSnapshot(
  technicianContextId: string, correctionCaseId: string, memberRevisionIds: string[],
) {
  return apiPost<TechnicianEvidenceSetSnapshot>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}/evidence-set-snapshots`,
    { body: { memberRevisionIds }, idempotencyKey: crypto.randomUUID(), headers: technicianHeaders(technicianContextId) },
  )
}

export function resubmitTechnicianCorrection(
  technicianContextId: string, correctionCaseId: string, evidenceSetSnapshotId: string,
) {
  return apiPost<TechnicianCorrection>(
    `/technician/me/corrections/${encodeURIComponent(correctionCaseId)}:resubmit`,
    { body: { evidenceSetSnapshotId }, idempotencyKey: crypto.randomUUID(), headers: technicianHeaders(technicianContextId) },
  )
}

/**
 * H5 只在用户主动点击后采集一次浏览器位置；服务端端点固定 online，不能由 Web 冒充离线工作包。
 * Idempotency-Key 与 deviceCommandId 保持一致，网络超时后可以安全重放同一请求。
 */
export function checkInTechnicianVisit(
  technicianContextId: string,
  appointmentId: string,
  command: { capturedAt: string; deviceCommandId: string; deviceId: string; location: TechnicianVisitLocation },
) {
  return apiPost<VisitCommandReceipt>(
    `/technician/me/appointments/${encodeURIComponent(appointmentId)}/visits:check-in`,
    {
      body: command,
      idempotencyKey: command.deviceCommandId,
      headers: technicianHeaders(technicianContextId),
    },
  )
}

export function interruptTechnicianVisit(
  technicianContextId: string,
  visitId: string,
  aggregateVersion: number,
  command: { capturedAt: string; exceptionCode: string; note: string | null; evidenceRefs: string[] },
) {
  return apiPost<VisitCommandReceipt>(
    `/technician/me/visits/${encodeURIComponent(visitId)}:interrupt`,
    {
      body: command,
      idempotencyKey: crypto.randomUUID(),
      ifMatch: `"${aggregateVersion}"`,
      headers: technicianHeaders(technicianContextId),
    },
  )
}

export function isPortalContextInvalid(err: unknown): boolean {
  const problem = (err as HttpStatusError | undefined)?.problem
  return (
    (err as HttpStatusError | undefined)?.status === 403 &&
    (problem?.errorCode === 'PORTAL_CONTEXT_INVALID' ||
      problem?.code === 'PORTAL_CONTEXT_INVALID' ||
      problem?.title === 'PORTAL_CONTEXT_INVALID')
  )
}
