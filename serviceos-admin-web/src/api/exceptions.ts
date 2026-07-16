import { apiGet, apiPost, newIdempotencyKey, quotedVersion } from './client'

export type OperationalException = {
  exceptionId: string
  projectId: string | null
  sourceType: string
  sourceId: string
  sourceAttemptId: string
  sourceTaskType: string
  category: string
  severity: 'P0' | 'P1' | 'P2' | 'P3'
  errorCode: string
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  workOrderId: string | null
  taskId: string | null
  handlingTaskId: string | null
  occurrenceCount: number
  aggregateVersion: number
  openedAt: string
  lastDetectedAt: string
  acknowledgedAt: string | null
  acknowledgedBy: string | null
  acknowledgementNote: string | null
  resolvedAt: string | null
  resolutionCode: string | null
  allowedActions: Array<'ACKNOWLEDGE'>
}

export type OperationalExceptionAcknowledgement = {
  exceptionId: string
  status: 'ACKNOWLEDGED'
  aggregateVersion: number
  acknowledgedAt: string
  acknowledgedBy: string
}

export function getOperationalException(exceptionId: string) {
  return apiGet<OperationalException>(`/operational-exceptions/${exceptionId}`)
}

export function acknowledgeOperationalException(
  exceptionId: string,
  aggregateVersion: number,
  note?: string | null,
) {
  return apiPost<OperationalExceptionAcknowledgement>(
    `/operational-exceptions/${exceptionId}:acknowledge`,
    {
      idempotencyKey: newIdempotencyKey('exception-ack'),
      ifMatch: quotedVersion(aggregateVersion),
      body: { note: note ?? null },
    },
  )
}
