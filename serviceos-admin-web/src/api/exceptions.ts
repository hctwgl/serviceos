import { apiPost, newIdempotencyKey, quotedVersion } from './client'

export type OperationalExceptionAcknowledgement = {
  exceptionId: string
  status: 'ACKNOWLEDGED'
  aggregateVersion: number
  acknowledgedAt: string
  acknowledgedBy: string
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
