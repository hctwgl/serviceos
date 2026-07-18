package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 按流程实例批量取消仍开放的任务（工单取消/人工跳转级联）。 */
public record CancelOpenWorkflowTasksCommand(
        String tenantId,
        List<UUID> workflowInstanceIds,
        String reasonCode,
        UUID sourceEventId,
        Instant cancelledAt,
        String correlationId
) {
    public CancelOpenWorkflowTasksCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workflowInstanceIds == null || workflowInstanceIds.isEmpty()) {
            throw new IllegalArgumentException("workflowInstanceIds must not be empty");
        }
        workflowInstanceIds = List.copyOf(workflowInstanceIds);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (sourceEventId == null) {
            throw new IllegalArgumentException("sourceEventId must not be null");
        }
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt must not be null");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }
}
