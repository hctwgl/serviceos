import { apiGet } from './client'

export type ReviewCaseQueuePage = {
  items: Array<{
    reviewCaseId: string
    projectId: string
    taskId: string
    status: string
    origin: string
    createdAt: string
    latestDecision: string | null
    latestReasonCodes: string[]
  }>
  nextCursor: string | null
  asOf: string
}

export type CorrectionCaseQueuePage = {
  items: Array<{
    correctionCaseId: string
    projectId: string
    taskId: string
    sourceReviewCaseId: string
    correctionTaskId: string | null
    status: string
    reasonCodes: string[]
    createdAt: string
    resubmissionCount: number
  }>
  nextCursor: string | null
  asOf: string
}

export type OutboundDeliveryQueuePage = {
  items: Array<{
    deliveryId: string
    projectId: string
    sourceWorkOrderId: string
    status: string
    businessMessageType: string
    externalOrderCode: string
    createdAt: string
    attemptCount: number
  }>
  nextCursor: string | null
  asOf: string
}

export type OperationalExceptionPage = {
  items: Array<{
    exceptionId: string
    projectId: string | null
    workOrderId: string | null
    category: string
    severity: string
    status: string
    errorCode: string
    openedAt: string
    aggregateVersion: number
    allowedActions: Array<'ACKNOWLEDGE'>
  }>
  nextCursor: string | null
}

/** API-06 §6 审核队列筛选；status 省略时服务端默认 OPEN。 */
export type ReviewCaseQueueQuery = {
  projectId?: string
  status?: string
  origin?: string
  taskId?: string
  cursor?: string
  limit?: string
}

export function listReviewCases(params: ReviewCaseQueueQuery = {}) {
  return apiGet<ReviewCaseQueuePage>('/review-cases', params)
}

/**
 * API-06 §6 整改队列筛选；status 省略时服务端默认 OPEN。
 * Admin 运营默认仍显式传 IN_PROGRESS（与既有冒烟/客户端一致）。
 */
export type CorrectionCaseQueueQuery = {
  projectId?: string
  status?: string
  taskId?: string
  sourceReviewCaseId?: string
  cursor?: string
  limit?: string
}

export function listCorrectionCases(params: CorrectionCaseQueueQuery = {}) {
  return apiGet<CorrectionCaseQueuePage>('/correction-cases', {
    ...params,
    status: params.status ?? 'IN_PROGRESS',
  })
}

/** API-06 §6 外发队列筛选；status 省略时服务端默认 UNKNOWN。 */
export type OutboundDeliveryQueueQuery = {
  projectId?: string
  status?: string
  businessMessageType?: string
  sourceWorkOrderId?: string
  sourceReviewCaseId?: string
  cursor?: string
  limit?: string
}

export function listOutboundDeliveries(params: OutboundDeliveryQueueQuery = {}) {
  return apiGet<OutboundDeliveryQueuePage>('/outbound-deliveries', params)
}

export function listOperationalExceptions(params: Record<string, string | undefined> = {}) {
  return apiGet<OperationalExceptionPage>('/operational-exceptions', params)
}
