/** Technician Portal Feed API：networkId 只经 X-Technician-Context。 */
import { apiGet, type HttpStatusError } from './client'

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
  asOf: string
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

export function isPortalContextInvalid(err: unknown): boolean {
  const problem = (err as HttpStatusError | undefined)?.problem
  return (
    (err as HttpStatusError | undefined)?.status === 403 &&
    (problem?.errorCode === 'PORTAL_CONTEXT_INVALID' ||
      problem?.code === 'PORTAL_CONTEXT_INVALID' ||
      problem?.title === 'PORTAL_CONTEXT_INVALID')
  )
}
