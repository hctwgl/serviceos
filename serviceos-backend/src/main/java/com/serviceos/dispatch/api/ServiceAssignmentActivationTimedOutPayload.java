package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** ServiceAssignment 激活 saga 某个精确阶段/版本的超时事实。 */
public record ServiceAssignmentActivationTimedOutPayload(
        UUID timeoutId,
        UUID sagaId,
        UUID serviceAssignmentId,
        UUID workOrderId,
        UUID taskId,
        String stage,
        long sagaVersion,
        Instant deadlineAt,
        Instant detectedAt,
        String errorCode
) {
}
