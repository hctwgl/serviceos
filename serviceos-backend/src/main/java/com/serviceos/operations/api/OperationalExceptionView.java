package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalExceptionView(
        UUID exceptionId,
        String tenantId,
        UUID sourceTaskId,
        UUID sourceAttemptId,
        String sourceTaskType,
        String errorCode,
        String status,
        UUID handlingTaskId,
        Instant openedAt
) {
}
