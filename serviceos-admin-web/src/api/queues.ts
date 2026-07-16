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

export function listReviewCases(params: Record<string, string | undefined> = {}) {
  return apiGet<ReviewCaseQueuePage>('/review-cases', params)
}

export function listCorrectionCases(params: Record<string, string | undefined> = {}) {
  return apiGet<CorrectionCaseQueuePage>('/correction-cases', {
    status: params.status ?? 'IN_PROGRESS',
    ...params,
  })
}

export function listOutboundDeliveries(params: Record<string, string | undefined> = {}) {
  return apiGet<OutboundDeliveryQueuePage>('/outbound-deliveries', params)
}

export function listOperationalExceptions(params: Record<string, string | undefined> = {}) {
  return apiGet<OperationalExceptionPage>('/operational-exceptions', params)
}
