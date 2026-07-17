import { apiGet } from './client'

/** 入站 Envelope 安全摘要；不含原文对象引用、签名值与传输凭据。 */
export type InboundEnvelope = {
  inboundEnvelopeId: string
  projectId: string | null
  connectorVersionId: string
  messageType: 'CREATE_WORK_ORDER' | 'RECORD_CLIENT_REVIEW_RESULT' | string
  externalMessageId: string
  rawPayloadDigest: string
  canonicalPayloadDigest: string | null
  signatureStatus: string
  processingStatus: 'RECEIVED' | 'COMPLETED' | 'REJECTED' | string
  mappingVersionId: string | null
  canonicalMessageId: string | null
  resultCode: string | null
  resultType: string | null
  resultId: string | null
  receivedAt: string
  completedAt: string | null
  correlationId: string
}

export type CanonicalMessage = {
  canonicalMessageId: string
  projectId: string | null
  connectorVersionId: string
  messageType: 'CREATE_WORK_ORDER' | 'RECORD_CLIENT_REVIEW_RESULT' | string
  businessKey: string
  payloadDigest: string
  mappingVersionId: string
  processingStatus: 'PROCESSING' | 'COMPLETED' | string
  resultCode: string | null
  resultType: string | null
  resultId: string | null
  createdAt: string
  processedAt: string | null
}

export function getInboundEnvelope(envelopeId: string) {
  return apiGet<InboundEnvelope>(`/inbound-envelopes/${envelopeId}`)
}

export function getCanonicalMessage(messageId: string) {
  return apiGet<CanonicalMessage>(`/canonical-messages/${messageId}`)
}
