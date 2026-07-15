package com.serviceos.task.application;

import com.serviceos.task.api.TaskDetail;
import com.serviceos.task.api.TaskDirectoryItem;
import com.serviceos.task.api.TaskExecutionAttemptView;
import com.serviceos.task.api.TaskTimelineContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDirectoryQueryRepository {
    List<TaskDirectoryItem> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            UUID projectId,
            String taskKind,
            String status,
            String assigneeId,
            Integer cursorPriority,
            Instant cursorNextRunAt,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize);

    Optional<TaskDetail> findDetail(String tenantId, UUID taskId, Instant asOf);

    Optional<AllowedActionState> findAllowedActionState(
            String tenantId, UUID taskId, String principalId);

    List<TaskExecutionAttemptView> findExecutionAttempts(
            String tenantId,
            UUID taskId,
            Integer beforeAttemptNo,
            int fetchSize);

    Optional<TaskTimelineContext> findTimelineContext(String tenantId, UUID taskId);

    record AllowedActionState(
            String taskKind,
            String status,
            long version,
            String claimedBy,
            UUID workflowNodeInstanceId,
            boolean actorCandidate,
            boolean actorResponsible,
            boolean activeGuard
    ) {
    }
}
