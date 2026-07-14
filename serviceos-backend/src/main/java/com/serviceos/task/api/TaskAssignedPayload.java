package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 责任策略解析后的 USER 候选快照。 */
public record TaskAssignedPayload(
        UUID taskId,
        UUID assignmentBatchId,
        List<String> candidatePrincipalIds,
        AssignmentSourceType sourceType,
        String sourceId,
        Instant assignedAt
) {
}
