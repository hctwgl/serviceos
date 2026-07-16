package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.application.WorkOrderTimelineProjectionRuntime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

@Repository
final class JdbcWorkOrderTimelineProjectionRuntime implements WorkOrderTimelineProjectionRuntime {
    private final JdbcClient jdbc;

    JdbcWorkOrderTimelineProjectionRuntime(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectionState requireState() {
        return jdbc.sql("""
                SELECT schema_version, active_generation, status,
                       last_rebuild_started_at, last_rebuild_completed_at, updated_at
                  FROM rdm_projection_state
                 WHERE projection_code = :code
                """)
                .param("code", PROJECTION_CODE)
                .query((rs, row) -> new ProjectionState(
                        rs.getInt("schema_version"),
                        rs.getInt("active_generation"),
                        rs.getString("status"),
                        optionalInstant(rs.getTimestamp("last_rebuild_started_at")),
                        optionalInstant(rs.getTimestamp("last_rebuild_completed_at")),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "缺少投影状态: " + PROJECTION_CODE));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectionDefinition requireDefinition() {
        return jdbc.sql("""
                SELECT projection_code, schema_version, partition_strategy,
                       rebuild_policy, freshness_target, owner_module
                  FROM rdm_projection_definition
                 WHERE projection_code = :code
                """)
                .param("code", PROJECTION_CODE)
                .query((rs, row) -> new ProjectionDefinition(
                        rs.getString("projection_code"),
                        rs.getInt("schema_version"),
                        rs.getString("partition_strategy"),
                        rs.getString("rebuild_policy"),
                        rs.getString("freshness_target"),
                        rs.getString("owner_module")))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "缺少投影定义: " + PROJECTION_CODE));
    }

    @Override
    @Transactional
    public void markRebuilding(int targetGeneration, Instant startedAt) {
        int updated = jdbc.sql("""
                UPDATE rdm_projection_state
                   SET status = 'REBUILDING',
                       last_rebuild_started_at = :startedAt,
                       updated_at = :startedAt
                 WHERE projection_code = :code
                   AND status IN ('RUNNING', 'LAGGING', 'FAILED')
                   AND active_generation = :expectedActive
                """)
                .param("code", PROJECTION_CODE)
                .param("startedAt", timestamptz(startedAt))
                .param("expectedActive", targetGeneration - 1)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("无法进入 REBUILDING：投影状态冲突");
        }
    }

    @Override
    @Transactional
    public void markRunning(int activeGeneration, Instant completedAt) {
        int updated = jdbc.sql("""
                UPDATE rdm_projection_state
                   SET status = 'RUNNING',
                       active_generation = :generation,
                       last_rebuild_completed_at = :completedAt,
                       updated_at = :completedAt
                 WHERE projection_code = :code
                   AND status = 'REBUILDING'
                """)
                .param("code", PROJECTION_CODE)
                .param("generation", activeGeneration)
                .param("completedAt", timestamptz(completedAt))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("无法切换 active generation：投影不在 REBUILDING");
        }
    }

    @Override
    @Transactional
    public void markFailed(String errorCode, Instant failedAt) {
        jdbc.sql("""
                UPDATE rdm_projection_state
                   SET status = 'FAILED',
                       updated_at = :failedAt
                 WHERE projection_code = :code
                """)
                .param("code", PROJECTION_CODE)
                .param("failedAt", timestamptz(failedAt))
                .update();
    }

    @Override
    @Transactional
    public void markRecoveredFromFailed(Instant recoveredAt) {
        int updated = jdbc.sql("""
                UPDATE rdm_projection_state
                   SET status = 'RUNNING',
                       updated_at = :recoveredAt
                 WHERE projection_code = :code
                   AND status = 'FAILED'
                """)
                .param("code", PROJECTION_CODE)
                .param("recoveredAt", timestamptz(recoveredAt))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("无法从 FAILED 恢复：投影状态冲突");
        }
    }

    @Override
    @Transactional
    public void advanceCheckpoint(
            String tenantId,
            int rebuildGeneration,
            UUID lastSourceOutboxId,
            Instant lastOccurredAt,
            Instant processedAt
    ) {
        jdbc.sql("""
                INSERT INTO rdm_projection_checkpoint (
                    projection_code, tenant_id, partition_key, rebuild_generation,
                    last_source_outbox_id, last_occurred_at, processed_at, status, error_code
                ) VALUES (
                    :code, :tenantId, :partition, :generation,
                    :outboxId, :occurredAt, :processedAt, 'RUNNING', NULL
                )
                ON CONFLICT (projection_code, tenant_id, partition_key, rebuild_generation)
                DO UPDATE SET
                    last_source_outbox_id = EXCLUDED.last_source_outbox_id,
                    last_occurred_at = EXCLUDED.last_occurred_at,
                    processed_at = EXCLUDED.processed_at,
                    status = 'RUNNING',
                    error_code = NULL
                """)
                .param("code", PROJECTION_CODE)
                .param("tenantId", tenantId)
                .param("partition", PARTITION_KEY)
                .param("generation", rebuildGeneration)
                .param("outboxId", lastSourceOutboxId)
                .param("occurredAt", timestamptz(lastOccurredAt))
                .param("processedAt", timestamptz(processedAt))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Checkpoint> findCheckpoint(String tenantId, int rebuildGeneration) {
        return jdbc.sql("""
                SELECT tenant_id, rebuild_generation, last_source_outbox_id, last_occurred_at,
                       processed_at, status, error_code
                  FROM rdm_projection_checkpoint
                 WHERE projection_code = :code
                   AND tenant_id = :tenantId
                   AND partition_key = :partition
                   AND rebuild_generation = :generation
                """)
                .param("code", PROJECTION_CODE)
                .param("tenantId", tenantId)
                .param("partition", PARTITION_KEY)
                .param("generation", rebuildGeneration)
                .query((rs, row) -> new Checkpoint(
                        rs.getString("tenant_id"),
                        rs.getInt("rebuild_generation"),
                        rs.getObject("last_source_outbox_id", UUID.class),
                        optionalInstant(rs.getTimestamp("last_occurred_at")),
                        rs.getTimestamp("processed_at").toInstant(),
                        rs.getString("status"),
                        rs.getString("error_code")))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenDeadLetters(String tenantId, int rebuildGeneration) {
        Long count = jdbc.sql("""
                SELECT count(*) FROM rdm_projection_dead_letter
                 WHERE projection_code = :code
                   AND tenant_id = :tenantId
                   AND rebuild_generation = :generation
                   AND resolved_at IS NULL
                """)
                .param("code", PROJECTION_CODE)
                .param("tenantId", tenantId)
                .param("generation", rebuildGeneration)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyOpenDeadLetters() {
        Long count = jdbc.sql("""
                SELECT count(*) FROM rdm_projection_dead_letter
                 WHERE projection_code = :code
                   AND resolved_at IS NULL
                """)
                .param("code", PROJECTION_CODE)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetter> listOpenDeadLetters() {
        return jdbc.sql("""
                SELECT dead_letter_id, tenant_id, rebuild_generation, event_id, payload_digest,
                       event_type, schema_version, error_code, attempt_count
                  FROM rdm_projection_dead_letter
                 WHERE projection_code = :code
                   AND resolved_at IS NULL
                 ORDER BY first_failed_at, dead_letter_id
                """)
                .param("code", PROJECTION_CODE)
                .query((rs, row) -> new DeadLetter(
                        rs.getObject("dead_letter_id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getInt("rebuild_generation"),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("payload_digest"),
                        rs.getString("event_type"),
                        rs.getInt("schema_version"),
                        rs.getString("error_code"),
                        rs.getInt("attempt_count")))
                .list();
    }

    @Override
    @Transactional
    public void recordDeadLetter(
            String tenantId,
            int rebuildGeneration,
            UUID eventId,
            String payloadDigest,
            String eventType,
            int schemaVersion,
            String errorCode,
            Instant failedAt
    ) {
        jdbc.sql("""
                INSERT INTO rdm_projection_dead_letter (
                    dead_letter_id, projection_code, tenant_id, rebuild_generation,
                    event_id, payload_digest, event_type, schema_version, error_code,
                    attempt_count, first_failed_at, last_failed_at, resolved_at, resolution
                ) VALUES (
                    :id, :code, :tenantId, :generation,
                    :eventId, :digest, :eventType, :schemaVersion, :errorCode,
                    1, :failedAt, :failedAt, NULL, 'PENDING'
                )
                ON CONFLICT (projection_code, tenant_id, rebuild_generation, event_id)
                DO UPDATE SET
                    payload_digest = EXCLUDED.payload_digest,
                    error_code = EXCLUDED.error_code,
                    attempt_count = rdm_projection_dead_letter.attempt_count + 1,
                    last_failed_at = EXCLUDED.last_failed_at,
                    resolved_at = NULL,
                    resolution = 'PENDING'
                """)
                .param("id", UUID.randomUUID())
                .param("code", PROJECTION_CODE)
                .param("tenantId", tenantId)
                .param("generation", rebuildGeneration)
                .param("eventId", eventId)
                .param("digest", payloadDigest)
                .param("eventType", eventType)
                .param("schemaVersion", schemaVersion)
                .param("errorCode", truncate(errorCode, 100))
                .param("failedAt", timestamptz(failedAt))
                .update();
    }

    @Override
    @Transactional
    public void resolveDeadLetter(UUID deadLetterId, String resolution, Instant resolvedAt) {
        if (!"REPLAYED".equals(resolution) && !"DISCARDED".equals(resolution)) {
            throw new IllegalArgumentException("resolution must be REPLAYED or DISCARDED");
        }
        int updated = jdbc.sql("""
                UPDATE rdm_projection_dead_letter
                   SET resolved_at = :resolvedAt,
                       resolution = :resolution
                 WHERE dead_letter_id = :id
                   AND projection_code = :code
                   AND resolved_at IS NULL
                """)
                .param("id", deadLetterId)
                .param("code", PROJECTION_CODE)
                .param("resolvedAt", timestamptz(resolvedAt))
                .param("resolution", resolution)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("dead letter 不存在或已解决: " + deadLetterId);
        }
    }

    @Override
    @Transactional
    public long deleteCheckpointsForGeneration(int rebuildGeneration) {
        return jdbc.sql("""
                DELETE FROM rdm_projection_checkpoint
                 WHERE projection_code = :code
                   AND rebuild_generation = :generation
                """)
                .param("code", PROJECTION_CODE)
                .param("generation", rebuildGeneration)
                .update();
    }

    private static Instant optionalInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "UNKNOWN";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
