package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 入站消息安全摘要视图；原文对象引用、签名值和传输凭据不通过普通查询返回。
 */
public record InboundEnvelopeView(
        UUID inboundEnvelopeId,
        UUID projectId,
        String connectorVersionId,
        String messageType,
        String externalMessageId,
        String rawPayloadDigest,
        String canonicalPayloadDigest,
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
