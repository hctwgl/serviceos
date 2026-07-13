package com.serviceos.reliability.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * 已认领且 payload 不可变的发布消息。重试必须保持 eventId、payload 和 digest 不变。
 */
public record OutboxMessage(
        UUID outboxId,
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
        Instant occurredAt,
        int attemptNo
) {
}
