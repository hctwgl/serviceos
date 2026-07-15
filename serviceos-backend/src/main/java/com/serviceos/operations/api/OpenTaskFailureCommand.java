package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

/**
 * task.execution.manual-intervention-required 的标准消费输入。
 */
public record OpenTaskFailureCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        UUID sourceTaskId,
        UUID sourceAttemptId,
        String sourceTaskType,
        String errorCode,
        Instant detectedAt,
        String correlationId
) {
}
