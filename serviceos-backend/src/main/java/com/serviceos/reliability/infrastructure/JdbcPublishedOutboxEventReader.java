package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.PublishedOutboxEventReader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 只读扫描 PUBLISHED Outbox，供投影重建；不认领、不改写状态。
 */
@Repository
final class JdbcPublishedOutboxEventReader implements PublishedOutboxEventReader {
    private final JdbcClient jdbc;

    JdbcPublishedOutboxEventReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        if (afterOutboxId == null) {
            return jdbc.sql("""
                    SELECT outbox_id, event_id, module_name, event_type, schema_version,
                           aggregate_type, aggregate_id, aggregate_version, tenant_id,
                           correlation_id, causation_id, partition_key, payload::text AS payload,
                           payload_digest, occurred_at, attempt_count, trace_parent, trace_state,
                           created_at
                      FROM rel_outbox_event
                     WHERE status = 'PUBLISHED'
                       AND event_type IN (:eventTypes)
                     ORDER BY created_at ASC, outbox_id ASC
                     LIMIT :limit
                    """)
                    .param("eventTypes", types)
                    .param("limit", limit)
                    .query(this::mapEvent)
                    .list();
        }
        return jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at, attempt_count, trace_parent, trace_state,
                       created_at
                  FROM rel_outbox_event
                 WHERE status = 'PUBLISHED'
                   AND event_type IN (:eventTypes)
                   AND (created_at, outbox_id) > (:afterCreatedAt, :afterOutboxId)
                 ORDER BY created_at ASC, outbox_id ASC
                 LIMIT :limit
                """)
                .param("eventTypes", types)
                .param("limit", limit)
                .param("afterCreatedAt", timestamptz(afterCreatedAt))
                .param("afterOutboxId", afterOutboxId)
                .query(this::mapEvent)
                .list();
    }

    private PublishedOutboxEvent mapEvent(ResultSet rs, int rowNumber) throws SQLException {
        OutboxMessage message = new OutboxMessage(
                rs.getObject("outbox_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("module_name"),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getLong("aggregate_version"),
                rs.getString("tenant_id"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("partition_key"),
                rs.getString("payload"),
                rs.getString("payload_digest"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("attempt_count"),
                rs.getString("trace_parent"),
                rs.getString("trace_state"));
        return new PublishedOutboxEvent(message, rs.getTimestamp("created_at").toInstant());
    }
}
