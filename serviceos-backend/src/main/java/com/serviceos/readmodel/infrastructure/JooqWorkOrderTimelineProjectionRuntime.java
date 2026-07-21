package com.serviceos.readmodel.infrastructure;

import com.serviceos.jooq.generated.tables.RdmProjectionCheckpoint;
import com.serviceos.jooq.generated.tables.RdmProjectionDeadLetter;
import com.serviceos.jooq.generated.tables.RdmProjectionDefinition;
import com.serviceos.jooq.generated.tables.RdmProjectionState;
import com.serviceos.readmodel.application.WorkOrderTimelineProjectionRuntime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RdmProjectionCheckpoint.RDM_PROJECTION_CHECKPOINT;
import static com.serviceos.jooq.generated.tables.RdmProjectionDeadLetter.RDM_PROJECTION_DEAD_LETTER;
import static com.serviceos.jooq.generated.tables.RdmProjectionDefinition.RDM_PROJECTION_DEFINITION;
import static com.serviceos.jooq.generated.tables.RdmProjectionState.RDM_PROJECTION_STATE;
import static org.jooq.impl.DSL.excluded;

@Repository
final class JooqWorkOrderTimelineProjectionRuntime implements WorkOrderTimelineProjectionRuntime {
    private final DSLContext dsl;

    JooqWorkOrderTimelineProjectionRuntime(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectionState requireState() {
        RdmProjectionState state = RDM_PROJECTION_STATE;
        return dsl.select(
                        state.SCHEMA_VERSION, state.ACTIVE_GENERATION, state.STATUS,
                        state.LAST_REBUILD_STARTED_AT, state.LAST_REBUILD_COMPLETED_AT,
                        state.UPDATED_AT)
                .from(state)
                .where(state.PROJECTION_CODE.eq(PROJECTION_CODE))
                .fetchOptional(JooqWorkOrderTimelineProjectionRuntime::mapState)
                .orElseThrow(() -> new IllegalStateException(
                        "缺少投影状态: " + PROJECTION_CODE));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectionDefinition requireDefinition() {
        RdmProjectionDefinition definition = RDM_PROJECTION_DEFINITION;
        return dsl.select(
                        definition.PROJECTION_CODE, definition.SCHEMA_VERSION,
                        definition.PARTITION_STRATEGY, definition.REBUILD_POLICY,
                        definition.FRESHNESS_TARGET, definition.OWNER_MODULE)
                .from(definition)
                .where(definition.PROJECTION_CODE.eq(PROJECTION_CODE))
                .fetchOptional(row -> new ProjectionDefinition(
                        row.get(definition.PROJECTION_CODE),
                        row.get(definition.SCHEMA_VERSION),
                        row.get(definition.PARTITION_STRATEGY),
                        row.get(definition.REBUILD_POLICY),
                        row.get(definition.FRESHNESS_TARGET),
                        row.get(definition.OWNER_MODULE)))
                .orElseThrow(() -> new IllegalStateException(
                        "缺少投影定义: " + PROJECTION_CODE));
    }

    @Override
    @Transactional
    public void markRebuilding(int targetGeneration, Instant startedAt) {
        // 状态与原 active generation 双条件，防止并发重建互相覆盖。
        RdmProjectionState state = RDM_PROJECTION_STATE;
        int updated = dsl.update(state)
                .set(state.STATUS, "REBUILDING")
                .set(state.LAST_REBUILD_STARTED_AT, startedAt)
                .set(state.UPDATED_AT, startedAt)
                .where(state.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(state.STATUS.in("RUNNING", "LAGGING", "FAILED"))
                .and(state.ACTIVE_GENERATION.eq(targetGeneration - 1))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("无法进入 REBUILDING：投影状态冲突");
        }
    }

    @Override
    @Transactional
    public void markRunning(int activeGeneration, Instant completedAt) {
        RdmProjectionState state = RDM_PROJECTION_STATE;
        int updated = dsl.update(state)
                .set(state.STATUS, "RUNNING")
                .set(state.ACTIVE_GENERATION, activeGeneration)
                .set(state.LAST_REBUILD_COMPLETED_AT, completedAt)
                .set(state.UPDATED_AT, completedAt)
                .where(state.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(state.STATUS.eq("REBUILDING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("无法切换 active generation：投影不在 REBUILDING");
        }
    }

    @Override
    @Transactional
    public void markFailed(String errorCode, Instant failedAt) {
        RdmProjectionState state = RDM_PROJECTION_STATE;
        dsl.update(state)
                .set(state.STATUS, "FAILED")
                .set(state.UPDATED_AT, failedAt)
                .where(state.PROJECTION_CODE.eq(PROJECTION_CODE))
                .execute();
    }

    @Override
    @Transactional
    public void markRecoveredFromFailed(Instant recoveredAt) {
        RdmProjectionState state = RDM_PROJECTION_STATE;
        int updated = dsl.update(state)
                .set(state.STATUS, "RUNNING")
                .set(state.UPDATED_AT, recoveredAt)
                .where(state.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(state.STATUS.eq("FAILED"))
                .execute();
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
        // 冲突即同一 (projection,tenant,partition,generation) 的续推：推进游标并清错误状态。
        RdmProjectionCheckpoint checkpoint = RDM_PROJECTION_CHECKPOINT;
        dsl.insertInto(checkpoint)
                .set(checkpoint.PROJECTION_CODE, PROJECTION_CODE)
                .set(checkpoint.TENANT_ID, tenantId)
                .set(checkpoint.PARTITION_KEY, PARTITION_KEY)
                .set(checkpoint.REBUILD_GENERATION, rebuildGeneration)
                .set(checkpoint.LAST_SOURCE_OUTBOX_ID, lastSourceOutboxId)
                .set(checkpoint.LAST_OCCURRED_AT, lastOccurredAt)
                .set(checkpoint.PROCESSED_AT, processedAt)
                .set(checkpoint.STATUS, "RUNNING")
                .setNull(checkpoint.ERROR_CODE)
                .onConflict(checkpoint.PROJECTION_CODE, checkpoint.TENANT_ID,
                        checkpoint.PARTITION_KEY, checkpoint.REBUILD_GENERATION)
                .doUpdate()
                .set(checkpoint.LAST_SOURCE_OUTBOX_ID, excluded(checkpoint.LAST_SOURCE_OUTBOX_ID))
                .set(checkpoint.LAST_OCCURRED_AT, excluded(checkpoint.LAST_OCCURRED_AT))
                .set(checkpoint.PROCESSED_AT, excluded(checkpoint.PROCESSED_AT))
                .set(checkpoint.STATUS, "RUNNING")
                .setNull(checkpoint.ERROR_CODE)
                .execute();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Checkpoint> findCheckpoint(String tenantId, int rebuildGeneration) {
        RdmProjectionCheckpoint checkpoint = RDM_PROJECTION_CHECKPOINT;
        return dsl.select(
                        checkpoint.TENANT_ID, checkpoint.REBUILD_GENERATION,
                        checkpoint.LAST_SOURCE_OUTBOX_ID, checkpoint.LAST_OCCURRED_AT,
                        checkpoint.PROCESSED_AT, checkpoint.STATUS, checkpoint.ERROR_CODE)
                .from(checkpoint)
                .where(checkpoint.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(checkpoint.TENANT_ID.eq(tenantId))
                .and(checkpoint.PARTITION_KEY.eq(PARTITION_KEY))
                .and(checkpoint.REBUILD_GENERATION.eq(rebuildGeneration))
                .fetchOptional(row -> new Checkpoint(
                        row.get(checkpoint.TENANT_ID),
                        row.get(checkpoint.REBUILD_GENERATION),
                        row.get(checkpoint.LAST_SOURCE_OUTBOX_ID),
                        row.get(checkpoint.LAST_OCCURRED_AT),
                        row.get(checkpoint.PROCESSED_AT),
                        row.get(checkpoint.STATUS),
                        row.get(checkpoint.ERROR_CODE)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenDeadLetters(String tenantId, int rebuildGeneration) {
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        return dsl.fetchCount(deadLetter,
                deadLetter.PROJECTION_CODE.eq(PROJECTION_CODE)
                        .and(deadLetter.TENANT_ID.eq(tenantId))
                        .and(deadLetter.REBUILD_GENERATION.eq(rebuildGeneration))
                        .and(deadLetter.RESOLVED_AT.isNull())) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyOpenDeadLetters() {
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        return dsl.fetchCount(deadLetter,
                deadLetter.PROJECTION_CODE.eq(PROJECTION_CODE)
                        .and(deadLetter.RESOLVED_AT.isNull())) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetter> listOpenDeadLetters() {
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        return dsl.select(
                        deadLetter.DEAD_LETTER_ID, deadLetter.TENANT_ID, deadLetter.REBUILD_GENERATION,
                        deadLetter.EVENT_ID, deadLetter.PAYLOAD_DIGEST, deadLetter.EVENT_TYPE,
                        deadLetter.SCHEMA_VERSION, deadLetter.ERROR_CODE, deadLetter.ATTEMPT_COUNT)
                .from(deadLetter)
                .where(deadLetter.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(deadLetter.RESOLVED_AT.isNull())
                .orderBy(deadLetter.FIRST_FAILED_AT, deadLetter.DEAD_LETTER_ID)
                .fetch(JooqWorkOrderTimelineProjectionRuntime::mapDeadLetter);
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
        // 冲突即同一事件再次失败：attempt_count 累加并重新置为未解决。
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        dsl.insertInto(deadLetter)
                .set(deadLetter.DEAD_LETTER_ID, UUID.randomUUID())
                .set(deadLetter.PROJECTION_CODE, PROJECTION_CODE)
                .set(deadLetter.TENANT_ID, tenantId)
                .set(deadLetter.REBUILD_GENERATION, rebuildGeneration)
                .set(deadLetter.EVENT_ID, eventId)
                .set(deadLetter.PAYLOAD_DIGEST, payloadDigest)
                .set(deadLetter.EVENT_TYPE, eventType)
                .set(deadLetter.SCHEMA_VERSION, schemaVersion)
                .set(deadLetter.ERROR_CODE, truncate(errorCode, 100))
                .set(deadLetter.ATTEMPT_COUNT, 1)
                .set(deadLetter.FIRST_FAILED_AT, failedAt)
                .set(deadLetter.LAST_FAILED_AT, failedAt)
                .setNull(deadLetter.RESOLVED_AT)
                .set(deadLetter.RESOLUTION, "PENDING")
                .onConflict(deadLetter.PROJECTION_CODE, deadLetter.TENANT_ID,
                        deadLetter.REBUILD_GENERATION, deadLetter.EVENT_ID)
                .doUpdate()
                .set(deadLetter.PAYLOAD_DIGEST, excluded(deadLetter.PAYLOAD_DIGEST))
                .set(deadLetter.ERROR_CODE, excluded(deadLetter.ERROR_CODE))
                .set(deadLetter.ATTEMPT_COUNT, deadLetter.ATTEMPT_COUNT.plus(1))
                .set(deadLetter.LAST_FAILED_AT, excluded(deadLetter.LAST_FAILED_AT))
                .setNull(deadLetter.RESOLVED_AT)
                .set(deadLetter.RESOLUTION, "PENDING")
                .execute();
    }

    @Override
    @Transactional
    public void resolveDeadLetter(UUID deadLetterId, String resolution, Instant resolvedAt) {
        if (!"REPLAYED".equals(resolution) && !"DISCARDED".equals(resolution)) {
            throw new IllegalArgumentException("resolution must be REPLAYED or DISCARDED");
        }
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        int updated = dsl.update(deadLetter)
                .set(deadLetter.RESOLVED_AT, resolvedAt)
                .set(deadLetter.RESOLUTION, resolution)
                .where(deadLetter.DEAD_LETTER_ID.eq(deadLetterId))
                .and(deadLetter.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(deadLetter.RESOLVED_AT.isNull())
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("dead letter 不存在或已解决: " + deadLetterId);
        }
    }

    @Override
    @Transactional
    public long deleteCheckpointsForGeneration(int rebuildGeneration) {
        RdmProjectionCheckpoint checkpoint = RDM_PROJECTION_CHECKPOINT;
        return dsl.deleteFrom(checkpoint)
                .where(checkpoint.PROJECTION_CODE.eq(PROJECTION_CODE))
                .and(checkpoint.REBUILD_GENERATION.eq(rebuildGeneration))
                .execute();
    }

    private static ProjectionState mapState(Record row) {
        RdmProjectionState state = RDM_PROJECTION_STATE;
        return new ProjectionState(
                row.get(state.SCHEMA_VERSION),
                row.get(state.ACTIVE_GENERATION),
                row.get(state.STATUS),
                row.get(state.LAST_REBUILD_STARTED_AT),
                row.get(state.LAST_REBUILD_COMPLETED_AT),
                row.get(state.UPDATED_AT));
    }

    private static DeadLetter mapDeadLetter(Record row) {
        RdmProjectionDeadLetter deadLetter = RDM_PROJECTION_DEAD_LETTER;
        return new DeadLetter(
                row.get(deadLetter.DEAD_LETTER_ID),
                row.get(deadLetter.TENANT_ID),
                row.get(deadLetter.REBUILD_GENERATION),
                row.get(deadLetter.EVENT_ID),
                row.get(deadLetter.PAYLOAD_DIGEST),
                row.get(deadLetter.EVENT_TYPE),
                row.get(deadLetter.SCHEMA_VERSION),
                row.get(deadLetter.ERROR_CODE),
                row.get(deadLetter.ATTEMPT_COUNT));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "UNKNOWN";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
