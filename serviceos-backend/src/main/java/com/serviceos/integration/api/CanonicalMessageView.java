package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/** 标准消息及其领域命令结果摘要。 */
public record CanonicalMessageView(
        UUID canonicalMessageId,
        UUID projectId,
        String connectorVersionId,
        String messageType,
        String businessKey,
        String payloadDigest,
        String mappingVersionId,
        String processingStatus,
        String resultCode,
        String resultType,
        String resultId,
        Instant createdAt,
        Instant processedAt
) {
}
