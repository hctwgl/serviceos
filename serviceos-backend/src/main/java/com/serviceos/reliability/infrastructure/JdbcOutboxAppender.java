package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Outbox 只冻结并追加事件，不负责网络发布。发布 worker 后续按租约认领并至少一次投递。
 */
@Repository
final class JdbcOutboxAppender implements OutboxAppender {
    private final JdbcClient jdbc;

    JdbcOutboxAppender(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(OutboxEvent event) {
        jdbc.sql("""
                        INSERT INTO rel_outbox_event (
                            outbox_id, event_id, module_name, event_type, schema_version,
                            aggregate_type, aggregate_id, aggregate_version,
                            tenant_id, correlation_id, causation_id, partition_key,
                            payload, payload_digest, status, occurred_at, available_at, created_at
                        ) VALUES (
                            :outboxId, :eventId, :module, :eventType, :schemaVersion,
                            :aggregateType, :aggregateId, :aggregateVersion,
                            :tenantId, :correlationId, :causationId, :partitionKey,
                            CAST(:payload AS jsonb), :payloadDigest, 'PENDING',
                            :occurredAt, :occurredAt, :occurredAt
                        )
                        """)
                .params(Map.ofEntries(
                        Map.entry("outboxId", event.outboxId()),
                        Map.entry("eventId", event.eventId()),
                        Map.entry("module", event.module()),
                        Map.entry("eventType", event.eventType()),
                        Map.entry("schemaVersion", event.schemaVersion()),
                        Map.entry("aggregateType", event.aggregateType()),
                        Map.entry("aggregateId", event.aggregateId()),
                        Map.entry("aggregateVersion", event.aggregateVersion()),
                        Map.entry("tenantId", event.tenantId()),
                        Map.entry("correlationId", event.correlationId()),
                        Map.entry("causationId", event.causationId()),
                        Map.entry("partitionKey", event.partitionKey()),
                        Map.entry("payload", event.payload()),
                        Map.entry("payloadDigest", event.payloadDigest()),
                        Map.entry("occurredAt", event.occurredAt())))
                .update();
    }
}
