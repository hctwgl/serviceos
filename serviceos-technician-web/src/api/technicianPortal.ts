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
