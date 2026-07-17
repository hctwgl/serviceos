package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 入站 Envelope 队列安全摘要；不含 payload digest、对象引用、签名原文或传输凭据。
 */
public record InboundEnvelopeQueueItem(
        UUID inboundEnvelopeId,
        UUID projectId,
        String connectorVersionId,
        String messageType,
        String externalMessageId,
        String signatureStatus,
        String processingStatus,
        String mappingVersionId,
        UUID canonicalMessageId,
        String resultCode,
        String resultType,
        String resultId,
        Instant receivedAt,
        Instant completedAt,
        String correlationId
) {
}
