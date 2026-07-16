package com.serviceos.readmodel.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 工单时间线投影运行时状态、checkpoint 与 dead letter 端口。 */
public interface WorkOrderTimelineProjectionRuntime {
    String PROJECTION_CODE = "work-order-core-timeline.v1";
    String PARTITION_KEY = "*";

    ProjectionState requireState();

    void markRebuilding(int targetGeneration, Instant startedAt);

    void markRunning(int activeGeneration, Instant completedAt);

    void markFailed(String errorCode, Instant failedAt);

    void advanceCheckpoint(
            String tenantId,
            int rebuildGeneration,
            UUID lastSourceOutboxId,
            Instant lastOccurredAt,
            Instant processedAt
    );

    Optional<Checkpoint> findCheckpoint(String tenantId, int rebuildGeneration);

    boolean hasOpenDeadLetters(String tenantId, int rebuildGeneration);

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

    record ProjectionState(
            int schemaVersion,
            int activeGeneration,
            String status,
            Instant lastRebuildStartedAt,
            Instant lastRebuildCompletedAt,
            Instant updatedAt
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
}
