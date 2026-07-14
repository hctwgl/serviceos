package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

/** service.assignment.activation-timed-out 的标准消费输入。 */
public record OpenServiceAssignmentTimeoutCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        UUID timeoutId,
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID workOrderId,
        UUID taskId,
        String stage,
        long sagaVersion,
        String errorCode,
        Instant detectedAt,
        String correlationId
) {
}
