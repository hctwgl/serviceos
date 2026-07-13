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
        int attemptNo,
        String traceParent,
        String traceState
) {
    /**
     * 兼容不需要追踪上下文的纯单元测试和内部调用；生产消息由数据库映射完整构造器。
     */
    public OutboxMessage(
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
        this(outboxId, eventId, module, eventType, schemaVersion, aggregateType, aggregateId,
                aggregateVersion, tenantId, correlationId, causationId, partitionKey, payload,
                payloadDigest, occurredAt, attemptNo, null, null);
    }
}
