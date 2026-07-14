package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/** 候选人快照命令的冻结响应。 */
public record TaskAssignmentBatchReceipt(
        UUID assignmentBatchId,
        UUID taskId,
        int candidateCount,
        long taskVersion,
        Instant assignedAt
) {
}
