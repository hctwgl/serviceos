package com.serviceos.operations.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 工作台列表与详情共用的租户隔离异常投影。 */
public record OperationalExceptionItem(
        UUID exceptionId, String sourceType, String sourceId, UUID sourceAttemptId,
        String sourceTaskType, String category, String severity, String errorCode,
        String status, UUID workOrderId, UUID taskId, UUID handlingTaskId,
        long occurrenceCount, long aggregateVersion, Instant openedAt, Instant lastDetectedAt,
        Instant acknowledgedAt, String acknowledgedBy, String acknowledgementNote,
        Instant resolvedAt, String resolutionCode, List<String> allowedActions
) {
    public OperationalExceptionItem {
        allowedActions = List.copyOf(allowedActions);
    }
}
