package com.serviceos.integration.api;

import java.util.UUID;

public record InboundEnvelopeQueueQuery(
        UUID projectId,
        String processingStatus,
        String messageType,
        String resultType,
        String resultId,
        UUID canonicalMessageId,
        String cursor,
        int limit
) {
}
