package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

/** OperationalExceptionResolved 领域事件的可核验恢复摘要。 */
public record OperationalExceptionResolvedPayload(
        UUID exceptionId,
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID handlingTaskId,
        String handlingTaskStatus,
        String resolutionCode,
        String resolutionActionRef,
        UUID resolutionEventId,
        Instant resolvedAt
) {
}
