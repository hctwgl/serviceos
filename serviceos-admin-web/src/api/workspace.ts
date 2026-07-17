import { apiGet } from './client'

export type SectionCode =
  | 'TASKS'
  | 'TIMELINE_AUDIT'
  | 'APPOINTMENTS_VISITS'
  | 'FORMS_EVIDENCE'
  | 'REVIEWS_CORRECTIONS'
  | 'INTEGRATION'

export type WorkOrderWorkspace = {
  header: {
    id: string
    projectId: string
    status: string
    externalOrderCode: string
    clientCode: string
    receivedAt: string
  }
  currentTaskSummary: {
    taskId: string
    taskType?: string
    status: string
    stageCode?: string | null
  } | null
  sectionAvailability: Record<string, 'AVAILABLE' | 'EMPTY' | 'UNAVAILABLE'>
  allowedActionLink: string | null
  serviceAssignmentSummary: Record<string, unknown> | null
  slaSummary: Record<string, unknown> | null
  exceptionSummary: Record<string, unknown> | null
  timelineFreshnessStatus: 'FRESH' | 'LAGGING' | 'UNKNOWN' | 'REBUILDING'
  meta: {
    asOf: string
    freshnessStatus?: string
    queryId?: string
  }
}

export type WorkOrderWorkspaceSection = {
  section: SectionCode
  meta: { asOf: string; freshnessStatus?: string; queryId?: string; nextCursor?: string | null }
  tasks?: { items: Array<Record<string, unknown>>; nextCursor: string | null } | null
  timeline?: { items: Array<Record<string, unknown>>; nextCursor: string | null; freshnessStatus?: string } | null
  appointmentsVisits?: Record<string, unknown> | null
  formsEvidence?: Record<string, unknown> | null
  reviewsCorrections?: Record<string, unknown> | null
  integration?: Record<string, unknown> | null
}

export type WorkOrderActivitySummary = {
  resourceVersion: number
  items: Array<{
    eventType?: string
    type?: string
    occurredAt?: string
    resourceType?: string
    resourceId?: string
    resourceCode?: string | null
  }>
  lastProjectedAt?: string | null
  meta: { asOf: string; freshnessStatus?: string; queryId?: string }
}

export function getWorkOrderWorkspace(workOrderId: string) {
  return apiGet<WorkOrderWorkspace>(`/work-orders/${workOrderId}/workspace`)
}

export function getWorkOrderWorkspaceSection(
  workOrderId: string,
  section: SectionCode,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<WorkOrderWorkspaceSection>(
    `/work-orders/${workOrderId}/workspace/sections/${section}`,
    query,
  )
}

export function getWorkOrderActivitySummary(workOrderId: string, limit = '5') {
  return apiGet<WorkOrderActivitySummary>(`/work-orders/${workOrderId}/activity-summary`, { limit })
}
