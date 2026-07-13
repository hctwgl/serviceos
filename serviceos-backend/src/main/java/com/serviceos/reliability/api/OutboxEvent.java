package com.serviceos.reliability.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 在领域事务中冻结的可靠事件信封。
 */
public record OutboxEvent(
        UUID eventId,
        String module,
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        String tenantId,
        String correlationId,
        String causationId,
        String partitionKey,
        String payload,
        String payloadDigest,
        Instant occurredAt
) {
}
