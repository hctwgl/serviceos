package com.serviceos.readmodel.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 工单时间线投影运行时状态、checkpoint 与 dead letter 端口。 */
public interface WorkOrderTimelineProjectionRuntime {
    String PROJECTION_CODE = "work-order-core-timeline.v1";
    String PARTITION_KEY = "*";

    ProjectionState requireState();

    ProjectionDefinition requireDefinition();

    void markRebuilding(int targetGeneration, Instant startedAt);

    void markRunning(int activeGeneration, Instant completedAt);

    void markFailed(String errorCode, Instant failedAt);

    /** FAILED 且无开放 dead letter 时恢复为 RUNNING，不改变 active generation。 */
    void markRecoveredFromFailed(Instant recoveredAt);

    void advanceCheckpoint(
            String tenantId,
            int rebuildGeneration,
            UUID lastSourceOutboxId,
            Instant lastOccurredAt,
            Instant processedAt
    );

    Optional<Checkpoint> findCheckpoint(String tenantId, int rebuildGeneration);

    boolean hasOpenDeadLetters(String tenantId, int rebuildGeneration);

    boolean hasAnyOpenDeadLetters();

    List<DeadLetter> listOpenDeadLetters();

    void recordDeadLetter(
            String tenantId,
            int rebuildGeneration,
            UUID eventId,
            String payloadDigest,
            String eventType,
            int schemaVersion,
            String errorCode,
            Instant failedAt
    );

    void resolveDeadLetter(UUID deadLetterId, String resolution, Instant resolvedAt);

    /** 删除指定 generation 的 checkpoint；条目删除由 Repository 负责。 */
    long deleteCheckpointsForGeneration(int rebuildGeneration);

    record ProjectionState(
            int schemaVersion,
            int activeGeneration,
            String status,
            Instant lastRebuildStartedAt,
            Instant lastRebuildCompletedAt,
            Instant updatedAt
    ) {
    }

    record ProjectionDefinition(
            String projectionCode,
            int schemaVersion,
            String partitionStrategy,
            String rebuildPolicy,
            String freshnessTarget,
            String ownerModule
    ) {
    }

    record Checkpoint(
            String tenantId,
            int rebuildGeneration,
            UUID lastSourceOutboxId,
            Instant lastOccurredAt,
            Instant processedAt,
            String status,
            String errorCode
    ) {
    }

    record DeadLetter(
            UUID deadLetterId,
            String tenantId,
            int rebuildGeneration,
            UUID eventId,
            String payloadDigest,
            String eventType,
            int schemaVersion,
            String errorCode,
            int attemptCount
    ) {
    }
}
