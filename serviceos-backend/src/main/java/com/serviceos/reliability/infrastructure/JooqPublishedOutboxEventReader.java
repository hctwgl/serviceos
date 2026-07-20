package com.serviceos.reliability.infrastructure;

import com.serviceos.jooq.generated.tables.RelOutboxEvent;
import com.serviceos.jooq.generated.tables.records.RelOutboxEventRecord;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.PublishedOutboxEventReader;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RelOutboxEvent.REL_OUTBOX_EVENT;

/**
 * 只读扫描 PUBLISHED Outbox，供投影重建；不认领、不改写状态。
 */
@Repository
final class JooqPublishedOutboxEventReader implements PublishedOutboxEventReader {
    private final DSLContext dsl;

    JooqPublishedOutboxEventReader(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PublishedOutboxEvent> scanPublished(
            Collection<String> eventTypes,
            UUID afterOutboxId,
            Instant afterCreatedAt,
            int limit
    ) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        // after 必须成对出现，避免半开游标扫出重复或漏事件。
        if ((afterOutboxId == null) != (afterCreatedAt == null)) {
            throw new IllegalArgumentException("afterOutboxId and afterCreatedAt must both be null or both set");
        }
        List<String> types = List.copyOf(eventTypes);
        RelOutboxEvent event = REL_OUTBOX_EVENT;
        Condition condition = event.STATUS.eq("PUBLISHED").and(event.EVENT_TYPE.in(types));
        if (afterOutboxId != null) {
            // 行值比较与 (created_at, outbox_id) > (...) 等价，严格推进游标。
            condition = condition.and(
                    DSL.row(event.CREATED_AT, event.OUTBOX_ID).gt(afterCreatedAt, afterOutboxId));
        }
        return dsl.selectFrom(event)
                .where(condition)
                .orderBy(event.CREATED_AT.asc(), event.OUTBOX_ID.asc())
                .limit(limit)
                .fetch(this::mapEvent);
    }

    @Override
    public Optional<PublishedOutboxEvent> findPublishedByEventId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        RelOutboxEvent event = REL_OUTBOX_EVENT;
        return dsl.selectFrom(event)
                .where(event.STATUS.eq("PUBLISHED"))
                .and(event.EVENT_ID.eq(eventId))
                .fetchOptional()
                .map(this::mapEvent);
    }

    private PublishedOutboxEvent mapEvent(RelOutboxEventRecord record) {
        // payload 列经 JsonbStringConverter 直接映射为 String，无需 ::text。
        OutboxMessage message = new OutboxMessage(
                record.getOutboxId(),
                record.getEventId(),
                record.getModuleName(),
                record.getEventType(),
                record.getSchemaVersion(),
                record.getAggregateType(),
                record.getAggregateId(),
                record.getAggregateVersion(),
                record.getTenantId(),
                record.getCorrelationId(),
                record.getCausationId(),
                record.getPartitionKey(),
                record.getPayload(),
                record.getPayloadDigest(),
                record.getOccurredAt(),
                record.getAttemptCount(),
                record.getTraceParent(),
                record.getTraceState());
        return new PublishedOutboxEvent(message, record.getCreatedAt());
    }
}
