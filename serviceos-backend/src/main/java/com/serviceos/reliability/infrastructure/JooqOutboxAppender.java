package com.serviceos.reliability.infrastructure;

import com.serviceos.jooq.generated.tables.RelOutboxEvent;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxTelemetry;
import com.serviceos.reliability.spi.OutboxTraceHeaders;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.serviceos.jooq.generated.tables.RelOutboxEvent.REL_OUTBOX_EVENT;

/**
 * Outbox 只冻结并追加事件，不负责网络发布。发布 worker 后续按租约认领并至少一次投递。
 */
@Repository
final class JooqOutboxAppender implements OutboxAppender {
    private final DSLContext dsl;
    private final OutboxTelemetry telemetry;

    JooqOutboxAppender(DSLContext dsl, OutboxTelemetry telemetry) {
        this.dsl = dsl;
        this.telemetry = telemetry;
    }

    @Override
    public void append(OutboxEvent event) {
        OutboxTraceHeaders trace = telemetry.capture();
        RelOutboxEvent table = REL_OUTBOX_EVENT;
        // payload 列由全局 JsonbStringConverter 绑定（String -> JSONB），无需手写 CAST；
        // occurred_at/available_at/created_at 统一取事件发生时刻，attempt_count 走数据库默认值 0。
        dsl.insertInto(table)
                .set(table.OUTBOX_ID, event.outboxId())
                .set(table.EVENT_ID, event.eventId())
                .set(table.MODULE_NAME, event.module())
                .set(table.EVENT_TYPE, event.eventType())
                .set(table.SCHEMA_VERSION, event.schemaVersion())
                .set(table.AGGREGATE_TYPE, event.aggregateType())
                .set(table.AGGREGATE_ID, event.aggregateId())
                .set(table.AGGREGATE_VERSION, event.aggregateVersion())
                .set(table.TENANT_ID, event.tenantId())
                .set(table.CORRELATION_ID, event.correlationId())
                .set(table.CAUSATION_ID, event.causationId())
                .set(table.PARTITION_KEY, event.partitionKey())
                .set(table.PAYLOAD, event.payload())
                .set(table.PAYLOAD_DIGEST, event.payloadDigest())
                .set(table.TRACE_PARENT, trace.traceParent())
                .set(table.TRACE_STATE, trace.traceState())
                .set(table.STATUS, "PENDING")
                .set(table.OCCURRED_AT, event.occurredAt())
                .set(table.AVAILABLE_AT, event.occurredAt())
                .set(table.CREATED_AT, event.occurredAt())
                .execute();
    }
}
