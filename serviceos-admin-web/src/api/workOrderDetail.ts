import { apiGet } from './client'
import type { WorkOrder } from './workOrders'

export type WorkOrderDetail = {
  workOrder: WorkOrder
  asOf: string
}

export type WorkflowInstance = {
  id: string
  projectId: string
  workOrderId: string
  workflowKey: string
  workflowVersion: string
  status: string
  version: number
  startedAt: string
  completedAt: string | null
}

export type StageInstance = {
  id: string
  workflowInstanceId: string
  workOrderId: string
  stageCode: string
  sequenceNo: number
  status: string
  version: number
  activatedAt: string
  completedAt: string | null
}

export type WorkflowExecutionProjection = {
  workflow: WorkflowInstance | null
  stages: StageInstance[]
  asOf: string
}

export type WorkOrderTaskSummary = {
  id: string
  projectId: string | null
  workOrderId: string
  taskType: string
  taskKind: string
  priority: string
  status: string
  stageCode: string | null
  nextRunAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export type WorkOrderTaskPage = {
  items: WorkOrderTaskSummary[]
  nextCursor: string | null
  asOf: string
}

export type WorkOrderTimelineItem = {
  id: string
  category: string
  eventType: string
  occurredAt: string
  actorId: string
  resourceType: string
  resourceId: string
  resourceCode?: string | null
  outcomeCode: string | null
  correlationId: string
}

export type WorkOrderTimelinePage = {
  resourceVersion: number
  items: WorkOrderTimelineItem[]
  nextCursor: string | null
  asOf: string
  lastProjectedAt: string | null
  freshnessStatus: 'FRESH' | 'LAGGING' | 'UNKNOWN' | 'REBUILDING'
}

export function getAuthorizedWorkOrder(workOrderId: string) {
  return apiGet<WorkOrderDetail>(`/work-orders/${workOrderId}`)
}

export function getAuthorizedWorkOrderStages(workOrderId: string) {
  return apiGet<WorkflowExecutionProjection>(`/work-orders/${workOrderId}/stages`)
}

export function listAuthorizedWorkOrderTasks(
  workOrderId: string,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<WorkOrderTaskPage>(`/work-orders/${workOrderId}/tasks`, query)
}

export function listWorkOrderCoreTimeline(
  workOrderId: string,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<WorkOrderTimelinePage>(`/work-orders/${workOrderId}/timeline`, query)
}
